package com.dok.editor.model

import java.util.UUID

enum class TrackType {
    VIDEO,
    AUDIO,
    TEXT
}

enum class KeyframeProperty {
    POSITION_X,
    POSITION_Y,
    SCALE_X,
    SCALE_Y,
    ROTATION,
    OPACITY,
    VOLUME
}

enum class InterpolationType {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT
}

data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    val timestampOffsetUs: Long, // relative to clip startTimeUs
    val property: KeyframeProperty,
    val value: Float,
    val interpolation: InterpolationType = InterpolationType.LINEAR
)

data class Transform2D(
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1.0f
)

data class ColorGradingParams(
    val brightness: Float = 0f,      // -1.0 .. +1.0 (default 0)
    val contrast: Float = 1.0f,      // 0.0 .. 2.0 (default 1)
    val saturation: Float = 1.0f,    // 0.0 .. 2.0 (default 1)
    val temperature: Float = 0f,     // -1.0 (cool blue) .. +1.0 (warm amber)
    val vignette: Float = 0f,        // 0.0 (none) .. 1.0 (heavy)
    val lutCubeUri: String? = null   // Optional external .cube LUT uri or path
)

enum class TransitionType {
    CROSSFADE,
    DIP_TO_BLACK,
    DIP_TO_WHITE,
    WIPE_LEFT,
    WIPE_RIGHT
}

data class TransitionConfig(
    val type: TransitionType = TransitionType.CROSSFADE,
    val durationUs: Long = 500_000L // 0.5s default
)

enum class EffectType {
    ZOOM,
    SHAKE,
    RGB_SPLIT,
    GLITCH,
    FLASH,
    GLOW,
    VIGNETTE,
    COLOR_GRADING,
    LUT_3D
}

sealed interface Effect {
    val id: String
    val effectType: EffectType
    val intensity: Float

    data class ParametricEffect(
        override val id: String = UUID.randomUUID().toString(),
        override val effectType: EffectType,
        override val intensity: Float = 1.0f,
        val params: Map<String, Float> = emptyMap()
    ) : Effect

    // Extension point for future Fusion node graphs (V2)
    data class NodeGraphEffect(
        override val id: String = UUID.randomUUID().toString(),
        override val effectType: EffectType = EffectType.COLOR_GRADING,
        override val intensity: Float = 1.0f,
        val graphDefinitionJson: String = ""
    ) : Effect
}

data class TextOverlayConfig(
    val text: String = "Text Overlay",
    val fontSizeSp: Float = 24f,
    val textColorHex: String = "#FFFFFF",
    val backgroundColorHex: String = "#00000000",
    val positionX: Float = 0.5f, // 0..1 normalized
    val positionY: Float = 0.5f,
    val isBold: Boolean = true,
    val isItalic: Boolean = false
)

data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val trackId: String,
    val mediaUri: String,
    val mediaName: String = "",
    val isOffline: Boolean = false,
    val startTimeUs: Long,       // Position on timeline
    val durationUs: Long,        // Duration on timeline
    val trimInUs: Long = 0L,     // Trim in point within media source
    val trimOutUs: Long = 0L,    // Trim out point within media source (0 = full length)
    val sourceDurationUs: Long = 0L,
    val speed: Float = 1.0f,
    val volumeDb: Float = 0.0f,  // dB gain (0dB = unity)
    val pan: Float = 0.0f,       // -1.0 (left) .. 1.0 (right)
    val fadeInUs: Long = 0L,
    val fadeOutUs: Long = 0L,
    val isMuted: Boolean = false,
    val transform: Transform2D = Transform2D(),
    val keyframes: List<Keyframe> = emptyList(),
    val colorParams: ColorGradingParams = ColorGradingParams(),
    val effects: List<Effect.ParametricEffect> = emptyList(),
    val transitionIn: TransitionConfig? = null,
    val transitionOut: TransitionConfig? = null,
    val textOverlay: TextOverlayConfig? = null
) {
    val endTimeUs: Long get() = startTimeUs + durationUs
}

data class Track(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TrackType,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isLocked: Boolean = false,
    val volumeDb: Float = 0.0f,
    val pan: Float = 0.0f,
    val clips: List<TimelineClip> = emptyList()
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Gaming Edit",
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val schemaVersion: Int = 1,
    val tracks: List<Track> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val modifiedAtMs: Long = System.currentTimeMillis()
) {
    val durationUs: Long
        get() = tracks.flatMap { it.clips }.maxOfOrNull { it.endTimeUs } ?: 0L

    val totalDurationUs: Long
        get() = durationUs
}
