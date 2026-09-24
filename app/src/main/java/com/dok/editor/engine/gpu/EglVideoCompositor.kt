package com.dok.editor.engine.gpu

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.net.Uri
import android.view.Surface
import com.dok.editor.engine.video.SequentialVideoDecoder
import com.dok.editor.engine.plan.RenderFrameInstruction
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Encoder-surface compositor. Video decoders render into SurfaceTextures and
 * this EGL context composites every active layer into the MediaCodec input
 * surface before the encoder consumes the frame.
 */
class EglVideoCompositor(
    private val context: Context,
    private val outputSurface: Surface,
    private val width: Int,
    private val height: Int
) : Closeable {

    private data class Source(
        val textureId: Int,
        val texture: SurfaceTexture,
        val surface: Surface,
        val decoder: SequentialVideoDecoder
    )

    private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private lateinit var eglContext: android.opengl.EGLContext
    private lateinit var eglSurface: android.opengl.EGLSurface
    private lateinit var program: Int
    private var sources = LinkedHashMap<String, Source>()
    private var initialized = false

    private val position = floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f)
    private val tex = floatArrayOf(0f,1f, 1f,1f, 0f,0f, 1f,0f)
    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(position.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(position).position(0) }
    private val texBuffer: FloatBuffer = ByteBuffer.allocateDirect(tex.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(tex).position(0) }

    private val mvp = FloatArray(16)
    private val texMatrix = FloatArray(16)

    init { initEgl() }

    private fun initEgl() {
        check(display != EGL14.EGL_NO_DISPLAY) { "EGL display unavailable" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL initialize failed" }
        val attrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, 4,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "No EGL window configuration"
        }
        val config = configs[0]!!
        val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttrs, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "EGL context creation failed" }
        eglSurface = EGL14.eglCreateWindowSurface(display, config, outputSurface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "EGL encoder surface creation failed" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) { "EGL make current failed" }
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        initialized = true
    }

    fun sourceFor(clipId: String, uri: Uri): Any {
        return sources.getOrPut(clipId) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val st = SurfaceTexture(ids[0])
            st.setDefaultBufferSize(width, height)
            val surface = Surface(st)
            Source(ids[0], st, surface, SequentialVideoDecoder(context, uri, surface))
        }
    }

    fun renderLayer(sourceKey: String, uri: Uri, instruction: RenderFrameInstruction) {
        val source = sourceFor(sourceKey, uri) as Source
        if (!source.decoder.decodeFrameToPts(instruction.mediaSourceTimeUs)) return
        source.texture.updateTexImage()
        source.texture.getTransformMatrix(texMatrix)

        GLES20.glUseProgram(program)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        Matrix.setIdentityM(mvp, 0)
        Matrix.translateM(mvp, 0, instruction.transform.positionX, instruction.transform.positionY, 0f)
        Matrix.rotateM(mvp, 0, instruction.transform.rotationDegrees, 0f, 0f, 1f)
        Matrix.scaleM(mvp, 0, instruction.transform.scaleX, instruction.transform.scaleY, 1f)

        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, texMatrix, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uAlpha"), instruction.opacity.coerceIn(0f, 1f))
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBrightness"), instruction.colorParams.brightness)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uContrast"), instruction.colorParams.contrast)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSaturation"), instruction.colorParams.saturation)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uVignette"), instruction.colorParams.vignette)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, source.textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        val p = GLES20.glGetAttribLocation(program, "aPosition")
        val t = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glEnableVertexAttribArray(p)
        GLES20.glVertexAttribPointer(p, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(t)
        GLES20.glVertexAttribPointer(t, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(p)
        GLES20.glDisableVertexAttribArray(t)
    }

    fun beginFrame() {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    fun endFrame(presentationTimeUs: Long) {
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, presentationTimeUs * 1000L)
        check(EGL14.eglSwapBuffers(display, eglSurface)) { "EGL swap failed" }
    }

    override fun close() {
        if (!initialized) return
        sources.values.forEach {
            runCatching { it.decoder.close() }
            runCatching { it.surface.release() }
            runCatching { it.texture.release() }
            runCatching { GLES20.glDeleteTextures(1, intArrayOf(it.textureId), 0) }
        }
        sources.clear()
        runCatching { GLES20.glDeleteProgram(program) }
        runCatching { EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) }
        runCatching { EGL14.eglDestroySurface(display, eglSurface) }
        runCatching { EGL14.eglDestroyContext(display, eglContext) }
        runCatching { EGL14.eglReleaseThread() }
        runCatching { EGL14.eglTerminate(display) }
        initialized = false
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        fun shader(type: Int, source: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, source)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { GLES20.glGetShaderInfoLog(s) }
            return s
        }
        val vs = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { GLES20.glGetProgramInfoLog(p) }
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        return p
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uMvp;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 0.0, 1.0);
                vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """
        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            uniform float uAlpha;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uVignette;
            varying vec2 vTexCoord;
            void main() {
                vec4 c = texture2D(uTexture, vTexCoord);
                c.rgb += vec3(uBrightness);
                c.rgb = (c.rgb - 0.5) * uContrast + 0.5;
                float l = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
                c.rgb = mix(vec3(l), c.rgb, uSaturation);
                float d = distance(vTexCoord, vec2(0.5));
                c.rgb *= 1.0 - clamp(uVignette, 0.0, 1.0) * smoothstep(0.25, 0.75, d);
                c.rgb = clamp(c.rgb, 0.0, 1.0);
                gl_FragColor = vec4(c.rgb, c.a * uAlpha);
            }
        """
    }
}
