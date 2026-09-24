package com.dok.editor.engine.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

object AudioWaveformExtractor {
    suspend fun extract(context: Context, uri: Uri, bars: Int = 180): List<Float> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
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
            val format = audioFormat ?: return@withContext emptyList()
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val peaks = FloatArray(bars)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L) else 1L
            val timeoutUs = 10_000L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var peakCount = 0L
            var maxSeen = 0.0001f

            while (!outputDone) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(timeoutUs)
                    if (index >= 0) {
                        val input = codec.getInputBuffer(index)
                        if (input != null) {
                            input.clear()
                            val size = extractor.readSampleData(input, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val output = codec.dequeueOutputBuffer(info, timeoutUs)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED, MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (output >= 0) {
                        val buffer: ByteBuffer? = codec.getOutputBuffer(output)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val sampleCount = info.size / 2
                            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                            } else 1
                            val frameCount = sampleCount / channels
                            var frame = 0
                            val startUs = info.presentationTimeUs.coerceAtLeast(0L)
                            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 48_000
                            while (frame < frameCount && buffer.remaining() >= channels * 2) {
                                var framePeak = 0f
                                repeat(channels) {
                                    val lo = buffer.get().toInt() and 0xff
                                    val hi = buffer.get().toInt()
                                    val sample = ((hi shl 8) or lo).toShort()
                                    framePeak = max(framePeak, abs(sample.toInt()).toFloat() / 32768f)
                                }
                                val timeUs = startUs + (frame.toLong() * 1_000_000L / max(1, sampleRate))
                                val index = ((timeUs.toDouble() / durationUs.toDouble()) * bars).toInt().coerceIn(0, bars - 1)
                                peaks[index] = max(peaks[index], framePeak)
                                maxSeen = max(maxSeen, framePeak)
                                peakCount++
                                frame++
                            }
                        }
                        codec.releaseOutputBuffer(output, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            }
            codec.stop()
            codec.release()
            peaks.map { (it / maxSeen).coerceIn(0f, 1f) }.toList()
        } catch (_: Throwable) {
            emptyList()
        } finally {
            extractor.release()
        }
    }
}
