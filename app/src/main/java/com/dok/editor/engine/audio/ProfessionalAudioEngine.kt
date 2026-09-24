package com.dok.editor.engine.audio
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
data class EqBand(val frequencyHz:Float,val gainDb:Float,val q:Float=1f)
data class CompressorSettings(val thresholdDb:Float=-18f,val ratio:Float=4f,val attackMs:Float=10f,val releaseMs:Float=100f)
data class LimiterSettings(val ceilingDb:Float=-1f)
data class AudioAutomationPoint(val timeUs:Long,val value:Float)
class ProfessionalAudioEngine{
 fun applyEq(x:FloatArray,sampleRate:Int,bands:List<EqBand>):FloatArray{val o=x.copyOf();for(b in bands){val w=2.0*Math.PI*b.frequencyHz/sampleRate;val alpha=Math.sin(w)/(2*b.q);val A=10.0.pow(b.gainDb/40);val b0=1+alpha/A;val b1=-2*Math.cos(w);val b2=1-alpha/A;val a0=1+alpha*A;val a1=-2*Math.cos(w);val a2=1-alpha*A;var x1=0.0;var x2=0.0;var y1=0.0;var y2=0.0;for(i in o.indices){val y=b0/a0*o[i]+b1/a0*x1+b2/a0*x2-a1/a0*y1-a2/a0*y2;o[i]=y.toFloat();x2=x1;x1=x[i].toDouble();y2=y1;y1=y}};return o}
 fun compress(x:FloatArray,s:CompressorSettings):FloatArray{val o=x.copyOf();val atk=exp(-1.0/(s.attackMs.coerceAtLeast(.1f)*48));val rel=exp(-1.0/(s.releaseMs.coerceAtLeast(1f)*48));var env=0.0;val th=10.0.pow(s.thresholdDb/20);for(i in o.indices){val l=abs(o[i].toDouble());env=if(l>env)atk*env+(1-atk)*l else rel*env+(1-rel)*l;if(env>th){val over=env/th;o[i]=(o[i]/over.pow(1-1/s.ratio)).toFloat()}};return o}
 fun limit(x:FloatArray,s:LimiterSettings):FloatArray{val c=10.0.pow(s.ceilingDb/20).toFloat();return x.copyOf().also{a->for(i in a.indices)a[i]=a[i].coerceIn(-c,c)}}
 fun automate(base:Float,timeUs:Long,p:List<AudioAutomationPoint>):Float{if(p.isEmpty())return base;val q=p.sortedBy{it.timeUs};if(timeUs<=q.first().timeUs)return q.first().value;if(timeUs>=q.last().timeUs)return q.last().value;val r=q.first{it.timeUs>=timeUs};val l=q.last{it.timeUs<=timeUs};val t=(timeUs-l.timeUs).toFloat()/(r.timeUs-l.timeUs);return l.value+(r.value-l.value)*t}
}
