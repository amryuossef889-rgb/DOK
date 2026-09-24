package com.dok.editor.engine.effects

import android.opengl.GLES20
import android.util.Log
import com.dok.editor.model.ColorGradingParams
import com.dok.editor.model.Effect
import com.dok.editor.model.EffectType
import com.dok.editor.model.TransitionConfig
import com.dok.editor.model.TransitionType
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object DokShaders {

    private const val TAG = "DokShaders"

    val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uMvpMatrix;
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        
        void main() {
            gl_Position = uMvpMatrix * aPosition;
            vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
        }
    """.trimIndent()

    val FRAGMENT_SHADER_2D = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D sTexture;
        
        // Color grading
        uniform float uBrightness;
        uniform float uContrast;
        uniform float uSaturation;
        uniform float uTemperature;
        uniform float uVignette;
        uniform float uOpacity;
        
        // Parametric effects
        uniform float uRgbSplit;
        uniform float uGlitch;
        uniform float uFlash;
        uniform vec2 uShakeOffset;
        uniform float uZoomScale;
        uniform float uTime;
        
        // Transitions
        uniform int uTransitionType;
        uniform float uTransitionProgress;
        
        vec3 adjustColorGrading(vec3 rgb, vec2 uv) {
            // Brightness
            rgb += vec3(uBrightness);
            
            // Contrast
            rgb = (rgb - vec3(0.5)) * uContrast + vec3(0.5);
            
            // Saturation
            float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
            rgb = mix(vec3(luma), rgb, uSaturation);
            
            // Temperature: warm adds red/green, cool adds blue
            if (uTemperature > 0.0) {
                rgb.r += uTemperature * 0.15;
                rgb.g += uTemperature * 0.05;
                rgb.b -= uTemperature * 0.10;
            } else if (uTemperature < 0.0) {
                float cool = -uTemperature;
                rgb.r -= cool * 0.10;
                rgb.b += cool * 0.20;
            }
            
            // Vignette: radial falloff
            if (uVignette > 0.0) {
                vec2 center = uv - vec2(0.5);
                float dist = length(center) * 1.414; // normalized to ~1.0 at corners
                float vig = smoothstep(0.8 - uVignette * 0.5, 1.2, dist);
                rgb = mix(rgb, rgb * (1.0 - uVignette * 0.8), vig);
            }
            
            return clamp(rgb, 0.0, 1.0);
        }
        
        void main() {
            vec2 uv = vTexCoord;
            
            // Zoom effect
            if (uZoomScale > 1.0) {
                uv = (uv - vec2(0.5)) / uZoomScale + vec2(0.5);
            }
            
            // Shake effect
            uv += uShakeOffset;
            
            // Glitch effect: horizontal scanline displacement
            if (uGlitch > 0.0) {
                float scanline = sin(uv.y * 100.0 + uTime * 20.0);
                if (scanline > 0.7) {
                    uv.x += sin(uv.y * 20.0 + uTime * 30.0) * uGlitch * 0.05;
                }
            }
            
            // Transitions handling: Wipe
            if (uTransitionType == 4) { // WIPE_LEFT
                if (uv.x > uTransitionProgress) {
                    discard;
                }
            } else if (uTransitionType == 5) { // WIPE_RIGHT
                if ((1.0 - uv.x) > uTransitionProgress) {
                    discard;
                }
            }
            
            // Sample with RGB split chromatic aberration
            vec4 baseColor;
            if (uRgbSplit > 0.001) {
                vec2 rOffset = vec2(uRgbSplit * 0.015, 0.0);
                vec2 bOffset = vec2(-uRgbSplit * 0.015, 0.0);
                float r = texture2D(sTexture, uv + rOffset).r;
                float g = texture2D(sTexture, uv).g;
                float b = texture2D(sTexture, uv + bOffset).b;
                float a = texture2D(sTexture, uv).a;
                baseColor = vec4(r, g, b, a);
            } else {
                baseColor = texture2D(sTexture, uv);
            }
            
            vec3 processedRgb = adjustColorGrading(baseColor.rgb, uv);
            
            // Flash effect: additive white burst
            if (uFlash > 0.0) {
                processedRgb += vec3(uFlash);
                processedRgb = clamp(processedRgb, 0.0, 1.0);
            }
            
            float finalAlpha = baseColor.a * uOpacity;
            
            // Transitions handling: Dip to black / white
            if (uTransitionType == 2) { // DIP_TO_BLACK
                float dipFactor = uTransitionProgress < 0.5 ? (1.0 - uTransitionProgress * 2.0) : ((uTransitionProgress - 0.5) * 2.0);
                processedRgb *= dipFactor;
            } else if (uTransitionType == 3) { // DIP_TO_WHITE
                float dipFactor = uTransitionProgress < 0.5 ? (1.0 - uTransitionProgress * 2.0) : ((uTransitionProgress - 0.5) * 2.0);
                processedRgb = mix(vec3(1.0), processedRgb, dipFactor);
            } else if (uTransitionType == 1) { // CROSSFADE
                finalAlpha *= uTransitionProgress;
            }
            
            gl_FragColor = vec4(processedRgb, finalAlpha);
        }
    """.trimIndent()

    val FRAGMENT_SHADER_OES = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES sTexture;
        
        // Color grading
        uniform float uBrightness;
        uniform float uContrast;
        uniform float uSaturation;
        uniform float uTemperature;
        uniform float uVignette;
        uniform float uOpacity;
        
        // Parametric effects
        uniform float uRgbSplit;
        uniform float uGlitch;
        uniform float uFlash;
        uniform vec2 uShakeOffset;
        uniform float uZoomScale;
        uniform float uTime;
        
        // Transitions
        uniform int uTransitionType;
        uniform float uTransitionProgress;
        
        vec3 adjustColorGrading(vec3 rgb, vec2 uv) {
            rgb += vec3(uBrightness);
            rgb = (rgb - vec3(0.5)) * uContrast + vec3(0.5);
            float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
            rgb = mix(vec3(luma), rgb, uSaturation);
            
            if (uTemperature > 0.0) {
                rgb.r += uTemperature * 0.15;
                rgb.g += uTemperature * 0.05;
                rgb.b -= uTemperature * 0.10;
            } else if (uTemperature < 0.0) {
                float cool = -uTemperature;
                rgb.r -= cool * 0.10;
                rgb.b += cool * 0.20;
            }
            
            if (uVignette > 0.0) {
                vec2 center = uv - vec2(0.5);
                float dist = length(center) * 1.414;
                float vig = smoothstep(0.8 - uVignette * 0.5, 1.2, dist);
                rgb = mix(rgb, rgb * (1.0 - uVignette * 0.8), vig);
            }
            
            return clamp(rgb, 0.0, 1.0);
        }
        
        void main() {
            vec2 uv = vTexCoord;
            
            if (uZoomScale > 1.0) {
                uv = (uv - vec2(0.5)) / uZoomScale + vec2(0.5);
            }
            
            uv += uShakeOffset;
            
            if (uGlitch > 0.0) {
                float scanline = sin(uv.y * 100.0 + uTime * 20.0);
                if (scanline > 0.7) {
                    uv.x += sin(uv.y * 20.0 + uTime * 30.0) * uGlitch * 0.05;
                }
            }
            
            if (uTransitionType == 4) { // WIPE_LEFT
                if (uv.x > uTransitionProgress) {
                    discard;
                }
            } else if (uTransitionType == 5) { // WIPE_RIGHT
                if ((1.0 - uv.x) > uTransitionProgress) {
                    discard;
                }
            }
            
            vec4 baseColor;
            if (uRgbSplit > 0.001) {
                vec2 rOffset = vec2(uRgbSplit * 0.015, 0.0);
                vec2 bOffset = vec2(-uRgbSplit * 0.015, 0.0);
                float r = texture2D(sTexture, uv + rOffset).r;
                float g = texture2D(sTexture, uv).g;
                float b = texture2D(sTexture, uv + bOffset).b;
                float a = texture2D(sTexture, uv).a;
                baseColor = vec4(r, g, b, a);
            } else {
                baseColor = texture2D(sTexture, uv);
            }
            
            vec3 processedRgb = adjustColorGrading(baseColor.rgb, uv);
            
            if (uFlash > 0.0) {
                processedRgb += vec3(uFlash);
                processedRgb = clamp(processedRgb, 0.0, 1.0);
            }
            
            float finalAlpha = baseColor.a * uOpacity;
            
            if (uTransitionType == 2) { // DIP_TO_BLACK
                float dipFactor = uTransitionProgress < 0.5 ? (1.0 - uTransitionProgress * 2.0) : ((uTransitionProgress - 0.5) * 2.0);
                processedRgb *= dipFactor;
            } else if (uTransitionType == 3) { // DIP_TO_WHITE
                float dipFactor = uTransitionProgress < 0.5 ? (1.0 - uTransitionProgress * 2.0) : ((uTransitionProgress - 0.5) * 2.0);
                processedRgb = mix(vec3(1.0), processedRgb, dipFactor);
            } else if (uTransitionType == 1) { // CROSSFADE
                finalAlpha *= uTransitionProgress;
            }
            
            gl_FragColor = vec4(processedRgb, finalAlpha);
        }
    """.trimIndent()

    fun compileShader(shaderType: Int, source: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        if (shader == 0) {
            Log.e(TAG, "Error creating shader of type $shaderType")
            return 0
        }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compilation error: $log")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Error creating OpenGL program")
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Program link error: $log")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }
}

data class EvaluatedEffectsUniforms(
    val brightness: Float = 0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val temperature: Float = 0f,
    val vignette: Float = 0f,
    val opacity: Float = 1.0f,
    val rgbSplit: Float = 0f,
    val glitch: Float = 0f,
    val flash: Float = 0f,
    val shakeOffset: Pair<Float, Float> = 0f to 0f,
    val zoomScale: Float = 1.0f,
    val transitionTypeCode: Int = 0,
    val transitionProgress: Float = 1.0f,
    val timeSec: Float = 0f
)

object EffectsEvaluator {

    /**
     * Maps TransitionType to shader integer code.
     */
    fun transitionTypeCode(type: TransitionType?): Int {
        return when (type) {
            null -> 0
            TransitionType.CROSSFADE -> 1
            TransitionType.DIP_TO_BLACK -> 2
            TransitionType.DIP_TO_WHITE -> 3
            TransitionType.WIPE_LEFT -> 4
            TransitionType.WIPE_RIGHT -> 5
        }
    }

    /**
     * Calculates shake offset for a given intensity and timestamp.
     */
    fun calculateShakeOffset(intensity: Float, timeSec: Float): Pair<Float, Float> {
        if (intensity <= 0f) return 0f to 0f
        val freq = 25.0f
        val amp = intensity * 0.04f
        val x = sin(timeSec * freq * 1.3f) * cos(timeSec * freq * 0.7f) * amp
        val y = cos(timeSec * freq * 1.1f) * sin(timeSec * freq * 0.9f) * amp
        return x to y
    }

    /**
     * Calculates zoom scale factor.
     */
    fun calculateZoomScale(intensity: Float, baseScale: Float = 1.0f): Float {
        if (intensity <= 0f) return baseScale
        return baseScale * (1.0f + intensity * 0.5f)
    }

    /**
     * Calculates flash brightness factor decaying over time window.
     */
    fun calculateFlashFactor(intensity: Float, progress: Float): Float {
        if (intensity <= 0f) return 0f
        val decay = (1.0f - progress).coerceIn(0f, 1f)
        return intensity * decay
    }

    /**
     * Evaluates dip to black/white multiplier factor (0.0 to 1.0).
     * At 0.0 -> 1.0 (start)
     * At 0.5 -> 0.0 (maximum dip)
     * At 1.0 -> 1.0 (end)
     */
    fun calculateDipFactor(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return if (p < 0.5f) {
            1.0f - p * 2.0f
        } else {
            (p - 0.5f) * 2.0f
        }
    }

    /**
     * Evaluates CPU color grading for a single RGB triplet.
     */
    fun applyColorGradingCpu(
        r: Float,
        g: Float,
        b: Float,
        params: ColorGradingParams
    ): Triple<Float, Float, Float> {
        var cr = r + params.brightness
        var cg = g + params.brightness
        var cb = b + params.brightness

        // Contrast
        cr = (cr - 0.5f) * params.contrast + 0.5f
        cg = (cg - 0.5f) * params.contrast + 0.5f
        cb = (cb - 0.5f) * params.contrast + 0.5f

        // Saturation
        val luma = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
        cr = luma + (cr - luma) * params.saturation
        cg = luma + (cg - luma) * params.saturation
        cb = luma + (cb - luma) * params.saturation

        // Temperature
        if (params.temperature > 0f) {
            cr += params.temperature * 0.15f
            cg += params.temperature * 0.05f
            cb -= params.temperature * 0.10f
        } else if (params.temperature < 0f) {
            val cool = -params.temperature
            cr -= cool * 0.10f
            cb += cool * 0.20f
        }

        return Triple(
            cr.coerceIn(0f, 1f),
            cg.coerceIn(0f, 1f),
            cb.coerceIn(0f, 1f)
        )
    }

    /**
     * Evaluates complete uniform parameters for a frame rendering instruction.
     */
    fun evaluateUniforms(
        colorParams: ColorGradingParams,
        effects: List<Effect.ParametricEffect>,
        opacity: Float,
        transitionType: TransitionType?,
        transitionProgress: Float?,
        timeUs: Long
    ): EvaluatedEffectsUniforms {
        val timeSec = timeUs / 1_000_000f

        var rgbSplit = 0f
        var glitch = 0f
        var flash = 0f
        var zoomScale = 1.0f
        var shakeIntensity = 0f

        for (effect in effects) {
            when (effect.effectType) {
                EffectType.RGB_SPLIT -> rgbSplit = max(rgbSplit, effect.intensity)
                EffectType.GLITCH -> glitch = max(glitch, effect.intensity)
                EffectType.FLASH -> flash = max(flash, effect.intensity)
                EffectType.ZOOM -> zoomScale = calculateZoomScale(effect.intensity, zoomScale)
                EffectType.SHAKE -> shakeIntensity = max(shakeIntensity, effect.intensity)
                EffectType.VIGNETTE -> {} // Handled via colorParams.vignette or override
                else -> {}
            }
        }

        val shakeOffset = calculateShakeOffset(shakeIntensity, timeSec)

        return EvaluatedEffectsUniforms(
            brightness = colorParams.brightness,
            contrast = colorParams.contrast,
            saturation = colorParams.saturation,
            temperature = colorParams.temperature,
            vignette = colorParams.vignette,
            opacity = opacity,
            rgbSplit = rgbSplit,
            glitch = glitch,
            flash = flash,
            shakeOffset = shakeOffset,
            zoomScale = zoomScale,
            transitionTypeCode = transitionTypeCode(transitionType),
            transitionProgress = transitionProgress ?: 1.0f,
            timeSec = timeSec
        )
    }
}
