package com.dok.editor.engine.transitions
import com.dok.editor.model.TransitionType
data class TransitionSample(val sourceAAlpha:Float,val sourceBAlpha:Float,val wipeProgress:Float,val dipAlpha:Float)
object TransitionEngine{
 fun sample(type:TransitionType,progress:Float):TransitionSample{val p=progress.coerceIn(0f,1f);return when(type){TransitionType.CROSSFADE->TransitionSample(1f-p,p,0f,0f);TransitionType.DIP_TO_BLACK,TransitionType.DIP_TO_WHITE->TransitionSample(1f-p,p,0f,1f-(2f*p-1f).coerceAtLeast(0f));TransitionType.WIPE_LEFT,TransitionType.WIPE_RIGHT->TransitionSample(1f-p,p,p,0f)}}
}
