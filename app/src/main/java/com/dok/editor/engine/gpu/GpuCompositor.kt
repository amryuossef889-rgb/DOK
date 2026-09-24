package com.dok.editor.engine.gpu
import android.opengl.GLES20
import android.opengl.Matrix
import com.dok.editor.model.BlendMode
data class GpuLayer(val textureId:Int,val alpha:Float=1f,val cropLeft:Float=0f,val cropTop:Float=0f,val cropRight:Float=1f,val cropBottom:Float=1f,val x:Float=0f,val y:Float=0f,val scaleX:Float=1f,val scaleY:Float=1f,val rotationDegrees:Float=0f,val blendMode:BlendMode=BlendMode.NORMAL)
class GpuCompositor {
 private val vertex="""attribute vec2 aPosition; attribute vec2 aTexCoord; uniform mat4 uMvp; varying vec2 vTexCoord; void main(){gl_Position=uMvp*vec4(aPosition,0.0,1.0);vTexCoord=aTexCoord;}"""
 private val frag="""precision mediump float; uniform sampler2D uTexture; uniform float uAlpha; uniform vec4 uCrop; varying vec2 vTexCoord; void main(){vec2 uv=mix(uCrop.xy,uCrop.zw,vTexCoord);vec4 c=texture2D(uTexture,uv);gl_FragColor=vec4(c.rgb,c.a*uAlpha);}"""
 private val program by lazy{createProgram(vertex,frag)}
 private val positionLocation by lazy { GLES20.glGetAttribLocation(program,"aPosition") }
 private val texCoordLocation by lazy { GLES20.glGetAttribLocation(program,"aTexCoord") }
 private val mvpLocation by lazy { GLES20.glGetUniformLocation(program,"uMvp") }
 private val alphaLocation by lazy { GLES20.glGetUniformLocation(program,"uAlpha") }
 private val cropLocation by lazy { GLES20.glGetUniformLocation(program,"uCrop") }
 private val textureLocation by lazy { GLES20.glGetUniformLocation(program,"uTexture") }
 private val vertexBuffer by lazy { java.nio.ByteBuffer.allocateDirect(vertices.size*4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices).position(0) } }
 private val texBuffer by lazy { java.nio.ByteBuffer.allocateDirect(tex.size*4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply { put(tex).position(0) } }
 private val vertices=floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f); private val tex=floatArrayOf(0f,1f,1f,1f,0f,0f,1f,0f)
 private val mvp=FloatArray(16)
 fun begin(width:Int,height:Int){GLES20.glViewport(0,0,width,height);GLES20.glClearColor(0f,0f,0f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glEnable(GLES20.GL_BLEND)}
 fun drawLayer(l:GpuLayer){
  GLES20.glUseProgram(program); val p=positionLocation; val t=texCoordLocation
  val m=mvpLocation; val a=alphaLocation; val c=cropLocation; val s=textureLocation
  Matrix.setIdentityM(mvp,0);Matrix.translateM(mvp,0,l.x,l.y,0f);Matrix.rotateM(mvp,0,l.rotationDegrees,0f,0f,1f);Matrix.scaleM(mvp,0,l.scaleX,l.scaleY,1f)
  GLES20.glUniformMatrix4fv(m,1,false,mvp,0);GLES20.glUniform1f(a,l.alpha.coerceIn(0f,1f));GLES20.glUniform4f(c,l.cropLeft,l.cropTop,l.cropRight,l.cropBottom)
  GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,l.textureId);GLES20.glUniform1i(s,0)
  GLES20.glEnableVertexAttribArray(p);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,vertexBuffer);GLES20.glEnableVertexAttribArray(t);GLES20.glVertexAttribPointer(t,2,GLES20.GL_FLOAT,false,0,texBuffer)
  when(l.blendMode){BlendMode.ADD->GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);BlendMode.SCREEN->GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_COLOR);BlendMode.MULTIPLY->GLES20.glBlendFunc(GLES20.GL_DST_COLOR,GLES20.GL_ONE_MINUS_SRC_ALPHA);else->GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA)}
  GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(p);GLES20.glDisableVertexAttribArray(t)
 }
 fun end(){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);GLES20.glUseProgram(0)}
 private fun createProgram(v:String,f:String):Int{fun c(type:Int,src:String):Int{val s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);val ok=IntArray(1);GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){GLES20.glGetShaderInfoLog(s)};return s};val p=GLES20.glCreateProgram();val vs=c(GLES20.GL_VERTEX_SHADER,v);val fs=c(GLES20.GL_FRAGMENT_SHADER,f);GLES20.glAttachShader(p,vs);GLES20.glAttachShader(p,fs);GLES20.glLinkProgram(p);val ok=IntArray(1);GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);check(ok[0]!=0){GLES20.glGetProgramInfoLog(p)};return p}
}
