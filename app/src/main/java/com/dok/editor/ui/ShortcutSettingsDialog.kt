package com.dok.editor.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dok.editor.input.ShortcutStore

@Composable
fun ShortcutSettingsDialog(context: Context, onDismiss: () -> Unit) {
    val initial = remember { ShortcutStore.all(context) }
    val values = remember { mutableStateMapOf<String, String>().apply { putAll(initial) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keyboard Shortcuts") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type a key or chord such as Ctrl+Z, J, or Space.", style = MaterialTheme.typography.bodySmall)
                values.keys.forEach { name ->
                    OutlinedTextField(
                        value = values[name].orEmpty(),
                        onValueChange = { values[name] = it },
                        label = { Text(name) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { ShortcutStore.save(context, values); onDismiss() }) { Text("Save shortcuts") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
