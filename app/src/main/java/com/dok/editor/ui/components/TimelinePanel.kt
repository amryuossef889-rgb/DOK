package com.dok.editor.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.dok.editor.command.EditorCommand
import com.dok.editor.model.*
import com.dok.editor.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

@Composable
fun TimelinePanel(
    project: Project, currentTimeUs: Long, selectedClipId: String?, isSnappingEnabled: Boolean,
    zoomLevel: Float, canUndo: Boolean, canRedo: Boolean, inPointUs: Long?, outPointUs: Long?,
    onCommand: (EditorCommand) -> Unit, modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var gestureZoom by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(zoomLevel) }
    val pps = 72f * gestureZoom
    val duration = max(project.durationUs, 30_000_000L)
    val totalWidth = (duration / 1_000_000f * pps).dp

    Surface(color = DokBackground, modifier = modifier.testTag("timeline_panel")) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(42.dp).background(DokSurfaceElevated).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TIMELINE", color = DokPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                IconButton({ onCommand(EditorCommand.SplitClipAtPlayhead) }, Modifier.size(34.dp)) { Icon(Icons.Default.ContentCut, "Razor", tint = DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.DeleteSelectedClip) }, Modifier.size(34.dp)) { Icon(Icons.Default.Delete, "Delete", tint = if (selectedClipId != null) DokPrimaryText else DokSecondaryText) }
                Text(if (isSnappingEnabled) "SNAP" else "FREE", color = if (isSnappingEnabled) DokAccent else DokSecondaryText, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onCommand(EditorCommand.ToggleSnapping) }.padding(6.dp))
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
                        Row(Modifier.fillMaxWidth().height(66.dp).border(1.dp, DokDivider), verticalAlignment = Alignment.CenterVertically) {
                            Text(track.name, color = if (track.type == TrackType.VIDEO) DokAccent else DokPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
                        }
                    }
                }

                Box(
                    Modifier.fillMaxSize().horizontalScroll(scroll).pointerInput(zoomLevel) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            if (abs(zoomChange - 1f) > 0.001f) onCommand(EditorCommand.ZoomTimeline((zoomChange - 1f) * zoomLevel))
                        }
                    }
                ) {
                    Box(Modifier.width(totalWidth).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth().height(34.dp).background(DokSurfaceElevated).pointerInput(duration, pps) {
                                detectTapGestures { o ->
                                    val us = (o.x / density.density / pps * 1_000_000L).toLong()
                                    onCommand(EditorCommand.ScrubTo(us))
                                }
                            }) {
                                val seconds = (duration / 1_000_000L).toInt()
                                for (s in 0..seconds) {
                                    val x = (s * pps).dp
                                    Text(EditorViewModelFormat(s * 1_000_000L, project.fps).substring(3), color = DokSecondaryText, fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.offset(x = x + 2.dp, y = 4.dp))
                                    Box(Modifier.offset(x = x).width(1.dp).height(6.dp).background(DokDivider))
                                }
                            }

                            project.tracks.forEach { track ->
                                Box(Modifier.fillMaxWidth().height(66.dp).background(DokBackground).border(1.dp, DokDivider)) {
                                    track.clips.forEach { clip ->
                                        val xDp = (clip.startTimeUs / 1_000_000f * pps).dp
                                        val wDp = max(32f, clip.durationUs / 1_000_000f * pps).dp
                                        TimelineClipBlock(clip, track, clip.id == selectedClipId, xDp, wDp, pps, density,
                                            { onCommand(EditorCommand.SelectClip(clip.id)) },
                                            { newStart -> onCommand(EditorCommand.MoveClip(clip.id, newStart, track.id)) })
                                    }
                                }
                            }
                        }

                        val playheadX = currentTimeUs / 1_000_000f * pps
                        Box(Modifier.offset(x = playheadX.dp).fillMaxHeight().width(20.dp).pointerInput(currentTimeUs, pps) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val deltaUs = with(density) { drag.x.toDp().value } / pps * 1_000_000L
                                onCommand(EditorCommand.ScrubTo(currentTimeUs + deltaUs.toLong()))
                            }
                        }) {
                            Box(Modifier.align(Alignment.TopCenter).width(12.dp).height(16.dp).background(DokPlayhead, RoundedCornerShape(3.dp)))
                            Box(Modifier.align(Alignment.TopCenter).offset(y = 12.dp).width(2.dp).fillMaxHeight().background(DokPlayhead))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineClipBlock(
    clip: TimelineClip, track: Track, selected: Boolean, x: Dp, width: Dp, pps: Float,
    density: Density, onSelect: () -> Unit, onMove: (Long) -> Unit
) {
    var dragStartUs by remember(clip.id, clip.startTimeUs) { mutableLongStateOf(clip.startTimeUs) }
    var dragDeltaPx by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.offset(x).width(width).height(58.dp).padding(vertical = 4.dp)
            .background(if (selected) DokAccent.copy(.32f) else if (track.type == TrackType.VIDEO) Color(0xFF193E56) else Color(0xFF263F35), RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) DokAccent else DokDivider, RoundedCornerShape(4.dp))
            .pointerInput(clip.id, clip.startTimeUs, pps) {
                detectDragGestures(
                    onDragStart = { dragStartUs = clip.startTimeUs; dragDeltaPx = 0f; onSelect() },
                    onDragCancel = { dragDeltaPx = 0f },
                    onDragEnd = { dragDeltaPx = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDeltaPx += dragAmount.x
                        val deltaUs = with(density) { dragDeltaPx.toDp().value / pps * 1_000_000L }.toLong()
                        onMove((dragStartUs + deltaUs).coerceAtLeast(0L))
                    }
                )
            }
            .clickable { onSelect() }
            .testTag("clip_" + clip.id)
    ) {
        if (track.type == TrackType.VIDEO) ClipFrameThumbnail(clip)
        if (track.type == TrackType.AUDIO && clip.waveform.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 8.dp)) {
                val bars = clip.waveform
                val step = size.width / bars.size.coerceAtLeast(1)
                bars.forEachIndexed { i, amplitude ->
                    val h = size.height * amplitude.coerceIn(.03f, 1f)
                    drawLine(if (selected) DokAccent else Color(0xFF7AD69A), Offset(i * step, size.height / 2f - h / 2f), Offset(i * step, size.height / 2f + h / 2f), strokeWidth = max(1f, step * .55f))
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp)) {
            Text(clip.mediaName.ifBlank { "Clip" }, color = Color.White, fontSize = 9.sp, maxLines = 1)
            Text(EditorViewModelFormat(clip.durationUs, 30), color = Color.White.copy(.70f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
        if (clip.linkedClipId != null) Text("LINK", color = DokAccent, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}

@Composable
private fun ClipFrameThumbnail(clip: TimelineClip) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = clip.mediaUri) {
        value = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(Uri.parse(clip.mediaUri), emptyMap())
                val result = retriever.getFrameAtTime(clip.trimInUs.coerceAtLeast(0L), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                result
            } catch (_: Throwable) { null }
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), alpha = .42f)
}

private fun EditorViewModelFormat(us: Long, fps: Int): String {
    val safe = us.coerceAtLeast(0L)
    val total = safe / 1_000_000L
    val frames = ((safe % 1_000_000L) * fps / 1_000_000L).toInt()
    return "%02d:%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60, frames)
}
