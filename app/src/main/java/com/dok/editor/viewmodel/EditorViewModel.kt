package com.dok.editor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dok.editor.command.EditorCommand
import com.dok.editor.engine.TimelineEditingEngine
import com.dok.editor.engine.export.ExportPreset
import com.dok.editor.history.UndoRedoManager
import com.dok.editor.model.ColorGradingParams
import com.dok.editor.model.Effect
import com.dok.editor.model.Project
import com.dok.editor.model.TimelineClip
import com.dok.editor.model.Track
import com.dok.editor.model.TrackType
import com.dok.editor.model.Transform2D
import com.dok.editor.model.TransitionConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class EditorPanel {
    TIMELINE,
    INSPECTOR,
    COLOR,
    EFFECTS,
    DELIVER
}

enum class ExportUiState {
    IDLE,
    EXPORTING,
    SUCCESS,
    ERROR
}

class EditorViewModel(
    initialProject: Project? = null
) : ViewModel() {

    private val undoRedoManager = UndoRedoManager(maxHistorySize = 50)

    private val _project = MutableStateFlow(initialProject ?: createDefaultProject())
    val project: StateFlow<Project> = _project.asStateFlow()

    private val _currentTimeUs = MutableStateFlow(0L)
    val currentTimeUs: StateFlow<Long> = _currentTimeUs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _selectedTrackId = MutableStateFlow<String?>(null)
    val selectedTrackId: StateFlow<String?> = _selectedTrackId.asStateFlow()

    private val _inPointUs = MutableStateFlow<Long?>(null)
    val inPointUs: StateFlow<Long?> = _inPointUs.asStateFlow()

    private val _outPointUs = MutableStateFlow<Long?>(null)
    val outPointUs: StateFlow<Long?> = _outPointUs.asStateFlow()

    private val _isSnappingEnabled = MutableStateFlow(true)
    val isSnappingEnabled: StateFlow<Boolean> = _isSnappingEnabled.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1.0f) // 1.0f = default, >1.0f zoom in, <1.0f zoom out
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _activePanel = MutableStateFlow(EditorPanel.TIMELINE)
    val activePanel: StateFlow<EditorPanel> = _activePanel.asStateFlow()

    private val _exportUiState = MutableStateFlow(ExportUiState.IDLE)
    val exportUiState: StateFlow<ExportUiState> = _exportUiState.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _selectedExportPreset = MutableStateFlow(ExportPreset.YOUTUBE_1080P_60)
    val selectedExportPreset: StateFlow<ExportPreset> = _selectedExportPreset.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var playbackJob: Job? = null

    init {
        updateUndoRedoStatus()
    }

    fun setActivePanel(panel: EditorPanel) {
        _activePanel.value = panel
    }

    fun setSelectedExportPreset(preset: ExportPreset) {
        _selectedExportPreset.value = preset
    }

    fun dispatch(command: EditorCommand) {
        when (command) {
            is EditorCommand.TogglePlayPause -> {
                if (_isPlaying.value) pausePlayback() else startPlayback(1.0f)
            }
            is EditorCommand.Play -> startPlayback(1.0f)
            is EditorCommand.Pause -> pausePlayback()
            is EditorCommand.Stop -> {
                pausePlayback()
                _currentTimeUs.value = 0L
            }
            is EditorCommand.ShuttleForward -> {
                val nextSpeed = when (_playbackSpeed.value) {
                    1.0f -> 2.0f
                    2.0f -> 4.0f
                    4.0f -> 8.0f
                    else -> 1.0f
                }
                startPlayback(nextSpeed)
            }
            is EditorCommand.ShuttleReverse -> {
                val nextSpeed = when (_playbackSpeed.value) {
                    -1.0f -> -2.0f
                    -2.0f -> -4.0f
                    -4.0f -> -8.0f
                    else -> -1.0f
                }
                startPlayback(nextSpeed)
            }
            is EditorCommand.ShuttleStop -> pausePlayback()
            is EditorCommand.StepFrames -> {
                pausePlayback()
                val frameUs = 1_000_000L / _project.value.fps
                val newPos = (_currentTimeUs.value + command.frames * frameUs).coerceIn(0L, _project.value.durationUs)
                _currentTimeUs.value = newPos
            }
            is EditorCommand.ScrubTo -> {
                val duration = _project.value.durationUs
                val targetUs = if (duration > 0) command.timeUs.coerceIn(0L, duration) else 0L
                _currentTimeUs.value = targetUs
            }
            is EditorCommand.JumpToStart -> {
                _currentTimeUs.value = 0L
            }
            is EditorCommand.JumpToEnd -> {
                _currentTimeUs.value = _project.value.durationUs
            }
            is EditorCommand.SetInPoint -> {
                _inPointUs.value = command.timeUs
            }
            is EditorCommand.SetOutPoint -> {
                _outPointUs.value = command.timeUs
            }
            is EditorCommand.ClearInOutPoints -> {
                _inPointUs.value = null
                _outPointUs.value = null
            }
            is EditorCommand.SelectClip -> {
                _selectedClipId.value = command.clipId
            }
            is EditorCommand.SelectTrack -> {
                _selectedTrackId.value = command.trackId
            }
            is EditorCommand.SplitClipAtPlayhead -> {
                splitAtPlayhead()
            }
            is EditorCommand.DeleteSelectedClip -> {
                deleteSelectedClip(ripple = false)
            }
            is EditorCommand.RippleDeleteSelectedClip -> {
                deleteSelectedClip(ripple = true)
            }
            is EditorCommand.TrimClipStart -> {
                val clipId = command.clipId
                val newStart = command.newStartTimeUs
                val current = _project.value
                val clip = current.tracks.flatMap { it.clips }.find { it.id == clipId } ?: return
                val newDurationUs = maxOf(TimelineEditingEngine.MIN_CLIP_DURATION_US, clip.endTimeUs - newStart)
                val updated = TimelineEditingEngine.trimClip(
                    current, clipId,
                    newStartTimeUs = newStart,
                    newDurationUs = newDurationUs,
                    newTrimInUs = clip.trimInUs,
                    newTrimOutUs = clip.trimOutUs
                )
                commitProjectChange(updated)
            }
            is EditorCommand.TrimClipEnd -> {
                val clipId = command.clipId
                val newEnd = command.newEndTimeUs
                val current = _project.value
                val clip = current.tracks.flatMap { it.clips }.find { it.id == clipId } ?: return
                val newDurationUs = maxOf(TimelineEditingEngine.MIN_CLIP_DURATION_US, newEnd - clip.startTimeUs)
                val updated = TimelineEditingEngine.trimClip(
                    current, clipId,
                    newStartTimeUs = clip.startTimeUs,
                    newDurationUs = newDurationUs,
                    newTrimInUs = clip.trimInUs,
                    newTrimOutUs = clip.trimOutUs
                )
                commitProjectChange(updated)
            }
            is EditorCommand.MoveClip -> {
                val current = _project.value
                val clip = current.tracks.flatMap { it.clips }.find { it.id == command.clipId }
                val targetTrack = command.targetTrackId ?: clip?.trackId ?: current.tracks.first().id
                val updated = TimelineEditingEngine.moveClip(
                    current, command.clipId, targetTrack, command.newStartTimeUs
                )
                commitProjectChange(updated)
            }
            is EditorCommand.ChangeClipSpeed -> {
                val updated = TimelineEditingEngine.changeSpeed(
                    _project.value, command.clipId, command.speed
                )
                commitProjectChange(updated)
            }
            is EditorCommand.UpdateClipTransform -> {
                updateClip(command.clipId) { it.copy(transform = command.transform) }
            }
            is EditorCommand.UpdateColorGrading -> {
                updateClip(command.clipId) { it.copy(colorParams = command.colorParams) }
            }
            is EditorCommand.AddParametricEffect -> {
                updateClip(command.clipId) { clip ->
                    clip.copy(effects = clip.effects + command.effect)
                }
            }
            is EditorCommand.RemoveEffect -> {
                updateClip(command.clipId) { clip ->
                    clip.copy(effects = clip.effects.filter { it.id != command.effectId })
                }
            }
            is EditorCommand.SetClipTransitionIn -> {
                updateClip(command.clipId) { it.copy(transitionIn = command.transition) }
            }
            is EditorCommand.SetClipTransitionOut -> {
                updateClip(command.clipId) { it.copy(transitionOut = command.transition) }
            }
            is EditorCommand.Undo -> {
                val prev = undoRedoManager.undo(_project.value)
                if (prev != null) {
                    _project.value = prev
                    updateUndoRedoStatus()
                }
            }
            is EditorCommand.Redo -> {
                val next = undoRedoManager.redo(_project.value)
                if (next != null) {
                    _project.value = next
                    updateUndoRedoStatus()
                }
            }
            is EditorCommand.ToggleSnapping -> {
                _isSnappingEnabled.value = !_isSnappingEnabled.value
            }
            is EditorCommand.ZoomTimeline -> {
                _zoomLevel.value = (_zoomLevel.value + command.delta).coerceIn(0.2f, 5.0f)
            }
            is EditorCommand.ZoomToFit -> {
                _zoomLevel.value = 1.0f
            }
            is EditorCommand.ImportMediaClip -> {
                importMediaToTimeline(command.uri, command.name, command.durationUs)
            }
            is EditorCommand.RequestExport -> {
                _selectedExportPreset.value = command.preset
                _activePanel.value = EditorPanel.DELIVER
            }
        }
    }

    private fun startPlayback(speed: Float) {
        playbackJob?.cancel()
        _isPlaying.value = true
        _playbackSpeed.value = speed

        playbackJob = viewModelScope.launch {
            val frameIntervalMs = 33L // ~30fps preview loop
            while (isActive && _isPlaying.value) {
                delay(frameIntervalMs)
                val duration = _project.value.durationUs
                val deltaUs = (frameIntervalMs * 1_000L * _playbackSpeed.value).toLong()
                val nextUs = _currentTimeUs.value + deltaUs

                if (nextUs >= duration) {
                    _currentTimeUs.value = duration
                    _isPlaying.value = false
                    break
                } else if (nextUs <= 0) {
                    _currentTimeUs.value = 0L
                    _isPlaying.value = false
                    break
                } else {
                    _currentTimeUs.value = nextUs
                }
            }
        }
    }

    private fun pausePlayback() {
        playbackJob?.cancel()
        _isPlaying.value = false
        _playbackSpeed.value = 1.0f
    }

    private fun splitAtPlayhead() {
        val playheadUs = _currentTimeUs.value
        val selId = _selectedClipId.value
        val targetClip = if (selId != null) {
            _project.value.tracks.flatMap { it.clips }.find { it.id == selId }
        } else {
            _project.value.tracks.flatMap { it.clips }.find {
                playheadUs > it.startTimeUs && playheadUs < it.endTimeUs
            }
        } ?: return

        if (playheadUs > targetClip.startTimeUs && playheadUs < targetClip.endTimeUs) {
            val updated = TimelineEditingEngine.splitClip(_project.value, targetClip.id, playheadUs)
            commitProjectChange(updated)
        }
    }

    private fun deleteSelectedClip(ripple: Boolean) {
        val selId = _selectedClipId.value ?: return
        val updated = if (ripple) {
            TimelineEditingEngine.rippleDelete(_project.value, setOf(selId))
        } else {
            TimelineEditingEngine.liftDelete(_project.value, setOf(selId))
        }
        _selectedClipId.value = null
        commitProjectChange(updated)
    }

    private fun updateClip(clipId: String, transform: (TimelineClip) -> TimelineClip) {
        val current = _project.value
        val updatedTracks = current.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) transform(clip) else clip
            }
            track.copy(clips = updatedClips)
        }
        val updated = current.copy(tracks = updatedTracks, modifiedAtMs = System.currentTimeMillis())
        commitProjectChange(updated)
    }

    private fun importMediaToTimeline(uri: String, name: String, durationUs: Long) {
        val current = _project.value
        val targetTrack = current.tracks.firstOrNull { it.type == TrackType.VIDEO }
            ?: return

        val playheadUs = _currentTimeUs.value
        val newClip = TimelineClip(
            trackId = targetTrack.id,
            mediaUri = uri,
            mediaName = name,
            startTimeUs = playheadUs,
            durationUs = durationUs,
            sourceDurationUs = durationUs
        )

        val updatedClips = targetTrack.clips + newClip
        val updatedTracks = current.tracks.map {
            if (it.id == targetTrack.id) it.copy(clips = updatedClips) else it
        }

        val updated = current.copy(tracks = updatedTracks, modifiedAtMs = System.currentTimeMillis())
        commitProjectChange(updated)
        _selectedClipId.value = newClip.id
    }

    private fun commitProjectChange(newProject: Project) {
        undoRedoManager.pushState(_project.value)
        _project.value = newProject
        updateUndoRedoStatus()
    }

    private fun updateUndoRedoStatus() {
        _canUndo.value = undoRedoManager.canUndo
        _canRedo.value = undoRedoManager.canRedo
    }

    fun getSelectedClip(): TimelineClip? {
        val id = _selectedClipId.value ?: return null
        return _project.value.tracks.flatMap { it.clips }.find { it.id == id }
    }

    companion object {
        fun formatTimecode(timeUs: Long, fps: Int = 30): String {
            val totalSec = timeUs / 1_000_000L
            val remainderUs = timeUs % 1_000_000L
            val hours = totalSec / 3600
            val minutes = (totalSec % 3600) / 60
            val seconds = totalSec % 60
            val frames = ((remainderUs * fps) / 1_000_000L).toInt()
            return "%02d:%02d:%02d:%02d".format(hours, minutes, seconds, frames)
        }

        fun createDefaultProject(): Project {
            val videoTrack1 = Track(
                name = "V1",
                type = TrackType.VIDEO,
                clips = listOf(
                    TimelineClip(
                        trackId = "track_v1",
                        mediaUri = "content://dok/sample_gameplay.mp4",
                        mediaName = "Gameplay_Clutch.mp4",
                        startTimeUs = 0L,
                        durationUs = 5_000_000L,
                        sourceDurationUs = 10_000_000L,
                        transform = Transform2D(scaleX = 1f, scaleY = 1f)
                    ),
                    TimelineClip(
                        trackId = "track_v1",
                        mediaUri = "content://dok/sample_victory.mp4",
                        mediaName = "Victory_Screen.mp4",
                        startTimeUs = 5_000_000L,
                        durationUs = 4_000_000L,
                        sourceDurationUs = 8_000_000L
                    )
                )
            )

            val audioTrack1 = Track(
                name = "A1 Game",
                type = TrackType.AUDIO,
                clips = listOf(
                    TimelineClip(
                        trackId = "track_a1",
                        mediaUri = "content://dok/sample_audio.wav",
                        mediaName = "Game_Audio.wav",
                        startTimeUs = 0L,
                        durationUs = 9_000_000L,
                        sourceDurationUs = 15_000_000L,
                        volumeDb = 0.0f
                    )
                )
            )

            return Project(
                name = "Gaming Montage 01",
                width = 1920,
                height = 1080,
                fps = 30,
                tracks = listOf(videoTrack1, audioTrack1)
            )
        }
    }
}
