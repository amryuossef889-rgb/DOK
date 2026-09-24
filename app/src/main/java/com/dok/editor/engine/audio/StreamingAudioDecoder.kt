package com.dok.editor.engine.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class StreamingAudioDecoder(
    private val context: Context,
    private val uri: Uri
) : Closeable {

    companion object {
        private const val TIMEOUT_US = 10_000L
        private const val MAX_BOUNDED_BUFFER_FRAMES = 16_384
    }

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var sourceSampleRate = PcmMixer.SAMPLE_RATE_44K
    private var sourceChannels = 2
    private var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
    private var isEos = false

    private val pendingPcmBuffer = ArrayList<Float>()
    private var currentPositionUs = 0L

    init {
        initCodec()
    }

    private fun initCodec() {
        val ext = MediaExtractor()
        ext.setDataSource(context, uri, null)
        extractor = ext

        for (i in 0 until ext.trackCount) {
            val format = ext.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (!mime.startsWith("audio/")) continue

            trackIndex = i
            ext.selectTrack(i)
            sourceSampleRate = format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, PcmMixer.SAMPLE_RATE_44K)
            sourceChannels = format.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
            pcmEncoding = format.getIntegerOrDefault(
                MediaFormat.KEY_PCM_ENCODING,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            codec = decoder
            break
        }
    }

    private var trackIndex = -1

    @Synchronized
    fun readFrames(startFrame44k: Long, frameCount: Int): FloatArray {
        require(frameCount >= 0)
        if (frameCount == 0) return FloatArray(0)

        val targetStartTimeUs = (startFrame44k * 1_000_000L) / PcmMixer.SAMPLE_RATE_44K
        val ext = extractor ?: return FloatArray(frameCount * 2)
        val dec = codec ?: return FloatArray(frameCount * 2)

        if (targetStartTimeUs < currentPositionUs || targetStartTimeUs > currentPositionUs + 500_000L) {
            ext.seekTo(targetStartTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            dec.flush()
            pendingPcmBuffer.clear()
            currentPositionUs = ext.sampleTime.coerceAtLeast(0L)
            isEos = false
        }

        val requiredSamples = frameCount * 2
        val output = FloatArray(requiredSamples)
        var outputFilledSamples = 0
        val bufferInfo = MediaCodec.BufferInfo()

        while (outputFilledSamples < requiredSamples && !isEos) {
            if (pendingPcmBuffer.isNotEmpty()) {
                val toCopy = minOf(pendingPcmBuffer.size, requiredSamples - outputFilledSamples)
                for (k in 0 until toCopy) output[outputFilledSamples++] = pendingPcmBuffer[k]
                repeat(toCopy) { pendingPcmBuffer.removeAt(0) }
                if (outputFilledSamples >= requiredSamples) break
            }

            val inputIndex = dec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = dec.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    val sampleSize = ext.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        dec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        ext.advance()
                    } else {
                        val sampleTimeUs = ext.sampleTime.coerceAtLeast(0L)
                        dec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                        ext.advance()
                    }
                }
            }

            val outputIndex = dec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    val outBuffer = dec.getOutputBuffer(outputIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        currentPositionUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)
                        val duplicate = outBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                        duplicate.position(bufferInfo.offset.coerceIn(0, duplicate.limit()))
                        duplicate.limit((bufferInfo.offset + bufferInfo.size).coerceAtMost(duplicate.capacity()))
                        val decoded = decodePcmToFloat(duplicate, pcmEncoding)
                        val stereo44k = convertToStereo44k(decoded, sourceChannels, sourceSampleRate)
                        val room = MAX_BOUNDED_BUFFER_FRAMES * 2 - pendingPcmBuffer.size
                        if (room > 0) {
                            for (s in stereo44k.take(room)) pendingPcmBuffer.add(s)
                        }
                    }
                    dec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) isEos = true
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = dec.outputFormat
                    sourceSampleRate = newFormat.getIntegerOrDefault(
                        MediaFormat.KEY_SAMPLE_RATE,
                        sourceSampleRate
                    )
                    sourceChannels = newFormat.getIntegerOrDefault(
                        MediaFormat.KEY_CHANNEL_COUNT,
                        sourceChannels
                    )
                    pcmEncoding = newFormat.getIntegerOrDefault(
                        MediaFormat.KEY_PCM_ENCODING,
                        pcmEncoding
                    )
                }
            }
        }

        return output
    }

    private fun decodePcmToFloat(buffer: ByteBuffer, encoding: Int): FloatArray {
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> 2
        }
        val sampleCount = buffer.remaining() / bytesPerSample
        val out = FloatArray(sampleCount)

        for (i in 0 until sampleCount) {
            out[i] = when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> buffer.float.coerceIn(-1f, 1f)
                AudioFormat.ENCODING_PCM_32BIT -> (buffer.int.toDouble() / 2_147_483_648.0).toFloat().coerceIn(-1f, 1f)
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    val b0 = buffer.get().toInt() and 0xff
                    val b1 = buffer.get().toInt() and 0xff
                    val b2 = buffer.get().toInt()
                    val raw = b0 or (b1 shl 8) or (b2 shl 16)
                    val signed = if ((raw and 0x0080_0000) != 0) raw or -0x0100_0000 else raw
                    (signed / 8_388_608f).coerceIn(-1f, 1f)
                }
                AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xff) - 128) / 128f
                else -> (buffer.short.toInt() / 32_768f).coerceIn(-1f, 1f)
            }
        }
        return out
    }

    private fun convertToStereo44k(input: FloatArray, channels: Int, sampleRate: Int): FloatArray {
        if (input.isEmpty()) return FloatArray(0)
        val channelCount = channels.coerceAtLeast(1)
        val frames = input.size / channelCount
        if (frames == 0) return FloatArray(0)

        val stereo = FloatArray(frames * 2)
        for (frame in 0 until frames) {
            if (channelCount == 1) {
                val v = input[frame]
                stereo[frame * 2] = v
                stereo[frame * 2 + 1] = v
            } else {
                stereo[frame * 2] = input[frame * channelCount]
                stereo[frame * 2 + 1] = input[frame * channelCount + 1]
            }
        }

        if (sampleRate == PcmMixer.SAMPLE_RATE_44K) return stereo
        val outputFrames = (frames.toLong() * PcmMixer.SAMPLE_RATE_44K / sampleRate.coerceAtLeast(1))
            .toInt().coerceAtLeast(1)
        return PcmMixer.resampleByAbsolutePosition(
            sourcePcm = stereo,
            sourceSampleRate = sampleRate,
            absoluteOutputStartFrame = 0L,
            outputFrameCount = outputFrames,
            speed = 1f,
            targetSampleRate = PcmMixer.SAMPLE_RATE_44K
        )
    }

    override fun close() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor?.release() }
        codec = null
        extractor = null
    }
}

private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int =
    if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(default) else default
