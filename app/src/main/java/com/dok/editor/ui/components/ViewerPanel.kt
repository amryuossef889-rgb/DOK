package com.dok.editor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dok.editor.command.EditorCommand
import com.dok.editor.model.Project
import com.dok.editor.ui.theme.DokAccent
import com.dok.editor.ui.theme.DokBackground
import com.dok.editor.ui.theme.DokDivider
import com.dok.editor.ui.theme.DokPlayhead
import com.dok.editor.ui.theme.DokPrimaryText
import com.dok.editor.ui.theme.DokSecondaryText
import com.dok.editor.ui.theme.DokSurface
import com.dok.editor.ui.theme.DokSurfaceElevated
import com.dok.editor.viewmodel.EditorViewModel

@Composable
fun ViewerPanel(
    project: Project,
    currentTimeUs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    onCommand: (EditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DokSurface,
        modifier = modifier.testTag("viewer_panel")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Bar: Project name & Resolution badge & Timecode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DokSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = project.name,
                        color = DokPrimaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("viewer_project_name")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(DokDivider, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${project.width}x${project.height} @ ${project.fps}fps",
                            color = DokSecondaryText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // SMPTE Timecode display
                Text(
                    text = EditorViewModel.formatTimecode(currentTimeUs, project.fps),
                    color = DokAccent,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("timecode_display")
                )
            }

            // DaVinci Resolve Master Preview Canvas (16:9)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DokBackground)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black, RoundedCornerShape(4.dp))
                        .border(1.dp, DokDivider, RoundedCornerShape(4.dp))
                        .testTag("viewer_canvas"),
                    contentAlignment = Alignment.Center
                ) {
                    // Render simulated video composition frame
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Background grid / safe margins
                        val safeMarginX = size.width * 0.05f
                        val safeMarginY = size.height * 0.05f
                        drawRect(
                            color = Color(0x334A9EFF),
                            topLeft = Offset(safeMarginX, safeMarginY),
                            size = Size(size.width - safeMarginX * 2, size.height - safeMarginY * 2),
                            style = Stroke(width = 1f)
                        )
                        // Center crosshair
                        drawLine(
                            color = Color(0x334A9EFF),
                            start = Offset(size.width / 2f - 12f, size.height / 2f),
                            end = Offset(size.width / 2f + 12f, size.height / 2f),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color(0x334A9EFF),
                            start = Offset(size.width / 2f, size.height / 2f - 12f),
                            end = Offset(size.width / 2f, size.height / 2f + 12f),
                            strokeWidth = 1f
                        )
                    }

                    // Display active video clip badge or placeholder
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "MASTER VIEWER",
                            color = DokSecondaryText.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = EditorViewModel.formatTimecode(currentTimeUs, project.fps),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        if (playbackSpeed != 1.0f && isPlaying) {
                            Text(
                                text = "${playbackSpeed}x Shuttle",
                                color = DokAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Transport Control Bar (JKL + Shuttle + Step + Play/Pause)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DokSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Jump to Start
                IconButton(
                    onClick = { onCommand(EditorCommand.JumpToStart) },
                    modifier = Modifier.size(36.dp).testTag("jump_start_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FirstPage,
                        contentDescription = "Jump to Start",
                        tint = DokPrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Shuttle Reverse (J)
                IconButton(
                    onClick = { onCommand(EditorCommand.ShuttleReverse) },
                    modifier = Modifier.size(36.dp).testTag("shuttle_reverse_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Shuttle Reverse (J)",
                        tint = if (isPlaying && playbackSpeed < 0f) DokAccent else DokPrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Step 1 Frame Back
                IconButton(
                    onClick = { onCommand(EditorCommand.StepFrames(-1)) },
                    modifier = Modifier.size(36.dp).testTag("step_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Step 1 Frame Back",
                        tint = DokPrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play / Pause (Space / K)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (isPlaying) DokAccent else DokDivider, RoundedCornerShape(20.dp))
                        .clickable { onCommand(EditorCommand.TogglePlayPause) }
                        .testTag("play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause (K)" else "Play (Space)",
                        tint = if (isPlaying) Color.Black else DokPrimaryText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Step 1 Frame Forward
                IconButton(
                    onClick = { onCommand(EditorCommand.StepFrames(1)) },
                    modifier = Modifier.size(36.dp).testTag("step_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Step 1 Frame Forward",
                        tint = DokPrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Shuttle Forward (L)
                IconButton(
                    onClick = { onCommand(EditorCommand.ShuttleForward) },
                    modifier = Modifier.size(36.dp).testTag("shuttle_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Shuttle Forward (L)",
                        tint = if (isPlaying && playbackSpeed > 1f) DokAccent else DokPrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Jump to End
                IconButton(
                    onClick = { onCommand(EditorCommand.JumpToEnd) },
                    modifier = Modifier.size(36.dp).testTag("jump_end_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LastPage,
                        contentDescription = "Jump to End",
                        tint = DokPrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
