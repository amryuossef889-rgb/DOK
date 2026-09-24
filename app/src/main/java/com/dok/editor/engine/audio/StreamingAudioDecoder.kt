package com.dok.editor.engine.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StreamingAudioDecoder(
    private val context: Context,
    private val uri: Uri
) : Closeable {

    companion object {
        private const val TAG = "StreamingAudioDecoder"
        private const val TIMEOUT_US = 10_000L
        private const val MAX_BOUNDED_BUFFER_FRAMES = 16384 // Bounded memory buffer (~370ms)
    }

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var trackIndex = -1
    private var sourceSampleRate = 44100
    private var sourceChannels = 2
    private var isEos = false

    private val pendingPcmBuffer = ArrayList<Float>() // Interleaved stereo buffer
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
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                ext.selectTrack(i)
                sourceSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else 44100
                sourceChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else 2

                val decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(format, null, null, 0)
                decoder.start()
                codec = decoder
                break
            }
        }
    }

    /**
     * Reads exactly `frameCount` 44.1kHz stereo frames starting at `startFrame44k`.
     * Memory usage is strictly bounded.
     */
    @Synchronized
    fun readFrames(startFrame44k: Long, frameCount: Int): FloatArray {
        val targetStartTimeUs = (startFrame44k * 1_000_000L) / PcmMixer.SAMPLE_RATE_44K
        val ext = extractor ?: return FloatArray(frameCount * 2)
        val dec = codec ?: return FloatArray(frameCount * 2)

        // If target time is behind current position or significantly ahead (> 1s), seek with preroll
        val prerollThresholdUs = 500_000L
        if (targetStartTimeUs < currentPositionUs || targetStartTimeUs > currentPositionUs + prerollThresholdUs) {
            ext.seekTo(targetStartTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            dec.flush()
            pendingPcmBuffer.clear()
            currentPositionUs = ext.sampleTime
            isEos = false
        }

        val requiredSamples = frameCount * 2
        val output = FloatArray(requiredSamples)
        var outputFilledSamples = 0

        val bufferInfo = MediaCodec.BufferInfo()

        while (outputFilledSamples < requiredSamples && !isEos) {
            // Drain pending buffer first
            if (pendingPcmBuffer.isNotEmpty()) {
                val toCopy = minOf(pendingPcmBuffer.size, requiredSamples - outputFilledSamples)
                for (k in 0 until toCopy) {
                    output[outputFilledSamples++] = pendingPcmBuffer[k]
                }
                if (toCopy == pendingPcmBuffer.size) {
                    pendingPcmBuffer.clear()
                } else {
                    val sub = ArrayList(pendingPcmBuffer.subList(toCopy, pendingPcmBuffer.size))
                    pendingPcmBuffer.clear()
                    pendingPcmBuffer.addAll(sub)
                }
                if (outputFilledSamples >= requiredSamples) break
            }

            // Feed input buffer
            val inputIndex = dec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = dec.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    val sampleSize = ext.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        dec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEos = true
                    } else {
                        val sampleTimeUs = ext.sampleTime
                        dec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                        ext.advance()
                    }
                }
            }

            // Drain output buffer
            val outputIndex = dec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                val outBuffer = dec.getOutputBuffer(outputIndex)
                if (outBuffer != null && bufferInfo.size > 0) {
                    currentPositionUs = bufferInfo.presentationTimeUs
                    outBuffer.position(bufferInfo.offset)
                    outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    outBuffer.order(ByteOrder.LITTLE_ENDIAN)

                    val shortCount = bufferInfo.size / 2
                    val rawPcm = ShortArray(shortCount)
                    outBuffer.asShortBuffer().get(rawPcm)

                    val decodedFloat = PcmMixer.pcm16ToFloat(rawPcm)
                    val stereoFloat = convertToStereo44k(decodedFloat, sourceChannels, sourceSampleRate)

                    for (s in stereoFloat) {
                        if (pendingPcmBuffer.size < MAX_BOUNDED_BUFFER_FRAMES * 2) {
                            pendingPcmBuffer.add(s)
                        }
                    }
                }
                dec.releaseOutputBuffer(outputIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEos = true
                }
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = dec.outputFormat
                if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sourceSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    sourceChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        }

        return output
    }

    private fun convertToStereo44k(input: FloatArray, channels: Int, sampleRate: Int): FloatArray {
        // Step 1: Ensure stereo
        val stereo = if (channels == 1) {
            val s = FloatArray(input.size * 2)
            for (i in input.indices) {
                s[i * 2] = input[i]
                s[i * 2 + 1] = input[i]
            }
            s
        } else {
            input
        }

        // Step 2: Resample to 44.1k if necessary
        if (sampleRate == PcmMixer.SAMPLE_RATE_44K) return stereo

        val inputFrames = stereo.size / 2
        val outputFrames = ((inputFrames.toLong() * PcmMixer.SAMPLE_RATE_44K) / sampleRate).toInt()
        return PcmMixer.resampleByAbsolutePosition(
            sourcePcm = stereo,
            sourceSampleRate = sampleRate,
            absoluteOutputStartFrame = 0L,
            outputFrameCount = outputFrames,
            speed = 1.0f,
            targetSampleRate = PcmMixer.SAMPLE_RATE_44K
        )
    }

    override fun close() {
        try {
            codec?.stop()
            codec?.release()
        } catch (_: Exception) {}
        try {
            extractor?.release()
        } catch (_: Exception) {}
        codec = null
        extractor = null
    }
}
