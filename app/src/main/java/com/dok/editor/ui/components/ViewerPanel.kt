package com.dok.editor.ui.components

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dok.editor.command.EditorCommand
import com.dok.editor.model.Project
import com.dok.editor.ui.theme.*
import com.dok.editor.viewmodel.EditorViewModel

@Composable
fun ViewerPanel(
    project: Project, currentTimeUs: Long, isPlaying: Boolean, playbackSpeed: Float,
    onCommand: (EditorCommand) -> Unit, modifier: Modifier = Modifier, onSettings: () -> Unit = {}
) {
    val clip = project.tracks.filter { it.type == com.dok.editor.model.TrackType.VIDEO }.flatMap { it.clips }
        .filter { it.mediaUri.isNotBlank() && currentTimeUs >= it.startTimeUs && currentTimeUs < it.endTimeUs }
        .maxByOrNull { it.startTimeUs }

    Surface(color = DokSurface, modifier = modifier.testTag("viewer_panel")) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(DokSurfaceElevated).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DOK", color = DokAccent, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                Spacer(Modifier.width(10.dp))
                Text(project.name, color = DokPrimaryText, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(project.width.toString() + "×" + project.height + "  " + project.fps + "fps", color = DokSecondaryText, fontSize = 10.sp)
                IconButton(onClick = onSettings, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Settings, "Project settings", tint = DokPrimaryText) }
                Text(EditorViewModel.formatTimecode(currentTimeUs, project.fps), color = DokAccent, fontSize = 14.sp, modifier = Modifier.testTag("timecode_display"))
            }
            Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF07080A)).padding(8.dp), contentAlignment = Alignment.Center) {
                val context = LocalContext.current
                val player = remember(context) { ExoPlayer.Builder(context).build() }

                DisposableEffect(player) {
                    onDispose { player.release() }
                }

                LaunchedEffect(clip?.mediaUri) {
                    val uri = clip?.mediaUri
                    if (uri.isNullOrBlank()) {
                        player.stop()
                        player.clearMediaItems()
                    } else {
                        player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                        player.prepare()
                        val localMs = ((currentTimeUs - (clip.startTimeUs)) / 1000L).coerceAtLeast(0L)
                        player.seekTo(localMs)
                        player.playWhenReady = isPlaying
                    }
                }

                LaunchedEffect(currentTimeUs, isPlaying, clip?.id) {
                    if (clip != null) {
                        val targetMs = ((currentTimeUs - clip.startTimeUs) / 1000L).coerceAtLeast(0L)
                        val driftMs = kotlin.math.abs(player.currentPosition - targetMs)
                        if (!isPlaying || driftMs > 120L) {
                            player.seekTo(targetMs)
                        }
                        if (isPlaying) player.play() else player.pause()
                    } else {
                        player.pause()
                    }
                }

                val isImageClip = remember(clip?.mediaUri) {
                    clip?.mediaUri?.let { mediaUri ->
                        context.contentResolver.getType(Uri.parse(mediaUri)).orEmpty().startsWith("image/")
                    } ?: false
                }

                if (isImageClip && clip != null) {
                    AsyncImage(
                        model = Uri.parse(clip.mediaUri),
                        contentDescription = clip.mediaName,
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, DokDivider, RoundedCornerShape(4.dp))
                            .testTag("master_image_view"),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, DokDivider, RoundedCornerShape(4.dp))
                        .testTag("master_video_view"),
                    factory = {
                        PlayerView(it).apply {
                            useController = false
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { view ->
                        view.player = player
                    }
                )
                if (clip == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MASTER VIEWER", color = DokSecondaryText, fontSize = 12.sp, letterSpacing = 2.sp)
                        Text("Import a video to begin", color = DokSecondaryText.copy(alpha = .65f), fontSize = 11.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(50.dp).background(DokSurfaceElevated), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onCommand(EditorCommand.JumpToStart) }) { Icon(Icons.Default.FirstPage, "Start", tint = DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.ShuttleReverse) }) { Icon(Icons.Default.FastRewind, "Reverse", tint = DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.StepFrames(-1)) }) { Icon(Icons.Default.SkipPrevious, "Previous frame", tint = DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.TogglePlayPause) }, modifier = Modifier.size(42.dp).background(if (isPlaying) DokAccent else DokDivider, RoundedCornerShape(21.dp))) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = if (isPlaying) Color.Black else DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.StepFrames(1)) }) { Icon(Icons.Default.SkipNext, "Next frame", tint = DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.ShuttleForward) }) { Icon(Icons.Default.FastForward, "Forward", tint = DokPrimaryText) }
                IconButton({ onCommand(EditorCommand.JumpToEnd) }) { Icon(Icons.Default.LastPage, "End", tint = DokPrimaryText) }
            }
        }
    }
}
