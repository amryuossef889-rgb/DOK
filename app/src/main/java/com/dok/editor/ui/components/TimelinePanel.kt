package com.dok.editor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dok.editor.command.EditorCommand
import com.dok.editor.model.Project
import com.dok.editor.model.TimelineClip
import com.dok.editor.model.Track
import com.dok.editor.model.TrackType
import com.dok.editor.ui.theme.DokAccent
import com.dok.editor.ui.theme.DokBackground
import com.dok.editor.ui.theme.DokDivider
import com.dok.editor.ui.theme.DokPlayhead
import com.dok.editor.ui.theme.DokPrimaryText
import com.dok.editor.ui.theme.DokSecondaryText
import com.dok.editor.ui.theme.DokSurface
import com.dok.editor.ui.theme.DokSurfaceElevated
import com.dok.editor.ui.theme.DokTrackAudio
import com.dok.editor.ui.theme.DokTrackVideo
import com.dok.editor.ui.theme.DokWarning
import kotlin.math.max

@Composable
fun TimelinePanel(
    project: Project,
    currentTimeUs: Long,
    selectedClipId: String?,
    isSnappingEnabled: Boolean,
    zoomLevel: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    inPointUs: Long?,
    outPointUs: Long?,
    onCommand: (EditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // 1 second on timeline = 80.dp * zoomLevel
    val pixelsPerSecond = 80f * zoomLevel
    val projectDurationUs = max(project.durationUs, 10_000_000L)
    val totalTimelineWidthDp = ((projectDurationUs / 1_000_000f) * pixelsPerSecond).dp

    Surface(
        color = DokBackground,
        modifier = modifier.testTag("timeline_panel")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Action Toolbar (Split, Delete, Ripple, Snap, Undo, Redo, Zoom)
            TimelineToolbar(
                canUndo = canUndo,
                canRedo = canRedo,
                isSnappingEnabled = isSnappingEnabled,
                hasSelection = selectedClipId != null,
                currentTimeUs = currentTimeUs,
                onCommand = onCommand
            )

            Divider(color = DokDivider, thickness = 1.dp)

            // Timeline Scrollable Container
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Fixed Track Headers (V1, A1)
                TrackHeadersColumn(
                    tracks = project.tracks,
                    modifier = Modifier.width(72.dp)
                )

                Divider(color = DokDivider, modifier = Modifier.fillMaxHeight().width(1.dp))

                // Scrollable Tracks Area with Ruler & Playhead
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState)
                ) {
                    Column(
                        modifier = Modifier
                            .width(totalTimelineWidthDp)
                            .fillMaxHeight()
                    ) {
                        // Ruler Header
                        TimelineRuler(
                            durationUs = projectDurationUs,
                            pixelsPerSecond = pixelsPerSecond,
                            inPointUs = inPointUs,
                            outPointUs = outPointUs,
                            currentTimeUs = currentTimeUs,
                            onSeek = { seekUs -> onCommand(EditorCommand.ScrubTo(seekUs)) }
                        )

                        Divider(color = DokDivider, thickness = 1.dp)

                        // Tracks Content
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val clickedTimeUs = ((offset.x / (pixelsPerSecond * density)) * 1_000_000L).toLong()
                                        onCommand(EditorCommand.ScrubTo(clickedTimeUs))
                                    }
                                }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                project.tracks.forEach { track ->
                                    TrackRow(
                                        track = track,
                                        selectedClipId = selectedClipId,
                                        pixelsPerSecond = pixelsPerSecond,
                                        onSelectClip = { clipId -> onCommand(EditorCommand.SelectClip(clipId)) }
                                    )
                                    Divider(color = DokDivider, thickness = 1.dp)
                                }
                            }

                            // Scrub Playhead Line + Diamond Handle
                            val playheadOffsetXDp = ((currentTimeUs / 1_000_000f) * pixelsPerSecond).dp
                            Playhead(
                                offsetXDp = playheadOffsetXDp,
                                onDragSeek = { deltaPx ->
                                    val deltaUs = ((deltaPx / (pixelsPerSecond * 2.5f)) * 1_000_000L).toLong()
                                    onCommand(EditorCommand.ScrubTo(currentTimeUs + deltaUs))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    isSnappingEnabled: Boolean,
    hasSelection: Boolean,
    currentTimeUs: Long,
    onCommand: (EditorCommand) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DokSurfaceElevated)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("timeline_toolbar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Editing operations: Split, Ripple Delete, Delete
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Split (Razor Blade)
            IconButton(
                onClick = { onCommand(EditorCommand.SplitClipAtPlayhead) },
                modifier = Modifier.size(36.dp).testTag("split_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = "Split at Playhead (Ctrl+B)",
                    tint = DokPrimaryText,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Ripple Delete
            IconButton(
                onClick = { onCommand(EditorCommand.RippleDeleteSelectedClip) },
                enabled = hasSelection,
                modifier = Modifier.size(36.dp).testTag("ripple_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Ripple Delete",
                    tint = if (hasSelection) DokWarning else DokSecondaryText.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Lift Delete
            IconButton(
                onClick = { onCommand(EditorCommand.DeleteSelectedClip) },
                enabled = hasSelection,
                modifier = Modifier.size(36.dp).testTag("delete_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Lift Delete",
                    tint = if (hasSelection) DokPrimaryText else DokSecondaryText.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Snapping toggle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (isSnappingEnabled) DokAccent.copy(alpha = 0.2f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        if (isSnappingEnabled) DokAccent else DokDivider,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onCommand(EditorCommand.ToggleSnapping) }
                    .testTag("snap_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SNAP",
                    color = if (isSnappingEnabled) DokAccent else DokSecondaryText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // In/Out Marks & Undo / Redo & Zoom
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mark In
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .clickable { onCommand(EditorCommand.SetInPoint(currentTimeUs)) }
                    .background(DokDivider, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("mark_in_button")
            ) {
                Text(text = "IN", color = DokPrimaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            // Mark Out
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .clickable { onCommand(EditorCommand.SetOutPoint(currentTimeUs)) }
                    .background(DokDivider, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("mark_out_button")
            ) {
                Text(text = "OUT", color = DokPrimaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Undo
            IconButton(
                onClick = { onCommand(EditorCommand.Undo) },
                enabled = canUndo,
                modifier = Modifier.size(32.dp).testTag("undo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo (Ctrl+Z)",
                    tint = if (canUndo) DokPrimaryText else DokSecondaryText.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Redo
            IconButton(
                onClick = { onCommand(EditorCommand.Redo) },
                enabled = canRedo,
                modifier = Modifier.size(32.dp).testTag("redo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo (Ctrl+Y)",
                    tint = if (canRedo) DokPrimaryText else DokSecondaryText.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Zoom Out
            IconButton(
                onClick = { onCommand(EditorCommand.ZoomTimeline(-0.25f)) },
                modifier = Modifier.size(32.dp).testTag("zoom_out_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Zoom Out",
                    tint = DokPrimaryText,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Zoom In
            IconButton(
                onClick = { onCommand(EditorCommand.ZoomTimeline(0.25f)) },
                modifier = Modifier.size(32.dp).testTag("zoom_in_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom In",
                    tint = DokPrimaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TrackHeadersColumn(
    tracks: List<Track>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DokSurface)
            .fillMaxHeight()
    ) {
        // Empty space for ruler height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(DokSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "TRACKS",
                color = DokSecondaryText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Divider(color = DokDivider, thickness = 1.dp)

        tracks.forEach { track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = track.name,
                        color = when (track.type) {
                            TrackType.VIDEO -> DokAccent
                            TrackType.AUDIO -> Color(0xFF48BB78)
                            TrackType.TEXT -> Color(0xFFB794F4)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${track.clips.size} clips",
                        color = DokSecondaryText,
                        fontSize = 9.sp
                    )
                }

                if (track.isMuted) {
                    Icon(
                        imageVector = Icons.Default.VolumeMute,
                        contentDescription = "Muted",
                        tint = DokWarning,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Divider(color = DokDivider, thickness = 1.dp)
        }
    }
}

@Composable
fun TimelineRuler(
    durationUs: Long,
    pixelsPerSecond: Float,
    inPointUs: Long?,
    outPointUs: Long?,
    currentTimeUs: Long,
    onSeek: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(DokSurfaceElevated)
            .pointerInput(durationUs) {
                detectTapGestures { offset ->
                    val seekUs = ((offset.x / (pixelsPerSecond * density)) * 1_000_000L).toLong()
                    onSeek(seekUs)
                }
            }
            .testTag("timeline_ruler")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalSeconds = (durationUs / 1_000_000L).toInt() + 1
            for (sec in 0..totalSeconds) {
                val x = sec * pixelsPerSecond
                val isMajor = sec % 5 == 0
                val tickHeight = if (isMajor) size.height * 0.6f else size.height * 0.3f
                drawLine(
                    color = if (isMajor) Color.LightGray else Color.DarkGray,
                    start = Offset(x, size.height - tickHeight),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
            }

            // In / Out shading
            if (inPointUs != null && outPointUs != null && outPointUs > inPointUs) {
                val inX = (inPointUs / 1_000_000f) * pixelsPerSecond
                val outX = (outPointUs / 1_000_000f) * pixelsPerSecond
                drawRect(
                    color = Color(0x334A9EFF),
                    topLeft = Offset(inX, 0f),
                    size = Size(outX - inX, size.height)
                )
            }
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    selectedClipId: String?,
    pixelsPerSecond: Float,
    onSelectClip: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(DokBackground)
    ) {
        track.clips.forEach { clip ->
            val clipStartDp = ((clip.startTimeUs / 1_000_000f) * pixelsPerSecond).dp
            val clipWidthDp = ((clip.durationUs / 1_000_000f) * pixelsPerSecond).dp
            val isSelected = clip.id == selectedClipId

            ClipItem(
                clip = clip,
                trackType = track.type,
                isSelected = isSelected,
                modifier = Modifier
                    .offset(x = clipStartDp)
                    .width(clipWidthDp)
                    .height(52.dp)
                    .padding(vertical = 2.dp)
                    .clickable { onSelectClip(clip.id) }
            )
        }
    }
}

@Composable
fun ClipItem(
    clip: TimelineClip,
    trackType: TrackType,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val baseColor = when (trackType) {
        TrackType.VIDEO -> DokTrackVideo
        TrackType.AUDIO -> DokTrackAudio
        TrackType.TEXT -> Color(0xFF553C9A)
    }

    Box(
        modifier = modifier
            .background(baseColor, RoundedCornerShape(4.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else DokDivider,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("clip_item_${clip.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = clip.mediaName.ifEmpty { "Clip" },
                    color = DokPrimaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (clip.speed != 1.0f) {
                    Text(
                        text = "${clip.speed}x",
                        color = DokWarning,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Waveform or Thumbnail track indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${clip.durationUs / 1_000_000f}s",
                    color = DokSecondaryText,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (clip.effects.isNotEmpty()) {
                    Text(
                        text = "FX (${clip.effects.size})",
                        color = DokAccent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun Playhead(
    offsetXDp: androidx.compose.ui.unit.Dp,
    onDragSeek: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .offset(x = offsetXDp - 6.dp)
            .fillMaxHeight()
            .width(12.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragSeek(dragAmount.x)
                }
            }
            .testTag("playhead_handle")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Diamond handle at top
            val path = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height * 0.05f)
                lineTo(size.width / 2f, size.height * 0.1f)
                lineTo(0f, size.height * 0.05f)
                close()
            }
            drawPath(path, DokPlayhead)

            // Red vertical playhead line
            drawLine(
                color = DokPlayhead,
                start = Offset(size.width / 2f, size.height * 0.1f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
