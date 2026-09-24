package com.dok.editor.command

import com.dok.editor.engine.export.ExportPreset
import com.dok.editor.model.ColorGradingParams
import com.dok.editor.model.Effect
import com.dok.editor.model.Transform2D
import com.dok.editor.model.TransitionConfig

sealed interface EditorCommand {
    object TogglePlayPause : EditorCommand
    object Play : EditorCommand
    object Pause : EditorCommand
    object Stop : EditorCommand
    object ShuttleReverse : EditorCommand
    object ShuttleForward : EditorCommand
    object ShuttleStop : EditorCommand
    data class StepFrames(val frames: Int) : EditorCommand
    data class ScrubTo(val timeUs: Long) : EditorCommand
    object JumpToStart : EditorCommand
    object JumpToEnd : EditorCommand
    data class SetInPoint(val timeUs: Long) : EditorCommand
    data class SetOutPoint(val timeUs: Long) : EditorCommand
    object ClearInOutPoints : EditorCommand
    object SplitClipAtPlayhead : EditorCommand
    object DeleteSelectedClip : EditorCommand
    object RippleDeleteSelectedClip : EditorCommand
    data class SelectClip(val clipId: String?) : EditorCommand
    data class SelectTrack(val trackId: String?) : EditorCommand
    data class TrimClipStart(val clipId: String, val newStartTimeUs: Long) : EditorCommand
    data class TrimClipEnd(val clipId: String, val newEndTimeUs: Long) : EditorCommand
    data class MoveClip(val clipId: String, val newStartTimeUs: Long, val targetTrackId: String? = null) : EditorCommand
    data class ChangeClipSpeed(val clipId: String, val speed: Float) : EditorCommand
    data class UpdateClipTransform(val clipId: String, val transform: Transform2D) : EditorCommand
    data class UpdateColorGrading(val clipId: String, val colorParams: ColorGradingParams) : EditorCommand
    data class AddParametricEffect(val clipId: String, val effect: Effect.ParametricEffect) : EditorCommand
    data class RemoveEffect(val clipId: String, val effectId: String) : EditorCommand
    data class SetClipTransitionIn(val clipId: String, val transition: TransitionConfig?) : EditorCommand
    data class SetClipTransitionOut(val clipId: String, val transition: TransitionConfig?) : EditorCommand
    object Undo : EditorCommand
    object Redo : EditorCommand
    object ToggleSnapping : EditorCommand
    data class ZoomTimeline(val delta: Float) : EditorCommand
    object ZoomToFit : EditorCommand
    data class ImportMediaClip(val uri: String, val name: String, val durationUs: Long) : EditorCommand
    data class RequestExport(val preset: ExportPreset) : EditorCommand
    data class SetProjectSettings(val width: Int, val height: Int, val fps: Int) : EditorCommand
}
