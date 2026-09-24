package com.dok.editor.engine.gpu

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.Surface
import com.dok.editor.engine.video.SequentialVideoDecoder
import com.dok.editor.engine.plan.RenderFrameInstruction
import java.io.Closeable

class EglVideoCompositor(private val context:Context, private val encoderSurface:Surface, private val width:Int, private val height:Int):Closeable{
 private data class Source(val textureId:Int,val texture:SurfaceTexture,val surface:Surface,val decoder:SequentialVideoDecoder)
 private val sources=HashMap<String,Source>()
 private val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
 private val config:android.opengl.EGLConfig
 private val eglContext:android.opengl.EGLContext
 private val eglSurface:android.opengl.EGLSurface
 private val program:Int
 private val posLoc:Int
 private val texLoc:Int
 private val matrixLoc:Int
 private val opacityLoc:Int
 private val brightnessLoc:Int
 private val contrastLoc:Int
 private val saturationLoc:Int
 private val vignetteLoc:Int
 private val vertex=floatArrayOf(-1f,-1f,0f,1f,-1f,0f,-1f,1f,0f,1f,1f,0f)
 private val uv=floatArrayOf(0f,1f,1f,1f,0f,0f,1f,0f)
 init{
  val v=IntArray(2);check(EGL14.eglInitialize(display,v,0,v,1))
  val a=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_NONE)
  val cs=arrayOfNulls<android.opengl.EGLConfig>(1);val n=IntArray(1);check(EGL14.eglChooseConfig(display,a,0,cs,0,1,n,0)&&n[0]>0);config=cs[0]!!
  val ca=intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE);eglContext=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,ca,0);check(eglContext!=EGL14.EGL_NO_CONTEXT)
  eglSurface=EGL14.eglCreateWindowSurface(display,config,encoderSurface,intArrayOf(EGL14.EGL_NONE),0);check(eglSurface!=EGL14.EGL_NO_SURFACE);check(EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext))
  program=buildProgram("attribute vec4 aPosition;attribute vec2 aTexCoord;uniform mat4 uMatrix;varying vec2 vTexCoord;void main(){gl_Position=uMatrix*aPosition;vTexCoord=aTexCoord;}",
   "#extension GL_OES_EGL_image_external : require\nprecision mediump float;varying vec2 vTexCoord;uniform samplerExternalOES uTexture;uniform float uOpacity;uniform float uBrightness;uniform float uContrast;uniform float uSaturation;uniform float uVignette;void main(){vec4 c=texture2D(uTexture,vTexCoord);c.rgb+=uBrightness;c.rgb=(c.rgb-.5)*uContrast+.5;float l=dot(c.rgb,vec3(.299,.587,.114));c.rgb=mix(vec3(l),c.rgb,uSaturation);vec2 p=vTexCoord-.5;float q=1.0-uVignette*dot(p,p)*2.0;c.rgb*=max(0.0,q);gl_FragColor=vec4(c.rgb,c.a*uOpacity);}")
  posLoc=GLES20.glGetAttribLocation(program,"aPosition");texLoc=GLES20.glGetAttribLocation(program,"aTexCoord");matrixLoc=GLES20.glGetUniformLocation(program,"uMatrix");opacityLoc=GLES20.glGetUniformLocation(program,"uOpacity");brightnessLoc=GLES20.glGetUniformLocation(program,"uBrightness");contrastLoc=GLES20.glGetUniformLocation(program,"uContrast");saturationLoc=GLES20.glGetUniformLocation(program,"uSaturation");vignetteLoc=GLES20.glGetUniformLocation(program,"uVignette")
  GLES20.glViewport(0,0,width,height);GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glClearColor(0f,0f,0f,1f)
 }
 fun beginFrame(){GLES20.glViewport(0,0,width,height);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA)}
 fun renderLayer(sourceKey:String,uri:Uri,instruction:RenderFrameInstruction){
  val s=sources.getOrPut(sourceKey){val id=IntArray(1);GLES20.glGenTextures(1,id,0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,id[0]);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);val st=SurfaceTexture(id[0]);val sf=Surface(st);Source(id[0],st,sf,SequentialVideoDecoder(context,uri,sf))}
  s.decoder.decodeFrameToPts(instruction.mediaSourceTimeUs);s.texture.updateTexImage();GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,s.textureId);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0)
  val t=instruction.transform;val m=FloatArray(16);Matrix.setIdentityM(m,0);Matrix.translateM(m,0,t.positionX,t.positionY,0f);Matrix.rotateM(m,0,t.rotationDegrees,0f,0f,1f);Matrix.scaleM(m,0,t.scaleX,t.scaleY,1f);GLES20.glUniformMatrix4fv(matrixLoc,1,false,m,0);GLES20.glUniform1f(opacityLoc,instruction.opacity);GLES20.glUniform1f(brightnessLoc,instruction.colorParams.brightness);GLES20.glUniform1f(contrastLoc,instruction.colorParams.contrast);GLES20.glUniform1f(saturationLoc,instruction.colorParams.saturation);GLES20.glUniform1f(vignetteLoc,instruction.colorParams.vignette)
  val vb=java.nio.ByteBuffer.allocateDirect(vertex.size*4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().put(vertex).position(0);val ub=java.nio.ByteBuffer.allocateDirect(uv.size*4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().put(uv).position(0);GLES20.glEnableVertexAttribArray(posLoc);GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,0,vb);GLES20.glEnableVertexAttribArray(texLoc);GLES20.glVertexAttribPointer(texLoc,2,GLES20.GL_FLOAT,false,0,ub);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(posLoc);GLES20.glDisableVertexAttribArray(texLoc)
 }
 fun endFrame(presentationTimeUs:Long){EGLExt.eglPresentationTimeANDROID(display,eglSurface,presentationTimeUs*1000L);check(EGL14.eglSwapBuffers(display,eglSurface))}
 private fun buildProgram(v:String,f:String):Int{fun sh(type:Int,src:String):Int{val s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);val ok=IntArray(1);GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw IllegalStateException(GLES20.glGetShaderInfoLog(s));return s};val p=GLES20.glCreateProgram();GLES20.glAttachShader(p,sh(GLES20.GL_VERTEX_SHADER,v));GLES20.glAttachShader(p,sh(GLES20.GL_FRAGMENT_SHADER,f));GLES20.glLinkProgram(p);val ok=IntArray(1);GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw IllegalStateException(GLES20.glGetProgramInfoLog(p));return p}
 override fun close(){sources.values.forEach{runCatching{it.decoder.close()};runCatching{it.surface.release()};runCatching{it.texture.release()};GLES20.glDeleteTextures(1,intArrayOf(it.textureId),0)};sources.clear();runCatching{GLES20.glDeleteProgram(program)};runCatching{EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)};runCatching{EGL14.eglDestroySurface(display,eglSurface)};runCatching{EGL14.eglDestroyContext(display,eglContext)};runCatching{EGL14.eglReleaseThread();EGL14.eglTerminate(display)}}
}