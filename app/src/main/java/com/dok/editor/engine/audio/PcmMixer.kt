package com.dok.editor.engine.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object PcmMixer {

    const val SAMPLE_RATE_44K = 44100
    const val CHANNEL_COUNT_STEREO = 2

    /**
     * Converts decibels to linear amplitude gain.
     * 0 dB -> 1.0
     * -6 dB -> ~0.501
     * -inf / mute -> 0.0
     */
    fun dbToLinear(db: Float): Float {
        if (db <= -96.0f) return 0.0f
        return 10.0f.pow(db / 20.0f)
    }

    /**
     * Computes left and right channel gains using the Unity-Gain-Center pan law.
     * pan in [-1.0f .. +1.0f]:
     * pan == 0.0: left = 1.0, right = 1.0 (unity at center)
     * pan < 0.0 (left): left = 1.0, right = 1.0 + pan (at -1.0, right = 0.0)
     * pan > 0.0 (right): left = 1.0 - pan, right = 1.0 (at +1.0, left = 0.0)
     */
    fun calculatePanGains(pan: Float): Pair<Float, Float> {
        val clampedPan = pan.coerceIn(-1.0f, 1.0f)
        return when {
            clampedPan < 0f -> Pair(1.0f, 1.0f + clampedPan)
            clampedPan > 0f -> Pair(1.0f - clampedPan, 1.0f)
            else -> Pair(1.0f, 1.0f)
        }
    }

    /**
     * Calculates linear fade envelope multiplier (0.0 .. 1.0) at a specific timeline timestamp.
     */
    fun calculateFadeEnvelope(
        currentPositionUs: Long,
        clipStartTimeUs: Long,
        clipDurationUs: Long,
        fadeInUs: Long,
        fadeOutUs: Long
    ): Float {
        val elapsedUs = currentPositionUs - clipStartTimeUs
        if (elapsedUs < 0 || elapsedUs > clipDurationUs) return 0.0f

        var gain = 1.0f

        if (fadeInUs > 0L && elapsedUs < fadeInUs) {
            gain = (elapsedUs.toFloat() / fadeInUs.toFloat()).coerceIn(0.0f, 1.0f)
        }

        val remainingUs = clipDurationUs - elapsedUs
        if (fadeOutUs > 0L && remainingUs < fadeOutUs) {
            val outGain = (remainingUs.toFloat() / fadeOutUs.toFloat()).coerceIn(0.0f, 1.0f)
            gain = min(gain, outGain)
        }

        return gain
    }

    /**
     * Resamples audio by ABSOLUTE sample position.
     * This guarantees that chunked streaming produces identical samples
     * to a single monolithic resample without boundary phase clicks or drift.
     *
     * @param sourcePcm Interleaved stereo float array [L0, R0, L1, R1, ...]
     * @param sourceSampleRate Source sample rate (e.g. 48000)
     * @param absoluteOutputStartFrame Index of first frame to render relative to the clip origin
     * @param outputFrameCount Number of stereo frames to produce
     * @param speed Playback speed factor (e.g. 1.0, 1.5, 0.5)
     * @param targetSampleRate Output sample rate (default 44100)
     * @return Interleaved stereo float array of length (outputFrameCount * 2)
     */
    fun resampleByAbsolutePosition(
        sourcePcm: FloatArray,
        sourceSampleRate: Int,
        absoluteOutputStartFrame: Long,
        outputFrameCount: Int,
        speed: Float = 1.0f,
        targetSampleRate: Int = SAMPLE_RATE_44K
    ): FloatArray {
        val output = FloatArray(outputFrameCount * 2)
        val sourceTotalFrames = sourcePcm.size / 2
        if (sourceTotalFrames <= 0) return output

        val rateRatio = (sourceSampleRate.toDouble() * speed.toDouble()) / targetSampleRate.toDouble()

        for (i in 0 until outputFrameCount) {
            val absoluteFrame = absoluteOutputStartFrame + i
            val exactSourceFrame = absoluteFrame * rateRatio

            val s0 = exactSourceFrame.toLong()
            val frac = (exactSourceFrame - s0).toFloat()

            if (s0 < 0) {
                output[i * 2] = 0f
                output[i * 2 + 1] = 0f
            } else if (s0 >= sourceTotalFrames - 1) {
                if (s0 == (sourceTotalFrames - 1).toLong()) {
                    output[i * 2] = sourcePcm[s0.toInt() * 2]
                    output[i * 2 + 1] = sourcePcm[s0.toInt() * 2 + 1]
                } else {
                    output[i * 2] = 0f
                    output[i * 2 + 1] = 0f
                }
            } else {
                val idx0 = s0.toInt() * 2
                val idx1 = idx0 + 2
                val l0 = sourcePcm[idx0]
                val r0 = sourcePcm[idx0 + 1]
                val l1 = sourcePcm[idx1]
                val r1 = sourcePcm[idx1 + 1]

                output[i * 2] = l0 + (l1 - l0) * frac
                output[i * 2 + 1] = r0 + (r1 - r0) * frac
            }
        }

        return output
    }

    /**
     * Applies a studio-grade soft-knee peak limiter to an interleaved stereo buffer.
     * Prevents harsh digital clipping when multiple tracks sum beyond +/- 1.0.
     * Threshold: 0.90 (-0.9 dB), smooth tanh-like polynomial knee.
     */
    fun applySoftKneeLimiter(buffer: FloatArray, threshold: Float = 0.89125f) { // ~ -1 dBFS
        for (i in buffer.indices) {
            val x = buffer[i]
            val absX = abs(x)
            if (absX <= threshold) {
                // Linear region: clean unity passthrough
                continue
            }
            // Soft-knee compression above threshold
            val excess = absX - threshold
            val compressed = threshold + (1.0f - threshold) * (excess / (excess + 1.0f))
            buffer[i] = if (x > 0f) min(compressed, 0.999f) else max(-compressed, -0.999f)
        }
    }

    /**
     * Converts a normalized FloatArray (-1.0 .. 1.0) into 16-bit PCM ShortArray.
     */
    fun floatToPcm16(input: FloatArray): ShortArray {
        val output = ShortArray(input.size)
        for (i in input.indices) {
            val clamped = input[i].coerceIn(-1.0f, 1.0f)
            output[i] = (clamped * 32767.0f).toInt().toShort()
        }
        return output
    }

    /**
     * Converts 16-bit PCM ShortArray into normalized FloatArray (-1.0 .. 1.0).
     */
    fun pcm16ToFloat(input: ShortArray): FloatArray {
        val output = FloatArray(input.size)
        for (i in input.indices) {
            output[i] = input[i] / 32768.0f
        }
        return output
    }
}
