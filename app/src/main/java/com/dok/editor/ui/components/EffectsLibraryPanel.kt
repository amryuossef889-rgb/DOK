package com.dok.editor.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.view.View
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dok.editor.engine.effects.ExternalEffectLibrary
import com.dok.editor.model.ExternalEffectAsset

@Composable
fun EffectsLibraryPanel(
    library: ExternalEffectLibrary,
    onAddToSelectedClip: (ExternalEffectAsset) -> Unit,
    onAddSoundEffect: (Uri, String) -> Unit = { _, _ -> },
    onImportedAsset: (ExternalEffectAsset) -> Unit = {},
    onRemovedAsset: (String) -> Unit = {}
) {
    var assets by remember { mutableStateOf(library.all()) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported Effect"
            val asset = library.importAsset(context = context, uri = uri, name = name)
            onImportedAsset(asset)
            assets = library.all()
        }
    }

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("IMPORT") }
            OutlinedButton(onClick = { assets = library.all() }) { Text("REFRESH") }
        }
        LazyColumn(Modifier.heightIn(max = 280.dp)) {
            items(assets, key = { it.id }) { asset ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        Modifier.weight(1f).dragAndDropSource { _ ->
                            DragAndDropTransferData(
                                ClipData.newPlainText("DOK_EFFECT_ASSET", asset.id),
                                flags = View.DRAG_FLAG_GLOBAL
                            )
                        }
                    ) {
                        Text(asset.name)
                        Text(asset.kind)
                    }
                    OutlinedButton(onClick = {
                        if (asset.kind == "audio-sfx") onAddSoundEffect(Uri.parse(asset.uri), asset.name)
                        else onAddToSelectedClip(asset)
                    }) { Text(if (asset.kind == "audio-sfx") "INSERT" else "ADD") }
                    OutlinedButton(onClick = {
                        library.remove(asset.id)
                        onRemovedAsset(asset.id)
                        assets = library.all()
                    }) { Text("REMOVE") }
                }
            }
        }
    }
}
