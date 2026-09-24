package com.dok.editor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dok.editor.command.EditorCommand
import com.dok.editor.model.Project
import com.dok.editor.ui.theme.DokAccent

@Composable
fun ProjectSettingsDialog(project: Project, onDismiss: () -> Unit, onCommand: (EditorCommand) -> Unit) {
    var width by remember(project.width) { mutableStateOf(project.width.toString()) }
    var height by remember(project.height) { mutableStateOf(project.height.toString()) }
    var fps by remember(project.fps) { mutableStateOf(project.fps.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Project Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Timeline / Master", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("16:9" to Pair("1920","1080"), "9:16" to Pair("1080","1920"), "1:1" to Pair("1080","1080")).forEach { (label, size) ->
                        OutlinedButton(onClick = { width=size.first; height=size.second }) { Text(label) }
                    }
                }
                OutlinedTextField(width, { width=it.filter(Char::isDigit) }, label={Text("Width")}, singleLine=true, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(height, { height=it.filter(Char::isDigit) }, label={Text("Height")}, singleLine=true, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(fps, { fps=it.filter(Char::isDigit) }, label={Text("Frame rate (fps)")}, singleLine=true, modifier=Modifier.fillMaxWidth())
                Text("Changing this changes the timeline/master resolution, not the source files.", style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCommand(EditorCommand.SetProjectSettings(width.toIntOrNull() ?: project.width, height.toIntOrNull() ?: project.height, fps.toIntOrNull() ?: project.fps))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = DokAccent)
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick=onDismiss) { Text("Cancel") } }
    )
}
