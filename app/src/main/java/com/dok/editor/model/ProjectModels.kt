package com.dok.editor.model

import java.util.UUID

enum class TrackType { VIDEO, AUDIO, TEXT }
enum class KeyframeProperty { POSITION_X, POSITION_Y, SCALE_X, SCALE_Y, ROTATION, OPACITY, VOLUME }
enum class InterpolationType { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    val timestampOffsetUs: Long,
    val property: KeyframeProperty,
    val value: Float,
    val interpolation: InterpolationType = InterpolationType.LINEAR
)

data class Transform2D(
    val positionX: Float = 0f, val positionY: Float = 0f,
    val scaleX: Float = 1f, val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f, val opacity: Float = 1f
)

data class ColorGradingParams(
    val brightness: Float = 0f, val contrast: Float = 1f, val saturation: Float = 1f,
    val temperature: Float = 0f, val vignette: Float = 0f, val lutCubeUri: String? = null
)

enum class TransitionType { CROSSFADE, DIP_TO_BLACK, DIP_TO_WHITE, WIPE_LEFT, WIPE_RIGHT }
data class TransitionConfig(val type: TransitionType = TransitionType.CROSSFADE, val durationUs: Long = 500_000L)

enum class EffectType { ZOOM, SHAKE, RGB_SPLIT, GLITCH, FLASH, GLOW, VIGNETTE, COLOR_GRADING, LUT_3D }

sealed interface Effect {
    val id: String
    val effectType: EffectType
    val intensity: Float
    data class ParametricEffect(
        override val id: String = UUID.randomUUID().toString(),
        override val effectType: EffectType,
        override val intensity: Float = 1f,
        val params: Map<String, Float> = emptyMap()
    ) : Effect
    data class NodeGraphEffect(
        override val id: String = UUID.randomUUID().toString(),
        override val effectType: EffectType = EffectType.COLOR_GRADING,
        override val intensity: Float = 1f,
        val graphDefinitionJson: String = ""
    ) : Effect
}

data class TextOverlayConfig(
    val text: String = "Text Overlay", val fontSizeSp: Float = 24f,
    val textColorHex: String = "#FFFFFF", val backgroundColorHex: String = "#00000000",
    val positionX: Float = .5f, val positionY: Float = .5f,
    val isBold: Boolean = true, val isItalic: Boolean = false
)

data class TimelineClip(
    val id: String = UUID.randomUUID().toString(), val trackId: String, val mediaUri: String,
    val mediaName: String = "", val isOffline: Boolean = false,
    val startTimeUs: Long, val durationUs: Long, val trimInUs: Long = 0L, val trimOutUs: Long = 0L,
    val sourceDurationUs: Long = 0L, val speed: Float = 1f, val volumeDb: Float = 0f,
    val pan: Float = 0f, val fadeInUs: Long = 0L, val fadeOutUs: Long = 0L,
    val isMuted: Boolean = false, val transform: Transform2D = Transform2D(),
    val keyframes: List<Keyframe> = emptyList(), val colorParams: ColorGradingParams = ColorGradingParams(),
    val effects: List<Effect.ParametricEffect> = emptyList(),
    val transitionIn: TransitionConfig? = null, val transitionOut: TransitionConfig? = null,
    val textOverlay: TextOverlayConfig? = null
) { val endTimeUs: Long get() = startTimeUs + durationUs }

data class Track(
    val id: String = UUID.randomUUID().toString(), val name: String, val type: TrackType,
    val isMuted: Boolean = false, val isSolo: Boolean = false, val isLocked: Boolean = false,
    val volumeDb: Float = 0f, val pan: Float = 0f, val clips: List<TimelineClip> = emptyList()
)

data class ProjectSettings(
    val width: Int = 1920, val height: Int = 1080, val fps: Int = 30,
    val pixelAspect: String = "1:1", val colorSpace: String = "Rec.709"
)

data class Project(
    val id: String = UUID.randomUUID().toString(), val name: String = "Untitled Edit",
    val width: Int = 1920, val height: Int = 1080, val fps: Int = 30, val schemaVersion: Int = 1,
    val tracks: List<Track> = emptyList(), val createdAtMs: Long = System.currentTimeMillis(),
    val modifiedAtMs: Long = System.currentTimeMillis()
) {
    val settings: ProjectSettings get() = ProjectSettings(width, height, fps)
    val durationUs: Long get() = tracks.flatMap { it.clips }.maxOfOrNull { it.endTimeUs } ?: 0L
    val totalDurationUs: Long get() = durationUs
}
