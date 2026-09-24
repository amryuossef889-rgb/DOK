package com.dok.editor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun ColorScopes(
    luma: FloatArray = FloatArray(128) { i -> i / 127f },
    rgb: FloatArray = FloatArray(128) { i -> sin(i * .13f) * .5f + .5f }
) {
    Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScopeCanvas("Waveform", luma, Modifier.weight(1f))
        ScopeCanvas("RGB", rgb, Modifier.weight(1f))
        ScopeCanvas("Vectorscope", rgb.reversedArray(), Modifier.weight(1f))
    }
}

@Composable
private fun ScopeCanvas(title: String, data: FloatArray, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(colors.surfaceVariant)
            for (i in 1..3) {
                val y = size.height * i / 4f
                drawLine(colors.outline.copy(alpha = .25f), Offset(0f, y), Offset(size.width, y))
            }
            if (data.size > 1) {
                for (i in 1 until data.size) {
                    val x0 = size.width * (i - 1) / (data.size - 1)
                    val x1 = size.width * i / (data.size - 1)
                    val y0 = size.height * (1f - data[i - 1].coerceIn(0f, 1f))
                    val y1 = size.height * (1f - data[i].coerceIn(0f, 1f))
                    drawLine(colors.primary, Offset(x0, y0), Offset(x1, y1), 1.5f)
                }
            }
        }
    }
}

@Composable
fun ProfessionalAudioMixer(
    trackNames: List<String>,
    levels: List<Float>,
    onLevel: (Int, Float) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        trackNames.forEachIndexed { i, name ->
            Column(Modifier.width(72.dp)) {
                Text(name, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = (levels.getOrNull(i) ?: 0f).coerceIn(0f, 1f),
                    onValueChange = { onLevel(i, it) }
                )
                Text(
                    String.format("%.1f dB", ((levels.getOrNull(i) ?: 0f) * 24f) - 24f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
