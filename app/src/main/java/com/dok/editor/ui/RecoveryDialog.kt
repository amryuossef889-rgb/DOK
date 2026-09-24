package com.dok.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecoveryDialog(
    snapshots: List<String>,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Project Recovery") },
        text = {
            if (snapshots.isEmpty()) {
                Text("No recovery snapshots are available.")
            } else {
                Column {
                    Text("Choose a snapshot to restore. The current project is preserved by autosave before this operation.")
                    LazyColumn(Modifier.heightIn(max = 320.dp).fillMaxWidth().padding(top = 10.dp)) {
                        items(snapshots, key = { it }) { name ->
                            Button(
                                onClick = { onRestore(name); onDismiss() },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) { Text(name) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
