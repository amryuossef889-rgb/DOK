package com.dok.editor

import com.dok.editor.engine.effects.CubeLutParser
import com.dok.editor.engine.effects.DokShaders
import com.dok.editor.engine.effects.EffectsEvaluator
import com.dok.editor.model.ColorGradingParams
import com.dok.editor.model.Effect
import com.dok.editor.model.EffectType
import com.dok.editor.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EffectsEngineTest {

    @Test
    fun testCubeLutParserAndTrilinearSample() {
        val cubeData = """
            TITLE "TestIdentityLut"
            LUT_3D_SIZE 2
            DOMAIN_MIN 0.0 0.0 0.0
            DOMAIN_MAX 1.0 1.0 1.0
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val lut = CubeLutParser.parse(cubeData)
        assertEquals("TestIdentityLut", lut.title)
        assertEquals(2, lut.size)
        assertEquals(24, lut.table.size) // 2*2*2 * 3 = 24 floats

        // Sample origin
        val origin = lut.sample(0.0f, 0.0f, 0.0f)
        assertEquals(0.0f, origin.first, 0.001f)
        assertEquals(0.0f, origin.second, 0.001f)
        assertEquals(0.0f, origin.third, 0.001f)

        // Sample white corner
        val white = lut.sample(1.0f, 1.0f, 1.0f)
        assertEquals(1.0f, white.first, 0.001f)
        assertEquals(1.0f, white.second, 0.001f)
        assertEquals(1.0f, white.third, 0.001f)

        // Sample midpoint (trilinear interpolation in identity cube yields 0.5, 0.5, 0.5)
        val mid = lut.sample(0.5f, 0.5f, 0.5f)
        assertEquals(0.5f, mid.first, 0.001f)
        assertEquals(0.5f, mid.second, 0.001f)
        assertEquals(0.5f, mid.third, 0.001f)

        // Sample out-of-bounds (clamped)
        val clamped = lut.sample(1.5f, -0.5f, 0.5f)
        assertEquals(1.0f, clamped.first, 0.001f)
        assertEquals(0.0f, clamped.second, 0.001f)
        assertEquals(0.5f, clamped.third, 0.001f)
    }

    @Test
    fun testCubeLutParserInvalidFormat() {
        val invalidCube = """
            TITLE "Invalid"
            LUT_3D_SIZE 2
            0.0 0.0 0.0
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            CubeLutParser.parse(invalidCube)
        }
    }

    @Test
    fun testTransitionEvaluatorAndTypeCodes() {
        assertEquals(0, EffectsEvaluator.transitionTypeCode(null))
        assertEquals(1, EffectsEvaluator.transitionTypeCode(TransitionType.CROSSFADE))
        assertEquals(2, EffectsEvaluator.transitionTypeCode(TransitionType.DIP_TO_BLACK))
        assertEquals(3, EffectsEvaluator.transitionTypeCode(TransitionType.DIP_TO_WHITE))
        assertEquals(4, EffectsEvaluator.transitionTypeCode(TransitionType.WIPE_LEFT))
        assertEquals(5, EffectsEvaluator.transitionTypeCode(TransitionType.WIPE_RIGHT))

        // Dip factor test
        assertEquals(1.0f, EffectsEvaluator.calculateDipFactor(0.0f), 0.001f) // Start: fully visible
        assertEquals(0.0f, EffectsEvaluator.calculateDipFactor(0.5f), 0.001f) // Midpoint: fully dipped
        assertEquals(1.0f, EffectsEvaluator.calculateDipFactor(1.0f), 0.001f) // End: fully visible
        assertEquals(0.5f, EffectsEvaluator.calculateDipFactor(0.25f), 0.001f)
        assertEquals(0.5f, EffectsEvaluator.calculateDipFactor(0.75f), 0.001f)
    }

    @Test
    fun testParametricEffectsEvaluator() {
        // Shake
        val noShake = EffectsEvaluator.calculateShakeOffset(0f, 1.0f)
        assertEquals(0f, noShake.first, 0.0001f)
        assertEquals(0f, noShake.second, 0.0001f)

        val shake = EffectsEvaluator.calculateShakeOffset(1.0f, 0.25f)
        // Shake should be within bounds [-0.04 .. 0.04]
        assertTrue(shake.first >= -0.05f && shake.first <= 0.05f)
        assertTrue(shake.second >= -0.05f && shake.second <= 0.05f)

        // Zoom
        assertEquals(1.0f, EffectsEvaluator.calculateZoomScale(0f, 1.0f), 0.001f)
        val zoom = EffectsEvaluator.calculateZoomScale(1.0f, 1.0f)
        assertEquals(1.5f, zoom, 0.001f)

        // Flash
        assertEquals(0.0f, EffectsEvaluator.calculateFlashFactor(0f, 0.5f), 0.001f)
        assertEquals(1.0f, EffectsEvaluator.calculateFlashFactor(1.0f, 0.0f), 0.001f)
        assertEquals(0.5f, EffectsEvaluator.calculateFlashFactor(1.0f, 0.5f), 0.001f)
        assertEquals(0.0f, EffectsEvaluator.calculateFlashFactor(1.0f, 1.0f), 0.001f)
    }

    @Test
    fun testCpuColorGradingMath() {
        val baseColor = Triple(0.5f, 0.5f, 0.5f)

        // Brightness test
        val brighter = EffectsEvaluator.applyColorGradingCpu(
            baseColor.first, baseColor.second, baseColor.third,
            ColorGradingParams(brightness = 0.2f)
        )
        assertEquals(0.7f, brighter.first, 0.01f)
        assertEquals(0.7f, brighter.second, 0.01f)
        assertEquals(0.7f, brighter.third, 0.01f)

        // Contrast test
        val contrasted = EffectsEvaluator.applyColorGradingCpu(
            0.6f, 0.6f, 0.6f,
            ColorGradingParams(contrast = 1.5f)
        )
        // (0.6 - 0.5)*1.5 + 0.5 = 0.15 + 0.5 = 0.65
        assertEquals(0.65f, contrasted.first, 0.01f)

        // Temperature warm
        val warm = EffectsEvaluator.applyColorGradingCpu(
            0.5f, 0.5f, 0.5f,
            ColorGradingParams(temperature = 1.0f)
        )
        assertTrue(warm.first > 0.5f) // red boosted
        assertTrue(warm.third < 0.5f) // blue decreased
    }

    @Test
    fun testUniformEvaluationAggregation() {
        val effects = listOf(
            Effect.ParametricEffect(effectType = EffectType.RGB_SPLIT, intensity = 0.8f),
            Effect.ParametricEffect(effectType = EffectType.GLITCH, intensity = 0.6f),
            Effect.ParametricEffect(effectType = EffectType.FLASH, intensity = 0.5f),
            Effect.ParametricEffect(effectType = EffectType.ZOOM, intensity = 0.4f),
            Effect.ParametricEffect(effectType = EffectType.SHAKE, intensity = 0.7f)
        )

        val uniforms = EffectsEvaluator.evaluateUniforms(
            colorParams = ColorGradingParams(brightness = 0.1f, contrast = 1.2f, saturation = 1.1f),
            effects = effects,
            opacity = 0.9f,
            transitionType = TransitionType.CROSSFADE,
            transitionProgress = 0.75f,
            timeUs = 1_500_000L
        )

        assertEquals(0.1f, uniforms.brightness, 0.001f)
        assertEquals(1.2f, uniforms.contrast, 0.001f)
        assertEquals(1.1f, uniforms.saturation, 0.001f)
        assertEquals(0.9f, uniforms.opacity, 0.001f)
        assertEquals(0.8f, uniforms.rgbSplit, 0.001f)
        assertEquals(0.6f, uniforms.glitch, 0.001f)
        assertEquals(0.5f, uniforms.flash, 0.001f)
        assertTrue(uniforms.zoomScale > 1.0f)
        assertEquals(1, uniforms.transitionTypeCode)
        assertEquals(0.75f, uniforms.transitionProgress, 0.001f)
        assertEquals(1.5f, uniforms.timeSec, 0.001f)
    }

    @Test
    fun testShaderStringsCompleteness() {
        assertNotNull(DokShaders.VERTEX_SHADER)
        assertTrue(DokShaders.VERTEX_SHADER.contains("aPosition"))
        assertTrue(DokShaders.VERTEX_SHADER.contains("aTexCoord"))
        assertTrue(DokShaders.VERTEX_SHADER.contains("uMvpMatrix"))
        assertTrue(DokShaders.VERTEX_SHADER.contains("uTexMatrix"))

        assertNotNull(DokShaders.FRAGMENT_SHADER_2D)
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("sTexture"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uBrightness"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uContrast"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uSaturation"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uTemperature"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uVignette"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uRgbSplit"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uGlitch"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uFlash"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uShakeOffset"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uZoomScale"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uTransitionType"))
        assertTrue(DokShaders.FRAGMENT_SHADER_2D.contains("uTransitionProgress"))

        assertNotNull(DokShaders.FRAGMENT_SHADER_OES)
        assertTrue(DokShaders.FRAGMENT_SHADER_OES.contains("samplerExternalOES"))
        assertTrue(DokShaders.FRAGMENT_SHADER_OES.contains("GL_OES_EGL_image_external"))
    }
}
