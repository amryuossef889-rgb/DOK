package com.dok.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dok.editor.command.EditorCommand
import com.dok.editor.engine.export.ExportPreset
import com.dok.editor.ui.theme.DokAccent
import com.dok.editor.ui.theme.DokDivider
import com.dok.editor.ui.theme.DokPrimaryText
import com.dok.editor.ui.theme.DokSecondaryText
import com.dok.editor.ui.theme.DokSuccess
import com.dok.editor.ui.theme.DokSurface
import com.dok.editor.ui.theme.DokSurfaceElevated
import com.dok.editor.viewmodel.ExportUiState

@Composable
fun DeliverPanel(
    selectedPreset: ExportPreset,
    exportUiState: ExportUiState,
    exportProgress: Float,
    onSelectPreset: (ExportPreset) -> Unit,
    onCommand: (EditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DokSurface,
        modifier = modifier.testTag("deliver_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = "Deliver",
                        tint = DokAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DELIVER / EXPORT SETTINGS",
                        color = DokPrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(DokDivider, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "MP4 / H.264 + AAC",
                        color = DokSecondaryText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = DokDivider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Export Presets",
                color = DokPrimaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Presets List
            val presets = listOf(
                ExportPreset.YOUTUBE_1080P_60,
                ExportPreset.TIKTOK_SHORTS_1080P_60,
                ExportPreset.GAMING_4K_30,
                ExportPreset.FAST_720P_30
            )

            presets.forEach { preset ->
                val isSelected = preset.id == selectedPreset.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            if (isSelected) DokAccent.copy(alpha = 0.15f) else DokSurfaceElevated,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) DokAccent else DokDivider,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectPreset(preset) }
                        .padding(12.dp)
                        .testTag("preset_${preset.id.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = preset.name,
                                color = if (isSelected) DokAccent else DokPrimaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${preset.width}x${preset.height} @ ${preset.fps}fps • ${(preset.videoBitrateBps / 1_000_000)}Mbps",
                                color = DokSecondaryText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected Preset",
                                tint = DokAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Export Status & Progress
            if (exportUiState == ExportUiState.EXPORTING) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = "Rendering Timeline... ${(exportProgress * 100).toInt()}%",
                        color = DokAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = exportProgress,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = DokAccent,
                        trackColor = DokDivider
                    )
                }
            } else if (exportUiState == ExportUiState.SUCCESS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DokSuccess.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = DokSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Export saved to Movies/DokEditor/",
                        color = DokSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Export Action Button
            Button(
                onClick = {
                    onCommand(EditorCommand.RequestExport(selectedPreset))
                },
                enabled = exportUiState != ExportUiState.EXPORTING,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DokAccent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("start_export_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RENDER & EXPORT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
