package com.dok.editor.engine

import com.dok.editor.model.InterpolationType
import com.dok.editor.model.Keyframe
import kotlin.math.pow

object KeyframeEngine {
    fun evaluate(keyframes: List<Keyframe>, offsetUs: Long, defaultValue: Float): Float {
        if (keyframes.isEmpty()) return defaultValue
        val sorted = keyframes.sortedBy { it.timestampOffsetUs }
        if (offsetUs <= sorted.first().timestampOffsetUs) return sorted.first().value
        if (offsetUs >= sorted.last().timestampOffsetUs) return sorted.last().value
        val right = sorted.first { it.timestampOffsetUs >= offsetUs }
        val left = sorted.last { it.timestampOffsetUs <= offsetUs }
        if (right.timestampOffsetUs == left.timestampOffsetUs) return left.value
        val rawT = (offsetUs - left.timestampOffsetUs).toFloat() /
            (right.timestampOffsetUs - left.timestampOffsetUs).toFloat()
        val t = when (right.interpolation) {
            InterpolationType.LINEAR -> rawT
            InterpolationType.EASE_IN -> rawT.pow(2f)
            InterpolationType.EASE_OUT -> 1f - (1f - rawT).pow(2f)
            InterpolationType.EASE_IN_OUT -> if (rawT < .5f) 2f * rawT * rawT else 1f - (-2f * rawT + 2f).pow(2f) / 2f
        }
        return left.value + (right.value - left.value) * t
    }

    fun upsert(
        keyframes: List<Keyframe>,
        timestampOffsetUs: Long,
        property: com.dok.editor.model.KeyframeProperty,
        value: Float,
        interpolation: InterpolationType = InterpolationType.LINEAR
    ): List<Keyframe> {
        val filtered = keyframes.filterNot {
            it.property == property && it.timestampOffsetUs == timestampOffsetUs
        }
        return (filtered + Keyframe(
            timestampOffsetUs = timestampOffsetUs,
            property = property,
            value = value,
            interpolation = interpolation
        )).sortedBy { it.timestampOffsetUs }
    }
}
