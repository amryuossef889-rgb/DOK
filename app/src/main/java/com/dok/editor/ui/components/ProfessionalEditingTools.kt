package com.dok.editor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dok.editor.model.Keyframe
import kotlin.math.max
import kotlin.math.min

@Composable
fun KeyframeGraphEditor(
    keyframes:List<Keyframe>,
    durationUs:Long,
    valueMin:Float,
    valueMax:Float,
    onMove:(Keyframe,Long,Float)->Unit
){
    val points=remember(keyframes){keyframes.sortedBy{it.timestampOffsetUs}}
    Column(Modifier.fillMaxWidth().height(220.dp)){
        Text("Graph Editor",style=MaterialTheme.typography.labelLarge)
        Canvas(Modifier.fillMaxSize().pointerInput(points,durationUs,valueMin,valueMax){
            detectDragGestures{change,drag->
                if(points.isEmpty())return@detectDragGestures
                val nearest=points.minByOrNull{(it.timestampOffsetUs.toFloat()/max(1L,durationUs)*size.width-change.position.x)*(it.timestampOffsetUs.toFloat()/max(1L,durationUs)*size.width-change.position.x)}?:return@detectDragGestures
                val t=(change.position.x/size.width).coerceIn(0f,1f)
                val v=(1f-change.position.y/size.height).coerceIn(0f,1f)*(valueMax-valueMin)+valueMin
                onMove(nearest,(t*durationUs).toLong(),v);change.consume()
            }
        }){
            val w=size.width;val h=size.height
            for(i in 0..4){val y=h*i/4f;drawLine(MaterialTheme.colorScheme.outline.copy(alpha=.25f),Offset(0f,y),Offset(w,y))}
            points.forEachIndexed{index,k->
                val x=w*(k.timestampOffsetUs.toFloat()/max(1L,durationUs));val y=h*(1f-((k.value-valueMin)/(valueMax-valueMin).coerceAtLeast(.0001f)).coerceIn(0f,1f))
                drawCircle(MaterialTheme.colorScheme.primary,5f,Offset(x,y))
                if(index>0){val p=points[index-1];val px=w*(p.timestampOffsetUs.toFloat()/max(1L,durationUs));val py=h*(1f-((p.value-valueMin)/(valueMax-valueMin).coerceAtLeast(.0001f)).coerceIn(0f,1f));drawLine(MaterialTheme.colorScheme.primary,Offset(px,py),Offset(x,y),2f)}
            }
        }
    }
}

@Composable
fun ProfessionalTrimToolbar(onMode:(String)->Unit){
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        listOf("Ripple","Roll","Slip","Slide","Insert","Overwrite","Replace").forEach{label->Button(onClick={onMode(label)}){Text(label)}}
    }
}
