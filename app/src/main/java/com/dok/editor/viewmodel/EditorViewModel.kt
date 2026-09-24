package com.dok.editor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dok.editor.command.EditorCommand
import com.dok.editor.engine.TimelineEditingEngine
import com.dok.editor.engine.audio.AudioWaveformExtractor
import com.dok.editor.engine.media.MediaMetadataExtractor
import com.dok.editor.engine.subtitle.SrtSubtitleCodec
import com.dok.editor.engine.export.ExportPipeline
import com.dok.editor.engine.export.ExportPreset
import com.dok.editor.history.UndoRedoManager
import com.dok.editor.model.*
import com.dok.editor.persistence.ProjectSerializer
import com.dok.editor.render.PersistentRenderQueue
import com.dok.editor.render.RenderJob
import com.dok.editor.render.RenderJobSpec
import java.io.File
import org.json.JSONObject
import com.dok.editor.media.MediaPool
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class EditorPanel { MEDIA_POOL, TIMELINE, INSPECTOR, COLOR, EFFECTS, DELIVER }
enum class ExportUiState { IDLE, EXPORTING, SUCCESS, ERROR }

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val history = UndoRedoManager(50)
    private val mediaPool = MediaPool(application)
    private val recoveryManager = ProjectRecoveryManager(application)
    private val renderQueue = PersistentRenderQueue(application, viewModelScope)
    val renderQueueState: StateFlow<List<RenderJob>> = renderQueue.state
    private val projectFile = File(application.filesDir, "projects/current_project.json")
    private val restoredProject: Project? = runCatching {
        if (projectFile.exists()) ProjectSerializer.loadProject(projectFile) else null
    }.getOrNull()
    private val _mediaAssets = MutableStateFlow(restoredProject?.mediaPool ?: mediaPool.all())
    val mediaAssets: StateFlow<List<MediaAsset>> = _mediaAssets.asStateFlow()
    private val _project = MutableStateFlow(restoredProject ?: createEmptyProject(mediaPool.all()))
    val project: StateFlow<Project> = _project.asStateFlow()
    private val _currentTimeUs = MutableStateFlow(0L)
    val currentTimeUs: StateFlow<Long> = _currentTimeUs.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _playbackSpeed = MutableStateFlow(1f)
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
    private val _zoomLevel = MutableStateFlow(1f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()
    private val _activePanel = MutableStateFlow(EditorPanel.TIMELINE)
    val activePanel: StateFlow<EditorPanel> = _activePanel.asStateFlow()
    private val _exportUiState = MutableStateFlow(ExportUiState.IDLE)
    val exportUiState: StateFlow<ExportUiState> = _exportUiState.asStateFlow()
    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()
    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError.asStateFlow()
    private val _selectedExportPreset = MutableStateFlow(ExportPreset.YOUTUBE_1080P_60)
    val selectedExportPreset: StateFlow<ExportPreset> = _selectedExportPreset.asStateFlow()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()
    private var playbackJob: Job? = null
    private var exportJob: Job? = null
    private var autosaveRevision = 0

    init {
        restoredProject?.mediaPool?.forEach { mediaPool.upsert(it) }
        _mediaAssets.value = mediaPool.all()
        if (restoredProject == null) {
            persistProjectAsync(_project.value, snapshot = false)
        }
        restoreRenderQueue()
    }

    private fun persistProjectAsync(project: Project, snapshot: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                ProjectSerializer.saveProjectAtomically(projectFile, project)
                val json = ProjectSerializer.serializeToJson(project)
                recoveryManager.autosave(project.id, json)
                if (snapshot) {
                    recoveryManager.snapshot(project.id, json)
                    recoveryManager.prune(project.id, keep = 20)
                }
            }
        }
    }

    private fun markChanged(project: Project) {
        autosaveRevision++
        persistProjectAsync(project, snapshot = autosaveRevision % 20 == 0)
    }

    fun enqueueCurrentExport(preset: ExportPreset) {
        val snapshot = _project.value
        val jobId = java.util.UUID.randomUUID().toString()
        val snapshotDir = File(getApplication<Application>().filesDir, "render-snapshots").apply { mkdirs() }
        val snapshotFile = File(snapshotDir, jobId + ".json")
        runCatching { snapshotFile.writeText(ProjectSerializer.serializeToJson(snapshot)) }.onFailure { return }
        val spec = RenderJobSpec(
            id = jobId,
            name = snapshot.name + " • " + preset.name,
            projectId = snapshot.id,
            outputPath = "",
            presetId = preset.id,
            projectSnapshotPath = snapshotFile.absolutePath
        )
        viewModelScope.launch {
            renderQueue.enqueue(spec) { progress ->
                ExportPipeline(getApplication(), snapshot, preset).execute(
                    onProgress = { value -> progress(value) },
                    onComplete = {},
                    onError = { throw it }
                )
            }
        }
    }

    fun startQueuedExports() {
        viewModelScope.launch { renderQueue.startQueuedSequentially() }
    }

    fun cancelQueuedExport(id: String) { renderQueue.cancel(id) }

    fun removeQueuedExport(id: String) {
        viewModelScope.launch { renderQueue.remove(id) }
    }

    private fun restoreRenderQueue() {
        viewModelScope.launch {
            renderQueue.restoreSpecs().forEach { spec ->
                val file = File(spec.projectSnapshotPath)
                if (!file.exists()) return@forEach
                val snap = runCatching { ProjectSerializer.deserializeFromJson(file.readText()) }.getOrNull() ?: return@forEach
                val preset = exportPresetById(spec.presetId) ?: return@forEach
                renderQueue.enqueue(spec) { progress ->
                    ExportPipeline(getApplication(), snap, preset).execute(
                        onProgress = { value -> progress(value) },
                        onComplete = {},
                        onError = { throw it }
                    )
                }
            }
        }
    }

    private fun exportPresetById(id: String): ExportPreset? = listOf(
        ExportPreset.YOUTUBE_1080P_60,
        ExportPreset.TIKTOK_SHORTS_1080P_60,
        ExportPreset.GAMING_4K_30,
        ExportPreset.FAST_720P_30
    ).firstOrNull { it.id == id }

    fun recoverySnapshots(): List<String> =
        recoveryManager.snapshots(_project.value.id).map { it.name }

    fun restoreRecoverySnapshot(fileName: String): Boolean {
        val file = recoveryManager.snapshots(_project.value.id).firstOrNull { it.name == fileName } ?: return false
        val restored = try { ProjectSerializer.loadProject(file) } catch (_: Throwable) { return false }
        history.clear()
        _project.value = restored
        _mediaAssets.value = restored.mediaPool
        _selectedClipId.value = null
        _currentTimeUs.value = 0L
        syncHistory()
        persistProjectAsync(restored, snapshot = false)
        return true
    }

    fun setActivePanel(panel: EditorPanel) { _activePanel.value = panel }
    fun setSelectedExportPreset(preset: ExportPreset) { _selectedExportPreset.value = preset }

    fun dispatch(command: EditorCommand) {
        when (command) {
            EditorCommand.TogglePlayPause -> if (_isPlaying.value) pausePlayback() else startPlayback(1f)
            EditorCommand.Play -> startPlayback(1f)
            EditorCommand.Pause, EditorCommand.ShuttleStop -> pausePlayback()
            EditorCommand.Stop -> { pausePlayback(); _currentTimeUs.value = 0L }
            EditorCommand.ShuttleForward -> startPlayback(nextShuttleSpeed(_playbackSpeed.value, true))
            EditorCommand.ShuttleReverse -> startPlayback(nextShuttleSpeed(_playbackSpeed.value, false))
            is EditorCommand.StepFrames -> { pausePlayback(); seek(_currentTimeUs.value + command.frames * 1_000_000L / _project.value.fps) }
            is EditorCommand.ScrubTo -> seek(command.timeUs)
            EditorCommand.JumpToStart -> seek(0L)
            EditorCommand.JumpToEnd -> seek(_project.value.durationUs)
            is EditorCommand.SetInPoint -> _inPointUs.value = command.timeUs.coerceAtLeast(0L)
            is EditorCommand.SetOutPoint -> _outPointUs.value = command.timeUs.coerceAtLeast(0L)
            EditorCommand.ClearInOutPoints -> { _inPointUs.value = null; _outPointUs.value = null }
            EditorCommand.SplitClipAtPlayhead -> splitAtPlayhead()
            EditorCommand.DeleteSelectedClip -> deleteSelected(false)
            EditorCommand.RippleDeleteSelectedClip -> deleteSelected(true)
            is EditorCommand.SelectClip -> _selectedClipId.value = command.clipId
            is EditorCommand.SelectTrack -> _selectedTrackId.value = command.trackId
            is EditorCommand.TrimClipStart -> trimStart(command)
            is EditorCommand.TrimClipEnd -> trimEnd(command)
            is EditorCommand.MoveClip -> moveClipLinked(command)
            is EditorCommand.MoveSelectedClip -> selectedClipId.value?.let { id ->
                project.value.tracks.flatMap { it.clips }
                    .firstOrNull { it.id == id }
                    ?.let { clip ->
                        moveClipLinked(
                            EditorCommand.MoveClip(
                                id,
                                (clip.startTimeUs + command.deltaUs).coerceAtLeast(0L)
                            )
                        )
                    }
            }
            is EditorCommand.ChangeClipSpeed -> {
                val clip = findClip(command.clipId)
                var updated = TimelineEditingEngine.changeSpeed(_project.value, command.clipId, command.speed)
                clip?.linkedClipId?.let { linkedId ->
                    updated = TimelineEditingEngine.changeSpeed(updated, linkedId, command.speed)
                }
                commit(updated)
            }
            is EditorCommand.UpdateClipAudio -> updateClip(command.clipId) { it.copy(volumeDb = command.volumeDb, pan = command.pan) }
            is EditorCommand.UpdateClipTransform -> updateClip(command.clipId) { it.copy(transform = command.transform) }
            is EditorCommand.UpdateColorGrading -> updateClip(command.clipId) { it.copy(colorParams = command.colorParams) }
            is EditorCommand.AddParametricEffect -> updateClip(command.clipId) { it.copy(effects = it.effects + command.effect) }
            is EditorCommand.RemoveEffect -> updateClip(command.clipId) { it.copy(effects = it.effects.filterNot { e -> e.id == command.effectId }) }
            is EditorCommand.SetClipTransitionIn -> updateClip(command.clipId) { it.copy(transitionIn = command.transition) }
            is EditorCommand.SetClipTransitionOut -> updateClip(command.clipId) { it.copy(transitionOut = command.transition) }
            EditorCommand.Undo -> history.undo(_project.value)?.let { _project.value = it; syncHistory(); markChanged(it) }
            EditorCommand.Redo -> history.redo(_project.value)?.let { _project.value = it; syncHistory(); markChanged(it) }
            EditorCommand.ToggleSnapping -> _isSnappingEnabled.value = !_isSnappingEnabled.value
            is EditorCommand.ZoomTimeline -> _zoomLevel.value = (_zoomLevel.value + command.delta).coerceIn(.25f, 8f)
            EditorCommand.ZoomToFit -> _zoomLevel.value = 1f
            is EditorCommand.ImportMediaClip -> importMedia(command.uri, command.name, command.durationUs)
            is EditorCommand.AddMediaAssetToTimeline -> mediaPool.find(command.assetId)?.let { asset ->
                importMedia(asset.uri, asset.name, asset.durationUs, command.startTimeUs, command.targetTrackId)
            }
            is EditorCommand.ApplyExternalEffectAsset -> {
                project.value.effectLibrary.firstOrNull { it.id == command.assetId }?.let { applyExternalEffectAsset(it, command.targetTimeUs) }
            }
            is EditorCommand.InsertSoundEffectAsset -> {
                project.value.effectLibrary.firstOrNull { it.id == command.assetId }?.let {
                    insertExternalAudioEffect(it, command.startTimeUs)
                }
            }
            is EditorCommand.RequestExport -> { _selectedExportPreset.value = command.preset; startExport(command.preset) }
            is EditorCommand.SetProjectSettings -> {
                val w = command.width.coerceIn(144, 7680); val h = command.height.coerceIn(144, 7680); val fps = command.fps.coerceIn(1, 240)
                commit(_project.value.copy(width = w, height = h, fps = fps, modifiedAtMs = System.currentTimeMillis()))
            }
        }
    }

    private fun seek(value: Long) { _currentTimeUs.value = value.coerceIn(0L, _project.value.durationUs) }
    private fun nextShuttleSpeed(v: Float, forward: Boolean): Float {
        val sign = if (forward) 1f else -1f
        val a = kotlin.math.abs(v)
        return sign * when (a) { 1f -> 2f; 2f -> 4f; 4f -> 8f; else -> 1f }
    }
    private fun startPlayback(speed: Float) {
        playbackJob?.cancel()
        _isPlaying.value = true
        _playbackSpeed.value = speed
        playbackJob = viewModelScope.launch {
            var lastNs = System.nanoTime()
            while (isActive && _isPlaying.value) {
                delay(8L)
                val nowNs = System.nanoTime()
                val elapsedUs = ((nowNs - lastNs) / 1_000L).coerceIn(1_000L, 50_000L)
                lastNs = nowNs
                val next = _currentTimeUs.value + (elapsedUs * speed).toLong()
                if (next <= 0L) {
                    _currentTimeUs.value = 0L
                    pausePlayback()
                    break
                }
                if (next >= _project.value.durationUs) {
                    _currentTimeUs.value = _project.value.durationUs
                    pausePlayback()
                    break
                }
                _currentTimeUs.value = next
            }
        }
    }
    private fun pausePlayback() { playbackJob?.cancel(); _isPlaying.value = false; _playbackSpeed.value = 1f }
    private fun findClip(id: String) = _project.value.tracks.flatMap { it.clips }.find { it.id == id }

    private fun splitAtPlayhead() {
        val clip = _selectedClipId.value?.let(::findClip) ?: _project.value.tracks.flatMap { it.clips }.find { _currentTimeUs.value in (it.startTimeUs + 1) until it.endTimeUs }
        if (clip != null && _currentTimeUs.value > clip.startTimeUs && _currentTimeUs.value < clip.endTimeUs) {
            commit(TimelineEditingEngine.splitLinkedClip(_project.value, clip.id, _currentTimeUs.value))
        }
    }
    private fun deleteSelected(ripple: Boolean) {
        val id = _selectedClipId.value ?: return
        val clip = findClip(id) ?: return
        val ids = buildSet {
            add(id)
            clip.linkedClipId?.let(::add)
        }
        commit(
            if (ripple) TimelineEditingEngine.rippleDelete(_project.value, ids)
            else TimelineEditingEngine.liftDelete(_project.value, ids)
        )
        _selectedClipId.value = null
    }
    private fun moveClipLinked(command: EditorCommand.MoveClip) {
        val clip = findClip(command.clipId) ?: return
        val trackId = command.targetTrackId ?: clip.trackId
        val delta = command.newStartTimeUs - clip.startTimeUs
        var updated = TimelineEditingEngine.moveClip(_project.value, clip.id, trackId, command.newStartTimeUs.coerceAtLeast(0L))
        val linkedId = clip.linkedClipId
        if (linkedId != null) {
            val linked = findClipIn(updated, linkedId)
            if (linked != null) updated = TimelineEditingEngine.moveClip(updated, linkedId, linked.trackId, (linked.startTimeUs + delta).coerceAtLeast(0L))
        }
        commit(updated)
    }
    private fun findClipIn(project: Project, id: String): TimelineClip? = project.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == id }
    private fun trimStart(c: EditorCommand.TrimClipStart) {
        val clip = findClip(c.clipId) ?: return
        val start = c.newStartTimeUs.coerceIn(
            clip.startTimeUs,
            clip.endTimeUs - TimelineEditingEngine.MIN_CLIP_DURATION_US
        )
        val trimmed = TimelineEditingEngine.trimClip(
            _project.value, clip.id, start, clip.endTimeUs - start, clip.trimInUs, clip.trimOutUs
        )
        val delta = start - clip.startTimeUs
        val updated = clip.linkedClipId?.let { linkedId ->
            val linked = findClipIn(trimmed, linkedId)
            if (linked != null) {
                trimmed.copy(
                    tracks = trimmed.tracks.map { track ->
                        track.copy(clips = track.clips.map { other ->
                            if (other.id == linked.id) other.copy(
                                startTimeUs = (other.startTimeUs + delta).coerceAtLeast(0L),
                                durationUs = (other.durationUs - delta).coerceAtLeast(TimelineEditingEngine.MIN_CLIP_DURATION_US)
                            ) else other
                        })
                    }
                )
            } else trimmed
        } ?: trimmed
        commit(updated)
    }

    private fun trimEnd(c: EditorCommand.TrimClipEnd) {
        val clip = findClip(c.clipId) ?: return
        val end = c.newEndTimeUs.coerceIn(
            clip.startTimeUs + TimelineEditingEngine.MIN_CLIP_DURATION_US,
            clip.endTimeUs + maxOf(0L, clip.sourceDurationUs - clip.durationUs)
        )
        val trimmed = TimelineEditingEngine.trimClip(
            _project.value, clip.id, clip.startTimeUs, end - clip.startTimeUs, clip.trimInUs, clip.trimOutUs
        )
        val updated = clip.linkedClipId?.let { linkedId ->
            val linked = findClipIn(trimmed, linkedId)
            if (linked != null) {
                val linkedDuration = (end - linked.startTimeUs).coerceAtLeast(TimelineEditingEngine.MIN_CLIP_DURATION_US)
                trimmed.copy(
                    tracks = trimmed.tracks.map { track ->
                        track.copy(clips = track.clips.map { other ->
                            if (other.id == linked.id) other.copy(durationUs = linkedDuration) else other
                        })
                    }
                )
            } else trimmed
        } ?: trimmed
        commit(updated)
    }
    private fun updateClip(id: String, fn: (TimelineClip) -> TimelineClip) {
        commit(_project.value.copy(tracks = _project.value.tracks.map { t -> t.copy(clips = t.clips.map { c -> if (c.id == id) fn(c) else c }) }, modifiedAtMs = System.currentTimeMillis()))
    }
    private fun commit(p: Project) { history.pushState(_project.value); _project.value = p; syncHistory(); markChanged(p) }
    private fun commit(transform: (Project) -> Project) { commit(transform(_project.value)) }
    private fun syncHistory() { _canUndo.value = history.canUndo; _canRedo.value = history.canRedo }

    private fun importMedia(uriString: String, fallbackName: String, fallbackDurationUs: Long, startTimeOverrideUs: Long? = null, targetTrackId: String? = null) {
        val uri = Uri.parse(uriString)
        viewModelScope.launch {
            val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                MediaMetadataExtractor.extractInfo(getApplication(), uri, fallbackName)
            }
            val resolver = getApplication<Application>().contentResolver
            val mime = resolver.getType(uri).orEmpty().lowercase()
            val sourceSizeBytes = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length > 0L } ?: 0L } ?: 0L
            }.getOrDefault(0L)
            val isImage = mime.startsWith("image/")
            val isVideo = info.isVideo || isImage
            val isAudio = info.isAudio

            val asset = mediaPool.findByUri(uriString)?.copy(
                name = fallbackName,
                durationUs = durationOrFallback(info.durationUs, fallbackDurationUs),
                width = info.width,
                height = info.height,
                fps = info.fps.toFloat(),
                sampleRate = info.audioSampleRate,
                channels = info.audioChannels,
                codec = info.videoMime ?: info.audioMime ?: "",
                sizeBytes = sourceSizeBytes,
                isOffline = false
            ) ?: MediaAsset(
                uri = uriString,
                name = fallbackName,
                durationUs = durationOrFallback(info.durationUs, fallbackDurationUs),
                width = info.width,
                height = info.height,
                fps = info.fps.toFloat(),
                sampleRate = info.audioSampleRate,
                channels = info.audioChannels,
                codec = info.videoMime ?: info.audioMime ?: "",
                sizeBytes = sourceSizeBytes
            )
            mediaPool.upsert(asset)
            _mediaAssets.value = mediaPool.all()
            _project.value = _project.value.copy(mediaPool = mediaPool.all(), modifiedAtMs = System.currentTimeMillis())

            val duration = when {
                isImage -> 5_000_000L
                info.durationUs > 0L -> info.durationUs
                else -> fallbackDurationUs.coerceAtLeast(1_000_000L)
            }
            val startTime = (startTimeOverrideUs ?: _currentTimeUs.value).coerceAtLeast(0L)

            if (isAudio && !isVideo) {
                val audioTrack = _project.value.tracks.firstOrNull { it.type == TrackType.AUDIO } ?: return@launch
                val audioClip = TimelineClip(
                    trackId = audioTrack.id,
                    mediaUri = uriString,
                    mediaName = fallbackName + " • Audio",
                    startTimeUs = startTime,
                    durationUs = duration,
                    sourceDurationUs = duration
                )
                commit(_project.value.copy(
                    tracks = _project.value.tracks.map { track ->
                        if (track.id == audioTrack.id) track.copy(clips = track.clips + audioClip) else track
                    },
                    modifiedAtMs = System.currentTimeMillis()
                ))
                _selectedClipId.value = audioClip.id
                seek(startTime)
                launch {
                    val waveform = AudioWaveformExtractor.extract(getApplication(), uri, 240)
                    if (waveform.isNotEmpty()) {
                        _project.value = _project.value.copy(
                            tracks = _project.value.tracks.map { track ->
                                track.copy(clips = track.clips.map { clip ->
                                    if (clip.id == audioClip.id) clip.copy(waveform = waveform) else clip
                                })
                            },
                            modifiedAtMs = System.currentTimeMillis()
                        ).also { markChanged(it) }
                    }
                }
                return@launch
            }

            if (!isVideo) return@launch
            val requestedTrack = targetTrackId?.let { id -> _project.value.tracks.firstOrNull { it.id == id } }
            val videoTrack = requestedTrack?.takeIf { it.type == TrackType.VIDEO }
                ?: _project.value.tracks.firstOrNull { it.type == TrackType.VIDEO } ?: return@launch
            val audioTrack = if (isAudio) {
                requestedTrack?.takeIf { it.type == TrackType.AUDIO }
                    ?: _project.value.tracks.firstOrNull { it.type == TrackType.AUDIO }
            } else null

            val videoClip = TimelineClip(
                trackId = videoTrack.id,
                mediaUri = uriString,
                mediaName = fallbackName,
                startTimeUs = startTime,
                durationUs = duration,
                sourceDurationUs = duration
            )
            val audioClip = audioTrack?.let { track ->
                TimelineClip(
                    trackId = track.id,
                    mediaUri = uriString,
                    mediaName = fallbackName + " • Audio",
                    startTimeUs = startTime,
                    durationUs = duration,
                    sourceDurationUs = duration,
                    linkedClipId = videoClip.id
                )
            }
            val linkedVideoClip = videoClip.copy(linkedClipId = audioClip?.id)

            commit(_project.value.copy(
                tracks = _project.value.tracks.map { track ->
                    when {
                        track.id == videoTrack.id -> track.copy(clips = track.clips + linkedVideoClip)
                        audioClip != null && track.id == audioClip.trackId -> track.copy(clips = track.clips + audioClip)
                        else -> track
                    }
                },
                modifiedAtMs = System.currentTimeMillis()
            ))
            _selectedClipId.value = linkedVideoClip.id
            seek(startTime)

            if (audioClip != null) {
                launch {
                    val waveform = AudioWaveformExtractor.extract(getApplication(), uri, 240)
                    if (waveform.isNotEmpty()) {
                        _project.value = _project.value.copy(
                            tracks = _project.value.tracks.map { track ->
                                track.copy(clips = track.clips.map { clip ->
                                    if (clip.id == audioClip.id) clip.copy(waveform = waveform) else clip
                                })
                            },
                            modifiedAtMs = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }

    private fun durationOrFallback(actualUs: Long, fallbackUs: Long): Long =
        actualUs.takeIf { it > 0L } ?: fallbackUs.coerceAtLeast(1_000_000L)

    private fun startExport(preset: ExportPreset) {
        exportJob?.cancel()
        _exportError.value = null; _exportProgress.value = 0f; _exportUiState.value = ExportUiState.EXPORTING
        val snapshot = _project.value
        exportJob = viewModelScope.launch {
            ExportPipeline(getApplication(), snapshot, preset).execute(
                onProgress = { _exportProgress.value = it },
                onComplete = { _exportUiState.value = ExportUiState.SUCCESS },
                onError = { _exportError.value = it.message ?: it.javaClass.simpleName; _exportUiState.value = ExportUiState.ERROR }
            )
        }
    }

    fun getSelectedClip(): TimelineClip? = _selectedClipId.value?.let(::findClip)

    companion object {
        fun formatTimecode(timeUs: Long, fps: Int = 30): String {
            val total = timeUs.coerceAtLeast(0) / 1_000_000L
            val frames = ((timeUs % 1_000_000L) * fps / 1_000_000L).toInt()
            return "%02d:%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60, frames)
        }
        fun createEmptyProject(mediaPool: List<MediaAsset> = emptyList()): Project = Project(
            name = "DOK • Untitled", width = 1920, height = 1080, fps = 30,
            tracks = listOf(Track(id = "V1", name = "V1", type = TrackType.VIDEO), Track(id = "V2", name = "V2", type = TrackType.VIDEO), Track(id = "A1", name = "A1", type = TrackType.AUDIO), Track(id = "A2", name = "A2", type = TrackType.AUDIO), Track(id = "T1", name = "T1", type = TrackType.TEXT)),
            mediaPool = mediaPool
        )
    }
    fun applyExternalEffectAsset(asset: ExternalEffectAsset, targetTimeUs: Long? = null) {
        addExternalEffectAsset(asset)
        val clipId = _selectedClipId.value
            ?: targetTimeUs?.let { t -> _project.value.tracks.flatMap { it.clips }.firstOrNull { it.mediaUri.isNotBlank() && t in it.startTimeUs until it.endTimeUs }?.id }
            ?: return
        val path = Uri.parse(asset.uri).path ?: return
        val file = File(path)
        when {
            asset.kind == "lut" || asset.name.endsWith(".cube", ignoreCase = true) -> {
                updateClip(clipId) { clip ->
                    clip.copy(colorParams = clip.colorParams.copy(lutCubeUri = file.toURI().toString()))
                }
            }
            asset.kind == "preset" || asset.name.endsWith(".json", ignoreCase = true) -> {
                runCatching {
                    val o = JSONObject(file.readText())
                    val type = EffectType.valueOf(o.optString("effectType"))
                    val params = mutableMapOf<String, Float>()
                    o.optJSONObject("parameters")?.let { po -> po.keys().forEach { key -> params[key] = po.optDouble(key).toFloat() } }
                    dispatch(EditorCommand.AddParametricEffect(
                        clipId,
                        Effect.ParametricEffect(effectType = type, intensity = o.optDouble("intensity", 1.0).toFloat(), params = params)
                    ))
                }
            }
        }
    }

    fun insertExternalAudioEffect(asset: ExternalEffectAsset, startTimeUs: Long = _currentTimeUs.value) {
        addExternalEffectAsset(asset)
        val uri = Uri.parse(asset.uri)
        val durationFallback = runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(getApplication<Application>(), uri)
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1000L
            retriever.release()
            durationMs * 1000L
        }.getOrDefault(1_000_000L)
        importMedia(uri.toString(), asset.name, durationFallback, startTimeUs.coerceAtLeast(0L), null)
    }

    fun exportSrtText(): String {
        val cues = _project.value.tracks
            .filter { it.type == TrackType.TEXT }
            .flatMap { it.clips }
            .mapIndexed { index, clip ->
                val text = clip.textOverlay?.text?.trim().orEmpty()
                com.dok.editor.engine.subtitle.SubtitleCue(
                    index = index + 1,
                    startTimeUs = clip.startTimeUs,
                    endTimeUs = clip.endTimeUs,
                    text = text
                )
            }
            .filter { it.text.isNotBlank() && it.endTimeUs > it.startTimeUs }
        return SrtSubtitleCodec.write(cues)
    }

    fun writeSrtToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(exportSrtText().toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open destination")
            }
        }
    }

    fun importSrtSubtitles(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: return@launch
            val cues = SrtSubtitleCodec.parse(text)
            if (cues.isEmpty()) return@launch
            launch(Dispatchers.Main.immediate) {
                commit { project ->
                    val existing = project.tracks.firstOrNull { it.type == TrackType.TEXT }
                    val trackId = existing?.id ?: "subtitles-" + java.util.UUID.randomUUID()
                    val clips = cues.map { cue ->
                        TimelineClip(
                            trackId = trackId, mediaUri = "", mediaName = "SRT " + cue.index,
                            startTimeUs = cue.startTimeUs,
                            durationUs = (cue.endTimeUs - cue.startTimeUs).coerceAtLeast(1L),
                            textOverlay = TextOverlayConfig(text = cue.text)
                        )
                    }
                    if (existing == null) project.copy(tracks = project.tracks + Track(trackId, "Subtitles", TrackType.TEXT, clips = clips))
                    else project.copy(tracks = project.tracks.map { if (it.id == trackId) it.copy(clips = it.clips + clips) else it })
                }
            }
        }
    }

    fun addExternalEffectAsset(asset: com.dok.editor.model.ExternalEffectAsset) {
        commit { project ->
            if (project.effectLibrary.any { it.id == asset.id }) project
            else project.copy(effectLibrary = project.effectLibrary + asset)
        }
    }

    fun removeExternalEffectAsset(assetId: String) {
        commit { project -> project.copy(effectLibrary = project.effectLibrary.filterNot { it.id == assetId }) }
    }

}
