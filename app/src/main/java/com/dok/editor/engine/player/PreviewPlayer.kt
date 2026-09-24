package com.dok.editor.engine.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.view.Surface
import com.dok.editor.engine.audio.PcmMixer
import com.dok.editor.engine.audio.StreamingAudioDecoder
import com.dok.editor.engine.plan.TimelineRenderPlan
import com.dok.editor.engine.video.SequentialVideoDecoder
import com.dok.editor.model.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import kotlin.math.abs

enum class PlayerState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    SCRUBBING,
    ERROR
}

enum class PreviewScaleMode(val scaleFactor: Float) {
    FULL(1.0f),
    HALF(0.5f),
    QUARTER(0.25f)
}

class PreviewPlayer(
    private val context: Context
) : Closeable {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _playerState = MutableStateFlow(PlayerState.IDLE)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _playbackPositionUs = MutableStateFlow(0L)
    val playbackPositionUs: StateFlow<Long> = _playbackPositionUs.asStateFlow()

    private val _totalDurationUs = MutableStateFlow(0L)
    val totalDurationUs: StateFlow<Long> = _totalDurationUs.asStateFlow()

    private var currentProject: Project? = null
    private var surface: Surface? = null
    private var scaleMode = PreviewScaleMode.FULL

    // Decoders cache by URI
    private val audioDecoders = HashMap<String, StreamingAudioDecoder>()
    private val videoDecoders = HashMap<String, SequentialVideoDecoder>()

    // Audio streaming
    private var audioTrack: AudioTrack? = null
    private var audioJob: Job? = null
    private var videoJob: Job? = null

    // A-V Clock Sync
    private var audioStartHeadPosition = 0
    private var clockAnchorTimeNs = 0L
    private var clockAnchorPositionUs = 0L

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            PcmMixer.SAMPLE_RATE_44K,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize * 4, 16384)

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(PcmMixer.SAMPLE_RATE_44K)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    fun attachSurface(surface: Surface) {
        this.surface = surface
        // Re-evaluate current frame on attached surface
        currentProject?.let { renderVideoFrameAt(_playbackPositionUs.value) }
    }

    fun detachSurface() {
        this.surface = null
    }

    fun setScaleMode(mode: PreviewScaleMode) {
        this.scaleMode = mode
    }

    fun setProject(project: Project) {
        this.currentProject = project
        _totalDurationUs.value = project.totalDurationUs
        if (_playerState.value == PlayerState.IDLE) {
            _playerState.value = PlayerState.PAUSED
        }
        renderVideoFrameAt(_playbackPositionUs.value)
    }

    fun play() {
        val project = currentProject ?: return
        if (_playerState.value == PlayerState.PLAYING) return

        if (_playbackPositionUs.value >= project.totalDurationUs) {
            seekTo(0L)
        }

        _playerState.value = PlayerState.PLAYING
        clockAnchorTimeNs = System.nanoTime()
        clockAnchorPositionUs = _playbackPositionUs.value

        audioTrack?.play()
        startAudioLoop()
        startVideoLoop()
    }

    fun pause() {
        if (_playerState.value != PlayerState.PLAYING) return
        _playerState.value = PlayerState.PAUSED
        stopAudioAndVideoLoops()
        audioTrack?.pause()
    }

    fun startScrubbing() {
        pause()
        _playerState.value = PlayerState.SCRUBBING
    }

    fun scrubTo(positionUs: Long) {
        val project = currentProject ?: return
        val clamped = positionUs.coerceIn(0L, project.totalDurationUs)
        _playbackPositionUs.value = clamped
        renderVideoFrameAt(clamped)
    }

    fun stopScrubbing() {
        if (_playerState.value == PlayerState.SCRUBBING) {
            _playerState.value = PlayerState.PAUSED
        }
    }

    fun seekTo(positionUs: Long) {
        val project = currentProject ?: return
        val clamped = positionUs.coerceIn(0L, project.totalDurationUs)
        val wasPlaying = _playerState.value == PlayerState.PLAYING
        if (wasPlaying) {
            pause()
        }

        audioTrack?.pause()
        audioTrack?.flush()

        _playbackPositionUs.value = clamped
        clockAnchorPositionUs = clamped
        clockAnchorTimeNs = System.nanoTime()

        renderVideoFrameAt(clamped)

        if (wasPlaying) {
            play()
        }
    }

    private fun startAudioLoop() {
        audioJob?.cancel()
        audioJob = scope.launch(Dispatchers.Default) {
            val chunkFrames = 2048 // ~46ms chunk at 44.1kHz
            val mixedBuffer = FloatArray(chunkFrames * 2)

            while (isActive && _playerState.value == PlayerState.PLAYING) {
                val project = currentProject ?: break
                val currentPos = _playbackPositionUs.value
                if (currentPos >= project.totalDurationUs) {
                    launch(Dispatchers.Main) { pause() }
                    break
                }

                val currentFrame44k = (currentPos * PcmMixer.SAMPLE_RATE_44K) / 1_000_000L
                val instructions = TimelineRenderPlan.evaluateAudioRange(project, currentFrame44k, chunkFrames)

                mixedBuffer.fill(0f)

                for (inst in instructions) {
                    val decoder = getOrCreateAudioDecoder(inst.mediaUri) ?: continue
                    val decoded = decoder.readFrames(inst.sourceStartFrame44k, inst.frameCount)

                    val gainL = inst.combinedLinearGain * inst.panGains.first * inst.fadeMultiplier
                    val gainR = inst.combinedLinearGain * inst.panGains.second * inst.fadeMultiplier

                    for (i in 0 until minOf(inst.frameCount, chunkFrames)) {
                        mixedBuffer[i * 2] += decoded[i * 2] * gainL
                        mixedBuffer[i * 2 + 1] += decoded[i * 2 + 1] * gainR
                    }
                }

                // Studio soft-knee peak limiter
                PcmMixer.applySoftKneeLimiter(mixedBuffer)

                val pcm16 = PcmMixer.floatToPcm16(mixedBuffer)
                audioTrack?.write(pcm16, 0, pcm16.size)

                // Advance clock anchor
                val chunkUs = (chunkFrames.toLong() * 1_000_000L) / PcmMixer.SAMPLE_RATE_44K
                _playbackPositionUs.value = minOf(currentPos + chunkUs, project.totalDurationUs)
            }
        }
    }

    private fun startVideoLoop() {
        videoJob?.cancel()
        videoJob = scope.launch(Dispatchers.Default) {
            val frameIntervalMs = 1000L / (currentProject?.fps ?: 30).coerceAtLeast(1)

            while (isActive && _playerState.value == PlayerState.PLAYING) {
                val currentPos = _playbackPositionUs.value
                renderVideoFrameAt(currentPos)
                kotlinx.coroutines.delay(frameIntervalMs)
            }
        }
    }

    private fun renderVideoFrameAt(timeUs: Long) {
        val project = currentProject ?: return
        val targetSurface = surface ?: return

        val plan = TimelineRenderPlan.evaluateVideoAt(project, timeUs)
        for (frameInst in plan.frameInstructions) {
            val decoder = getOrCreateVideoDecoder(frameInst.mediaUri, targetSurface)
            decoder?.decodeFrameToPts(frameInst.mediaSourceTimeUs)
        }
    }

    private fun getOrCreateAudioDecoder(uriString: String): StreamingAudioDecoder? {
        return try {
            audioDecoders.getOrPut(uriString) {
                StreamingAudioDecoder(context, Uri.parse(uriString))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateVideoDecoder(uriString: String, surface: Surface): SequentialVideoDecoder? {
        return try {
            videoDecoders.getOrPut(uriString) {
                SequentialVideoDecoder(context, Uri.parse(uriString), surface)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun stopAudioAndVideoLoops() {
        audioJob?.cancel()
        audioJob = null
        videoJob?.cancel()
        videoJob = null
    }

    override fun close() {
        stopAudioAndVideoLoops()
        scope.cancel()

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        audioDecoders.values.forEach { it.close() }
        audioDecoders.clear()

        videoDecoders.values.forEach { it.close() }
        videoDecoders.clear()

        _playerState.value = PlayerState.IDLE
    }
}
