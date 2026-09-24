package com.dok.editor.ui.components

import android.content.ClipData
import android.net.Uri
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToPhotos
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dok.editor.command.EditorCommand
import com.dok.editor.model.MediaAsset
import com.dok.editor.ui.theme.*

@Composable
fun MediaPoolPanel(
    assets: List<MediaAsset>,
    onCommand: (EditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = DokSurface, modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(DokSurfaceElevated).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MEDIA POOL", color = DokPrimaryText, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${assets.size} source files", color = DokSecondaryText, fontSize = 9.sp)
            }
            Text(
                "Sources live here independently from the timeline. Reuse one asset in multiple clips.",
                color = DokSecondaryText, fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
            if (assets.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Movie, null, tint = DokSecondaryText, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Import media to build the project pool.", color = DokSecondaryText, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(assets, key = { it.id }) { asset ->
                        MediaPoolAssetRow(asset) {
                            onCommand(EditorCommand.AddMediaAssetToTimeline(asset.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPoolAssetRow(asset: MediaAsset, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(DokSurfaceElevated, RoundedCornerShape(6.dp))
            .border(1.dp, if (asset.isOffline) DokWarning else DokDivider, RoundedCornerShape(6.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = Uri.parse(asset.uri),
            contentDescription = null,
            modifier = Modifier.size(72.dp)
                .background(DokBackground, RoundedCornerShape(4.dp))
                .dragAndDropSource {
                    DragAndDropTransferData(
                        ClipData.newPlainText("DOK_MEDIA_ASSET", asset.id),
                        flags = View.DRAG_FLAG_GLOBAL
                    )
                },
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(asset.name.ifBlank { "Untitled media" }, color = DokPrimaryText, fontSize = 11.sp, maxLines = 1)
            Text(
                "${formatDuration(asset.durationUs)} • ${asset.width}×${asset.height}",
                color = DokSecondaryText, fontSize = 9.sp
            )
            if (asset.isOffline) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, null, tint = DokWarning, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("OFFLINE — relink required", color = DokWarning, fontSize = 8.sp)
                }
            }
        }
        FilledTonalButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Icon(Icons.Default.AddToPhotos, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("ADD", fontSize = 9.sp)
        }
    }
}

private fun formatDuration(us: Long): String {
    val total = (us.coerceAtLeast(0L) / 1_000_000L)
    return "%02d:%02d".format(total / 60, total % 60)
}
