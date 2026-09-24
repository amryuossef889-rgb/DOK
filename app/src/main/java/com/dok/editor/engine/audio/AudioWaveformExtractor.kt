package com.dok.editor.engine.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object AudioWaveformExtractor {
    /**
     * Decode the source audio and build a time-aligned peak/RMS envelope.
     *
     * The old implementation assumed every decoder output was signed 16-bit PCM and
     * derived sample counts from the compressed input format. Decoder output is what
     * matters here: MediaCodec exposes the actual PCM encoding/sample-rate/channel
     * layout on INFO_OUTPUT_FORMAT_CHANGED. Android's audio decoders normally output
     * PCM_16, but PCM_FLOAT is also supported.
     */
    suspend fun extract(context: Context, uri: Uri, bars: Int = 240): List<Float> = withContext(Dispatchers.IO) {
        if (bars <= 0) return@withContext emptyList()

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioFormat = format
                    extractor.selectTrack(i)
                    break
                }
            }
            val inputFormat = audioFormat ?: return@withContext emptyList()
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val peaks = FloatArray(bars)
            val rmsSum = DoubleArray(bars)
            val rmsCount = LongArray(bars)

            val durationUs = inputFormat.getLong(MediaFormat.KEY_DURATION, 0L).coerceAtLeast(1L)
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, 48_000).coerceAtLeast(1)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
            var encoding = AudioFormat.ENCODING_PCM_16BIT

            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        if (input != null) {
                            input.clear()
                            val size = extractor.readSampleData(input, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(inputIndex, 0, size, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate).coerceAtLeast(1)
                        channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                        encoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit((info.offset + info.size).coerceAtMost(buffer.capacity()))
                            buffer.order(ByteOrder.nativeOrder())

                            val frameCount = when (encoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> info.size / 4 / channels
                                AudioFormat.ENCODING_PCM_32BIT -> info.size / 4 / channels
                                AudioFormat.ENCODING_PCM_24BIT_PACKED -> info.size / 3 / channels
                                AudioFormat.ENCODING_PCM_8BIT -> info.size / channels
                                else -> info.size / 2 / channels
                            }

                            if (frameCount > 0) {
                                val startUs = info.presentationTimeUs.coerceAtLeast(0L)
                                val bytesPerSample = when (encoding) {
                                    AudioFormat.ENCODING_PCM_FLOAT,
                                    AudioFormat.ENCODING_PCM_32BIT -> 4
                                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                                    AudioFormat.ENCODING_PCM_8BIT -> 1
                                    else -> 2
                                }
                                val bytesPerFrame = bytesPerSample * channels
                                val usableFrames = minOf(frameCount, info.size / bytesPerFrame)

                                repeat(usableFrames) { frame ->
                                    var framePeak = 0f
                                    var frameSquare = 0.0
                                    repeat(channels) {
                                        val sample = readPcmSample(buffer, encoding)
                                        val a = abs(sample).coerceAtMost(1f)
                                        framePeak = max(framePeak, a)
                                        frameSquare += (a * a).toDouble()
                                    }
                                    val timeUs = startUs + frame.toLong() * 1_000_000L / sampleRate
                                    val bar = ((timeUs.toDouble() / durationUs.toDouble()) * bars)
                                        .toInt().coerceIn(0, bars - 1)
                                    peaks[bar] = max(peaks[bar], framePeak)
                                    rmsSum[bar] += frameSquare / channels
                                    rmsCount[bar]++
                                }
                            }
                        }

                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            // Blend peak and RMS so quiet speech remains visible without turning every
            // bar into a single full-height spike.
            val result = FloatArray(bars)
            var maxLevel = 0f
            for (i in 0 until bars) {
                val rms = if (rmsCount[i] > 0) sqrt(rmsSum[i] / rmsCount[i]).toFloat() else 0f
                result[i] = max(peaks[i] * 0.72f, rms * 1.35f).coerceIn(0f, 1f)
                maxLevel = max(maxLevel, result[i])
            }
            if (maxLevel <= 0.0001f) emptyList()
            else result.map { (it / maxLevel).coerceIn(0.015f, 1f) }
        } catch (_: Throwable) {
            emptyList()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun readPcmSample(buffer: ByteBuffer, encoding: Int): Float {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> buffer.float
            AudioFormat.ENCODING_PCM_32BIT -> buffer.int.toFloat() / 2_147_483_648f
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val b0 = buffer.get().toInt() and 0xff
                val b1 = buffer.get().toInt() and 0xff
                val b2 = buffer.get().toInt()
                val value = b0 or (b1 shl 8) or (b2 shl 16)
                val signed = if ((value and 0x800000) != 0) value or -0x1000000 else value
                signed.toFloat() / 8_388_608f
            }
            AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xff) - 128) / 128f
            else -> buffer.short.toFloat() / 32768f
        }
    }
}
