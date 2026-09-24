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

@Composable
fun KeyframeGraphEditor(
    keyframes: List<Keyframe>,
    durationUs: Long,
    valueMin: Float,
    valueMax: Float,
    onMove: (Keyframe, Long, Float) -> Unit
) {
    val points = remember(keyframes) { keyframes.sortedBy { it.timestampOffsetUs } }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().height(220.dp)) {
        Text("Graph Editor", style = MaterialTheme.typography.labelLarge)
        Canvas(
            Modifier.fillMaxSize().pointerInput(points, durationUs, valueMin, valueMax) {
                detectDragGestures { change, _ ->
                    if (points.isEmpty()) return@detectDragGestures
                    val nearest = points.minByOrNull { point ->
                        val px = point.timestampOffsetUs.toFloat() / max(1L, durationUs).toFloat() * size.width
                        val dx = px - change.position.x
                        dx * dx
                    } ?: return@detectDragGestures
                    val t = (change.position.x / size.width).coerceIn(0f, 1f)
                    val range = (valueMax - valueMin).coerceAtLeast(.0001f)
                    val v = (1f - change.position.y / size.height).coerceIn(0f, 1f) * range + valueMin
                    onMove(nearest, (t * durationUs).toLong(), v)
                    change.consume()
                }
            }
        ) {
            val w = size.width
            val h = size.height
            for (i in 0..4) {
                val y = h * i / 4f
                drawLine(colors.outline.copy(alpha = .25f), Offset(0f, y), Offset(w, y))
            }
            points.forEachIndexed { index, keyframe ->
                val x = w * keyframe.timestampOffsetUs.toFloat() / max(1L, durationUs).toFloat()
                val y = h * (1f - ((keyframe.value - valueMin) / (valueMax - valueMin).coerceAtLeast(.0001f)).coerceIn(0f, 1f))
                drawCircle(colors.primary, 5f, Offset(x, y))
                if (index > 0) {
                    val previous = points[index - 1]
                    val px = w * previous.timestampOffsetUs.toFloat() / max(1L, durationUs).toFloat()
                    val py = h * (1f - ((previous.value - valueMin) / (valueMax - valueMin).coerceAtLeast(.0001f)).coerceIn(0f, 1f))
                    drawLine(colors.primary, Offset(px, py), Offset(x, y), 2f)
                }
            }
        }
    }
}

@Composable
fun ProfessionalTrimToolbar(onMode: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Ripple", "Roll", "Slip", "Slide", "Insert", "Overwrite", "Replace").forEach { label ->
            Button(onClick = { onMode(label) }) { Text(label) }
        }
    }
}
