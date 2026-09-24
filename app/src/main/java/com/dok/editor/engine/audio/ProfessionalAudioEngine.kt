package com.dok.editor.engine.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

data class EqBand(val frequencyHz: Float, val gainDb: Float, val q: Float = 1f)
data class CompressorSettings(
    val thresholdDb: Float = -18f,
    val ratio: Float = 4f,
    val attackMs: Float = 10f,
    val releaseMs: Float = 100f
)
data class LimiterSettings(val ceilingDb: Float = -1f)
data class AudioAutomationPoint(val timeUs: Long, val value: Float)

class ProfessionalAudioEngine {
    fun applyEq(x: FloatArray, sampleRate: Int, bands: List<EqBand>): FloatArray {
        val o = x.copyOf()
        for (band in bands) {
            val w = 2.0 * Math.PI * band.frequencyHz.toDouble() / sampleRate.toDouble()
            val alpha = Math.sin(w) / (2.0 * band.q.toDouble().coerceAtLeast(0.0001))
            val aGain = 10.0.pow(band.gainDb.toDouble() / 40.0)
            val b0 = 1.0 + alpha * aGain
            val b1 = -2.0 * Math.cos(w)
            val b2 = 1.0 - alpha * aGain
            val a0 = 1.0 + alpha / aGain
            val a1 = -2.0 * Math.cos(w)
            val a2 = 1.0 - alpha / aGain

            var x1 = 0.0
            var x2 = 0.0
            var y1 = 0.0
            var y2 = 0.0
            for (i in o.indices) {
                val sample = x[i].toDouble()
                val y = (b0 / a0) * sample +
                    (b1 / a0) * x1 +
                    (b2 / a0) * x2 -
                    (a1 / a0) * y1 -
                    (a2 / a0) * y2
                o[i] = y.toFloat()
                x2 = x1
                x1 = sample
                y2 = y1
                y1 = y
            }
        }
        return o
    }

    fun compress(x: FloatArray, settings: CompressorSettings): FloatArray {
        val o = x.copyOf()
        val attack = exp(-1.0 / (settings.attackMs.coerceAtLeast(0.1f).toDouble() * 48.0))
        val release = exp(-1.0 / (settings.releaseMs.coerceAtLeast(1f).toDouble() * 48.0))
        val threshold = 10.0.pow(settings.thresholdDb.toDouble() / 20.0)
        val ratio = settings.ratio.coerceAtLeast(1f).toDouble()

        var envelope = 0.0
        for (i in o.indices) {
            val level = abs(o[i].toDouble())
            envelope = if (level > envelope) {
                attack * envelope + (1.0 - attack) * level
            } else {
                release * envelope + (1.0 - release) * level
            }

            if (envelope > threshold) {
                val over = envelope / threshold
                val gainReduction = over.pow(1.0 - 1.0 / ratio)
                o[i] = (o[i] / gainReduction).toFloat()
            }
        }
        return o
    }

    fun limit(x: FloatArray, settings: LimiterSettings): FloatArray {
        val ceiling = 10.0.pow(settings.ceilingDb.toDouble() / 20.0).toFloat()
        return x.copyOf().also { samples ->
            for (i in samples.indices) {
                samples[i] = samples[i].coerceIn(-ceiling, ceiling)
            }
        }
    }

    fun automate(base: Float, timeUs: Long, points: List<AudioAutomationPoint>): Float {
        if (points.isEmpty()) return base
        val sorted = points.sortedBy { it.timeUs }
        if (timeUs <= sorted.first().timeUs) return sorted.first().value
        if (timeUs >= sorted.last().timeUs) return sorted.last().value

        val right = sorted.first { it.timeUs >= timeUs }
        val left = sorted.last { it.timeUs <= timeUs }
        val span = (right.timeUs - left.timeUs).coerceAtLeast(1L)
        val t = (timeUs - left.timeUs).toFloat() / span.toFloat()
        return left.value + (right.value - left.value) * t
    }
}
