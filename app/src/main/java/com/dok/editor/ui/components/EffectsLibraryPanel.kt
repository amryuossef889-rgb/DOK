package com.dok.editor.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import com.dok.editor.engine.effects.ExternalEffectLibrary
import com.dok.editor.model.ExternalEffectAsset

@Composable
fun EffectsLibraryPanel(
    library: ExternalEffectLibrary,
    onAddToSelectedClip: (ExternalEffectAsset) -> Unit,
    onAddSoundEffect: (Uri, String) -> Unit = { _, _ -> }
) {
    var assets by remember { mutableStateOf(library.all()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported Effect"
            val asset = library.importAsset(
                context = androidx.compose.ui.platform.LocalContext.current,
                uri = uri,
                name = name,
                kind = "external-effect"
            )
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
                    Column(Modifier.weight(1f)) {
                        Text(asset.name)
                        Text(asset.kind)
                    }
                    OutlinedButton(onClick = { onAddToSelectedClip(asset) }) { Text("ADD") }
                    OutlinedButton(onClick = {
                        library.remove(asset.id)
                        assets = library.all()
                    }) { Text("REMOVE") }
                }
            }
        }
    }
}
