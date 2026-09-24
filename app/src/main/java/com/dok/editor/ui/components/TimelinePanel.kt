package com.dok.editor.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.dok.editor.command.EditorCommand
import com.dok.editor.model.*
import com.dok.editor.ui.theme.*
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun TimelinePanel(
    project: Project, currentTimeUs: Long, selectedClipId: String?, isSnappingEnabled: Boolean,
    zoomLevel: Float, canUndo: Boolean, canRedo: Boolean, inPointUs: Long?, outPointUs: Long?,
    onCommand: (EditorCommand) -> Unit, modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val pps = 72f * zoomLevel
    val duration = max(project.durationUs, 30_000_000L)
    val totalWidth = (duration / 1_000_000f * pps).dp
    val density = LocalDensity.current

    Surface(color = DokBackground, modifier = modifier.testTag("timeline_panel")) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(42.dp).background(DokSurfaceElevated).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TIMELINE", color = DokPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                IconButton({ onCommand(EditorCommand.SplitClipAtPlayhead) }, Modifier.size(34.dp)) { Icon(Icons.Default.ContentCut, "Razor", tint = DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.DeleteSelectedClip) }, Modifier.size(34.dp)) { Icon(Icons.Default.Delete, "Delete", tint = if (selectedClipId != null) DokPrimaryText else DokSecondaryText) }
                Text(if (isSnappingEnabled) "SNAP" else "FREE", color = if (isSnappingEnabled) DokAccent else DokSecondaryText, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onCommand(EditorCommand.ToggleSnapping) }.padding(6.dp))
                Spacer(Modifier.weight(1f))
                Text(EditorViewModelFormat(currentTimeUs, project.fps), color = DokAccent, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                IconButton({ onCommand(EditorCommand.ZoomToFit) }, Modifier.size(34.dp)) { Icon(Icons.Default.FitScreen, "Fit", tint = DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.ZoomTimeline(-.25f)) }, Modifier.size(34.dp)) { Icon(Icons.Default.ZoomOut, "Zoom out", tint = DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.ZoomTimeline(.25f)) }, Modifier.size(34.dp)) { Icon(Icons.Default.ZoomIn, "Zoom in", tint = DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.Undo) }, Modifier.size(34.dp), enabled = canUndo) { Icon(Icons.Default.Undo, "Undo", tint = DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.Redo) }, Modifier.size(34.dp), enabled = canRedo) { Icon(Icons.Default.Redo, "Redo", tint = DokPrimaryText) }
            }

            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(76.dp).fillMaxHeight().background(DokSurface)) {
                    Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) { Text("TRACKS", color = DokSecondaryText, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    project.tracks.forEach { track ->
                        Row(Modifier.fillMaxWidth().height(58.dp).border(1.dp, DokDivider), verticalAlignment = Alignment.CenterVertically) {
                            Text(track.name, color = if (track.type == TrackType.VIDEO) DokAccent else DokPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
                        }
                    }
                }

                Box(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                    Box(Modifier.width(totalWidth).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize()) {
                            Box(
                                Modifier.fillMaxWidth().height(34.dp).background(DokSurfaceElevated)
                                    .pointerInput(duration, pps) {
                                        detectTapGestures { o ->
                                            val us = (o.x / density.density / pps * 1_000_000L).toLong()
                                            onCommand(EditorCommand.ScrubTo(us))
                                        }
                                    }
                            ) {
                                val step = if (pps >= 60) 1 else if (pps >= 30) 2 else 5
                                for (s in 0..(duration / 1_000_000L).toInt() step step) {
                                    Text(
                                        EditorViewModelFormat(s * 1_000_000L, project.fps).substring(3),
                                        color = DokSecondaryText, fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.offset(x = (s * pps).dp + 2.dp, y = 5.dp)
                                    )
                                }
                            }
                            project.tracks.forEach { track ->
                                Box(Modifier.fillMaxWidth().height(58.dp).background(DokBackground).border(1.dp, DokDivider)) {
                                    track.clips.forEach { clip ->
                                        val x = (clip.startTimeUs / 1_000_000f * pps).dp
                                        val w = max(28f, clip.durationUs / 1_000_000f * pps).dp
                                        Box(
                                            Modifier.offset(x).width(w).height(50.dp).padding(vertical = 4.dp)
                                                .background(if (clip.id == selectedClipId) DokAccent.copy(.30f) else if (track.type == TrackType.VIDEO) Color(0xFF193E56) else Color(0xFF263F35), RoundedCornerShape(4.dp))
                                                .border(1.dp, if (clip.id == selectedClipId) DokAccent else DokDivider, RoundedCornerShape(4.dp))
                                                .clickable { onCommand(EditorCommand.SelectClip(clip.id)) }
                                                .testTag("clip_" + clip.id)
                                        ) {
                                            Column(Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) {
                                                Text(clip.mediaName.ifBlank { "Clip" }, color = Color.White, fontSize = 9.sp, maxLines = 1)
                                                Text(EditorViewModelFormat(clip.durationUs, project.fps), color = Color.White.copy(.55f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val playheadX = currentTimeUs / 1_000_000f * pps
                        Box(
                            Modifier.offset(x = playheadX.dp).fillMaxHeight().width(18.dp)
                                .pointerInput(currentTimeUs, pps) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        val px = with(density) { drag.x.toDp().value }
                                        val next = currentTimeUs + (px / pps * 1_000_000L).toLong()
                                        onCommand(EditorCommand.ScrubTo(next))
                                    }
                                }
                        ) {
                            Box(Modifier.align(Alignment.TopCenter).width(12.dp).height(16.dp).background(DokPlayhead, RoundedCornerShape(3.dp)))
                            Box(Modifier.align(Alignment.TopCenter).offset(y = 12.dp).width(2.dp).fillMaxHeight().background(DokPlayhead))
                        }
                    }
                }
            }
        }
    }
}

private fun EditorViewModelFormat(us: Long, fps: Int): String {
    val total = us.coerceAtLeast(0) / 1_000_000L
    val frames = ((us.coerceAtLeast(0) % 1_000_000L) * fps / 1_000_000L).toInt()
    return "%02d:%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60, frames)
}
