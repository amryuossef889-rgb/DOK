package com.dok.editor.engine.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.dok.editor.engine.audio.PcmMixer
import com.dok.editor.engine.gpu.EglVideoCompositor
import com.dok.editor.engine.audio.StreamingAudioDecoder
import com.dok.editor.engine.plan.TimelineRenderPlan
import com.dok.editor.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

class ExportPipeline(
    private val context: Context,
    private val project: Project,
    private val preset: ExportPreset
) {
    companion object {
        private const val TAG = "ExportPipeline"
        private const val TIMEOUT_US = 10_000L
        private const val VIDEO_MIME = "video/avc"
        private const val AUDIO_MIME = "audio/mp4a-latm"
    }

    private data class QueuedSample(
        val isVideo: Boolean,
        val buffer: ByteArray,
        val info: MediaCodec.BufferInfo
    )

    suspend fun execute(
        onProgress: (Float) -> Unit,
        onComplete: (Uri) -> Unit,
        onError: (Throwable) -> Unit
    ) = withContext(Dispatchers.Default) {
        val tempOutputFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        var muxer: MediaMuxer? = null
        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null

        val audioDecoders = HashMap<String, StreamingAudioDecoder>()
        var compositor: EglVideoCompositor? = null

        try {
            if (project.totalDurationUs <= 0L) {
                throw IllegalStateException("Cannot export an empty timeline")
            }
            val totalDurationUs = project.totalDurationUs
            require(preset.width > 0 && preset.height > 0) { "Export dimensions must be positive" }
            require(preset.fps > 0) { "Export FPS must be positive" }
            require(preset.audioSampleRate > 0) { "Export audio sample rate must be positive" }
            val totalFrames = kotlin.math.ceil((totalDurationUs.toDouble() / 1_000_000.0) * preset.fps).toLong().coerceAtLeast(1L)

            // Setup Video Encoder
            val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME, preset.width, preset.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, preset.videoBitrateBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, preset.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, preset.iFrameIntervalSec)
            }
            videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME)
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = videoEncoder.createInputSurface()
            videoEncoder.start()
            compositor = EglVideoCompositor(context, inputSurface, preset.width, preset.height)

            // Setup Audio Encoder
            val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME, preset.audioSampleRate, 2).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, preset.audioBitrateBps)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME)
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            // Setup Muxer
            muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false

            val pendingMuxerSamples = ArrayList<QueuedSample>()

            fun checkAndStartMuxer() {
                if (!muxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                    muxer.start()
                    muxerStarted = true
                    // Flush pending queued samples
                    for (sample in pendingMuxerSamples) {
                        val track = if (sample.isVideo) videoTrackIndex else audioTrackIndex
                        val byteBuf = ByteBuffer.wrap(sample.buffer)
                        muxer.writeSampleData(track, byteBuf, sample.info)
                    }
                    pendingMuxerSamples.clear()
                }
            }

            fun writeOrQueueSample(isVideo: Boolean, byteBuf: ByteBuffer, info: MediaCodec.BufferInfo) {
                if (muxerStarted) {
                    val track = if (isVideo) videoTrackIndex else audioTrackIndex
                    val start = info.offset.coerceIn(0, byteBuf.capacity())
                    val end = (start + info.size).coerceAtMost(byteBuf.capacity())
                    if (end > start) {
                        byteBuf.position(start)
                        byteBuf.limit(end)
                        muxer.writeSampleData(track, byteBuf, info)
                    }
                } else {
                    // Critical rule: Buffer pending samples until all track formats are known
                    val bytes = ByteArray(info.size)
                    val originalPos = byteBuf.position()
                    byteBuf.position(info.offset)
                    byteBuf.get(bytes)
                    byteBuf.position(originalPos)

                    val copiedInfo = MediaCodec.BufferInfo().apply {
                        set(0, info.size, info.presentationTimeUs, info.flags)
                    }
                    pendingMuxerSamples.add(QueuedSample(isVideo, bytes, copiedInfo))
                }
            }

            // Encode Video Frames sequentially
            var videoOutputFrames = 0L
            var videoEosReceived = false
            val bufferInfo = MediaCodec.BufferInfo()

            for (frameIdx in 0 until totalFrames) {
                if (!coroutineContext.isActive) break
                val frameTimeUs = (frameIdx * 1_000_000L) / preset.fps

                // Shared render plan drives a real GPU composition pass.
                val plan = TimelineRenderPlan.evaluateVideoAt(project, frameTimeUs)
                val gpu = compositor ?: error("GPU compositor unavailable")
                gpu.beginFrame()
                for (frameInst in plan.frameInstructions) {
                    gpu.renderLayer(frameInst.clipId, Uri.parse(frameInst.mediaUri), frameInst)
                }
                gpu.endFrame(frameTimeUs)

                // Drain video encoder
                var outIdx = videoEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outIdx >= 0) {
                    val outBuf = videoEncoder.getOutputBuffer(outIdx)
                    if (outBuf != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit((bufferInfo.offset + bufferInfo.size).coerceAtMost(outBuf.capacity()))
                        // Critical rule: derive PTS from output frame counter, not input index!
                        val ptsUs = (videoOutputFrames * 1_000_000L) / preset.fps
                        videoOutputFrames++
                        bufferInfo.presentationTimeUs = ptsUs
                        writeOrQueueSample(true, outBuf, bufferInfo)
                    }
                    videoEncoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        videoEosReceived = true
                        break
                    }
                    outIdx = videoEncoder.dequeueOutputBuffer(bufferInfo, 0)
                }
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    videoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)
                    checkAndStartMuxer()
                }

                val progress = (frameIdx.toFloat() / totalFrames.toFloat()) * 0.5f
                withContext(Dispatchers.Main) { onProgress(progress) }
            }

            // Signal Video EOS
            try {
                videoEncoder.signalEndOfInputStream()
            } catch (_: Exception) {}

            // Drain Video to EOS with wall-clock timeout
            val drainStartMs = System.currentTimeMillis()
            while (!videoEosReceived && System.currentTimeMillis() - drainStartMs < 5000L) {
                val outIdx = videoEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIdx >= 0) {
                    val outBuf = videoEncoder.getOutputBuffer(outIdx)
                    if (outBuf != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                        val ptsUs = (videoOutputFrames * 1_000_000L) / preset.fps
                        videoOutputFrames++
                        bufferInfo.presentationTimeUs = ptsUs
                        writeOrQueueSample(true, outBuf, bufferInfo)
                    }
                    videoEncoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        videoEosReceived = true
                        break
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    videoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)
                    checkAndStartMuxer()
                }
            }

            // Encode Audio
            val totalAudioFrames = ((totalDurationUs.toDouble() / 1_000_000.0) * preset.audioSampleRate).toLong().coerceAtLeast(1L)
            val chunkFrames = 2048
            var audioFrameCursor = 0L
            var audioOutputFrameCounter = 0L
            var audioEosReceived = false
            val audioBufferInfo = MediaCodec.BufferInfo()

            while (audioFrameCursor < totalAudioFrames && coroutineContext.isActive) {
                val framesThisChunk = minOf(chunkFrames.toLong(), totalAudioFrames - audioFrameCursor).toInt()
                val instructions = TimelineRenderPlan.evaluateAudioRange(
                    project,
                    audioFrameCursor,
                    framesThisChunk,
                    preset.audioSampleRate
                )

                val mixed = FloatArray(framesThisChunk * 2)
                for (inst in instructions) {
                    val dec = audioDecoders.getOrPut(inst.mediaUri) {
                        StreamingAudioDecoder(context, Uri.parse(inst.mediaUri))
                    }
                    val sourceFramesNeeded = kotlin.math.ceil(
                        inst.frameCount.toDouble() *
                            inst.speed.toDouble() *
                            PcmMixer.SAMPLE_RATE_44K.toDouble() /
                            preset.audioSampleRate.toDouble()
                    ).toLong().coerceAtLeast(2L)
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt() + 2
                    val decoded = dec.readFrames(inst.sourceStartFrame44k, sourceFramesNeeded)
                    val speedAdjusted = PcmMixer.resampleByAbsolutePosition(
                        sourcePcm = decoded,
                        sourceSampleRate = PcmMixer.SAMPLE_RATE_44K,
                        absoluteOutputStartFrame = 0L,
                        outputFrameCount = inst.frameCount,
                        speed = inst.speed,
                        targetSampleRate = preset.audioSampleRate
                    )
                    val gL = inst.combinedLinearGain * inst.panGains.first
                    val gR = inst.combinedLinearGain * inst.panGains.second
                    for (k in 0 until minOf(inst.frameCount, framesThisChunk)) {
                        val timelineUs = (audioFrameCursor + k).toLong() * 1_000_000L /
                            preset.audioSampleRate
                        val gain = PcmMixer.calculateFadeEnvelope(
                            currentPositionUs = timelineUs,
                            clipStartTimeUs = inst.clipStartTimeUs,
                            clipDurationUs = inst.clipDurationUs,
                            fadeInUs = inst.fadeInUs,
                            fadeOutUs = inst.fadeOutUs
                        )
                        mixed[k * 2] += speedAdjusted[k * 2] * gL * gain
                        mixed[k * 2 + 1] += speedAdjusted[k * 2 + 1] * gR * gain
                    }
                }
                PcmMixer.applySoftKneeLimiter(mixed)
                val pcm16 = PcmMixer.floatToPcm16(mixed)

                // Feed audio encoder without dropping chunks when its input queue is temporarily full.
                var inIdx = audioEncoder.dequeueInputBuffer(TIMEOUT_US)
                var inputWaits = 0
                while (inIdx < 0 && inputWaits < 50 && coroutineContext.isActive) {
                    kotlinx.coroutines.delay(2L)
                    inIdx = audioEncoder.dequeueInputBuffer(TIMEOUT_US)
                    inputWaits++
                }
                if (inIdx >= 0) {
                    val inBuf = audioEncoder.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        inBuf.clear()
                        val shortBuf = inBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        shortBuf.put(pcm16)
                        val bytesSize = pcm16.size * 2
                        val ptsUs = (audioFrameCursor * 1_000_000L) / preset.audioSampleRate
                        val isLast = (audioFrameCursor + framesThisChunk) >= totalAudioFrames
                        val flags = if (isLast) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        audioEncoder.queueInputBuffer(inIdx, 0, bytesSize, ptsUs, flags)
                    }
                } else {
                    throw IllegalStateException("Audio encoder input stalled")
                }

                // Drain audio encoder
                var aOutIdx = audioEncoder.dequeueOutputBuffer(audioBufferInfo, TIMEOUT_US)
                while (aOutIdx >= 0) {
                    val aOutBuf = audioEncoder.getOutputBuffer(aOutIdx)
                    if (aOutBuf != null && (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && audioBufferInfo.size > 0) {
                        aOutBuf.position(audioBufferInfo.offset)
                        aOutBuf.limit((audioBufferInfo.offset + audioBufferInfo.size).coerceAtMost(aOutBuf.capacity()))
                        writeOrQueueSample(false, aOutBuf, audioBufferInfo)
                    }
                    audioEncoder.releaseOutputBuffer(aOutIdx, false)
                    if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        audioEosReceived = true
                        break
                    }
                    aOutIdx = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 0)
                }
                if (aOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    audioTrackIndex = muxer.addTrack(audioEncoder.outputFormat)
                    checkAndStartMuxer()
                }

                audioFrameCursor += framesThisChunk
                val progress = 0.5f + (audioFrameCursor.toFloat() / totalAudioFrames.toFloat()) * 0.48f
                withContext(Dispatchers.Main) { onProgress(progress) }
            }

            // Drain audio to real EOS with wall-clock timeout
            val audioDrainStart = System.currentTimeMillis()
            while (!audioEosReceived && System.currentTimeMillis() - audioDrainStart < 5000L) {
                val aOutIdx = audioEncoder.dequeueOutputBuffer(audioBufferInfo, TIMEOUT_US)
                if (aOutIdx >= 0) {
                    val aOutBuf = audioEncoder.getOutputBuffer(aOutIdx)
                    if (aOutBuf != null && (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && audioBufferInfo.size > 0) {
                        writeOrQueueSample(false, aOutBuf, audioBufferInfo)
                    }
                    audioEncoder.releaseOutputBuffer(aOutIdx, false)
                    if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        audioEosReceived = true
                        break
                    }
                } else if (aOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    audioTrackIndex = muxer.addTrack(audioEncoder.outputFormat)
                    checkAndStartMuxer()
                }
            }

            // Close decoders and encoders
            compositor?.close()
            compositor = null
            audioDecoders.values.forEach { it.close() }

            try { videoEncoder.stop(); videoEncoder.release() } catch (_: Exception) {}
            try { audioEncoder.stop(); audioEncoder.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try { muxer.stop(); muxer.release() } catch (_: Exception) {}
            }

            // Register with MediaStore Movies/
            val finalUri = saveToMediaStore(context, tempOutputFile, "${project.name}_${preset.id}.mp4")
            withContext(Dispatchers.Main) {
                onProgress(1.0f)
                onComplete(finalUri)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Export failed", t)
            withContext(Dispatchers.Main) { onError(t) }
        } finally {
            runCatching { compositor?.close() }
            compositor = null
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
        }
    }

    private fun saveToMediaStore(context: Context, sourceFile: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/DokEditor")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: Uri.fromFile(sourceFile)

        if (uri.scheme == "content") {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        }
        return uri
    }
}
