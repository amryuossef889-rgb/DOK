package com.dok.editor.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dok.editor.command.EditorCommand
import com.dok.editor.model.ColorGradingParams
import com.dok.editor.model.Effect
import com.dok.editor.model.EffectType
import com.dok.editor.model.TimelineClip
import com.dok.editor.model.TransitionConfig
import com.dok.editor.model.TransitionType
import com.dok.editor.ui.theme.DokAccent
import com.dok.editor.ui.theme.DokBackground
import com.dok.editor.ui.theme.DokDivider
import com.dok.editor.ui.theme.DokPrimaryText
import com.dok.editor.ui.theme.DokSecondaryText
import com.dok.editor.ui.theme.DokSurface
import com.dok.editor.ui.theme.DokSurfaceElevated
import com.dok.editor.ui.theme.DokWarning

@Composable
fun InspectorPanel(
    selectedClip: TimelineClip?,
    onCommand: (EditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("TRANSFORM", "COLOR", "AUDIO", "EFFECTS")

    Surface(
        color = DokSurface,
        modifier = modifier.testTag("inspector_panel")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Inspector Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DokSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedClip != null) "INSPECTOR: ${selectedClip.mediaName}" else "INSPECTOR (NO CLIP SELECTED)",
                    color = DokPrimaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // Tab Navigation
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = DokSurfaceElevated,
                contentColor = DokAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = DokAccent
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTabIndex == index) DokAccent else DokSecondaryText
                            )
                        },
                        modifier = Modifier.testTag("tab_${title.lowercase()}")
                    )
                }
            }

            Divider(color = DokDivider, thickness = 1.dp)

            if (selectedClip == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a clip on the timeline to edit properties.",
                        color = DokSecondaryText,
                        fontSize = 13.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    when (selectedTabIndex) {
                        0 -> TransformTabContent(clip = selectedClip, onCommand = onCommand)
                        1 -> ColorTabContent(clip = selectedClip, onCommand = onCommand)
                        2 -> AudioTabContent(clip = selectedClip, onCommand = onCommand)
                        3 -> EffectsTabContent(clip = selectedClip, onCommand = onCommand)
                    }
                }
            }
        }
    }
}

@Composable
fun TransformTabContent(
    clip: TimelineClip,
    onCommand: (EditorCommand) -> Unit
) {
    val transform = clip.transform

    Column {
        Text("2D Transform", color = DokAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        InspectorSlider(
            label = "Position X",
            value = transform.positionX,
            range = -1.0f..1.0f,
            format = "%+.3f",
            testTag = "slider_position_x",
            onValueChange = { value ->
                onCommand(EditorCommand.UpdateClipTransform(clip.id, transform.copy(positionX = value)))
            }
        )

        InspectorSlider(
            label = "Position Y",
            value = transform.positionY,
            range = -1.0f..1.0f,
            format = "%+.3f",
            testTag = "slider_position_y",
            onValueChange = { value ->
                onCommand(EditorCommand.UpdateClipTransform(clip.id, transform.copy(positionY = value)))
            }
        )

        // Scale X / Y
        InspectorSlider(
            label = "Scale",
            value = transform.scaleX,
            range = 0.1f..3.0f,
            format = "%.2fx",
            testTag = "slider_scale",
            onValueChange = { newScale ->
                onCommand(
                    EditorCommand.UpdateClipTransform(
                        clip.id,
                        transform.copy(scaleX = newScale, scaleY = newScale)
                    )
                )
            }
        )

        // Rotation
        InspectorSlider(
            label = "Rotation",
            value = transform.rotationDegrees,
            range = -180f..180f,
            format = "%.1f°",
            testTag = "slider_rotation",
            onValueChange = { newRot ->
                onCommand(
                    EditorCommand.UpdateClipTransform(
                        clip.id,
                        transform.copy(rotationDegrees = newRot)
                    )
                )
            }
        )

        // Opacity
        InspectorSlider(
            label = "Opacity",
            value = transform.opacity,
            range = 0f..1f,
            format = "%.2f",
            testTag = "slider_opacity",
            onValueChange = { newOpacity ->
                onCommand(
                    EditorCommand.UpdateClipTransform(
                        clip.id,
                        transform.copy(opacity = newOpacity)
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text("Playback", color = DokAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        InspectorSlider(
            label = "Speed",
            value = clip.speed,
            range = 0.1f..8.0f,
            format = "%.2fx",
            testTag = "slider_speed",
            onValueChange = { value ->
                onCommand(EditorCommand.ChangeClipSpeed(clip.id, value))
            }
        )
    }
}

@Composable
fun ColorTabContent(
    clip: TimelineClip,
    onCommand: (EditorCommand) -> Unit
) {
    val color = clip.colorParams

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Color Grading (DaVinci Primary)", color = DokAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = {
                    onCommand(EditorCommand.UpdateColorGrading(clip.id, ColorGradingParams()))
                },
                modifier = Modifier.size(28.dp).testTag("color_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Color",
                    tint = DokSecondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Brightness
        InspectorSlider(
            label = "Brightness",
            value = color.brightness,
            range = -1.0f..1.0f,
            format = "%+.2f",
            testTag = "slider_brightness",
            onValueChange = { b ->
                onCommand(EditorCommand.UpdateColorGrading(clip.id, color.copy(brightness = b)))
            }
        )

        // Contrast
        InspectorSlider(
            label = "Contrast",
            value = color.contrast,
            range = 0.0f..2.0f,
            format = "%.2f",
            testTag = "slider_contrast",
            onValueChange = { c ->
                onCommand(EditorCommand.UpdateColorGrading(clip.id, color.copy(contrast = c)))
            }
        )

        // Saturation
        InspectorSlider(
            label = "Saturation",
            value = color.saturation,
            range = 0.0f..2.0f,
            format = "%.2f",
            testTag = "slider_saturation",
            onValueChange = { s ->
                onCommand(EditorCommand.UpdateColorGrading(clip.id, color.copy(saturation = s)))
            }
        )

        // Temperature
        InspectorSlider(
            label = "Temperature",
            value = color.temperature,
            range = -1.0f..1.0f,
            format = "%+.2f",
            testTag = "slider_temperature",
            onValueChange = { t ->
                onCommand(EditorCommand.UpdateColorGrading(clip.id, color.copy(temperature = t)))
            }
        )

        // Vignette
        InspectorSlider(
            label = "Vignette",
            value = color.vignette,
            range = 0.0f..1.0f,
            format = "%.2f",
            testTag = "slider_vignette",
            onValueChange = { v ->
                onCommand(EditorCommand.UpdateColorGrading(clip.id, color.copy(vignette = v)))
            }
        )
    }
}

@Composable
fun AudioTabContent(
    clip: TimelineClip,
    onCommand: (EditorCommand) -> Unit
) {
    Column {
        Text("Audio Mixer Channel", color = DokAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        // Volume dB
        InspectorSlider(
            label = "Volume",
            value = clip.volumeDb,
            range = -30.0f..12.0f,
            format = "%+.1f dB",
            testTag = "slider_volume",
            onValueChange = { v ->
                onCommand(EditorCommand.UpdateClipAudio(clip.id, v, clip.pan))
            }
        )

        // Pan
        InspectorSlider(
            label = "Pan (L <-> R)",
            value = clip.pan,
            range = -1.0f..1.0f,
            format = "%+.2f",
            testTag = "slider_pan",
            onValueChange = { p -> onCommand(EditorCommand.UpdateClipAudio(clip.id, clip.volumeDb, p)) }
        )
    }
}

@Composable
fun EffectsTabContent(
    clip: TimelineClip,
    onCommand: (EditorCommand) -> Unit
) {
    Column {
        Text("Parametric Video Effects", color = DokAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // Add Effect Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val availableEffects = listOf(
                "Shake" to EffectType.SHAKE,
                "RGB Split" to EffectType.RGB_SPLIT,
                "Glitch" to EffectType.GLITCH,
                "Flash" to EffectType.FLASH,
                "Zoom" to EffectType.ZOOM
            )

            availableEffects.forEach { (label, type) ->
                Box(
                    modifier = Modifier
                        .background(DokSurfaceElevated, RoundedCornerShape(4.dp))
                        .clickable {
                            onCommand(
                                EditorCommand.AddParametricEffect(
                                    clip.id,
                                    Effect.ParametricEffect(effectType = type, intensity = 0.5f)
                                )
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("add_effect_${label.lowercase().replace(' ', '_')}")
                ) {
                    Text(text = "+ $label", color = DokPrimaryText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active Effects List
        if (clip.effects.isEmpty()) {
            Text("No effects added to this clip yet.", color = DokSecondaryText, fontSize = 12.sp)
        } else {
            clip.effects.forEach { effect ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DokSurfaceElevated, RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = effect.effectType.name,
                            color = DokPrimaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Intensity: ${(effect.intensity * 100).toInt()}%",
                            color = DokSecondaryText,
                            fontSize = 10.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            onCommand(EditorCommand.RemoveEffect(clip.id, effect.id))
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Effect",
                            tint = DokWarning,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider(color = DokDivider, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        // Transitions Controls
        Text("Transitions", color = DokAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Transition In", color = DokPrimaryText, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("None", "Crossfade", "Dip Black", "Wipe").forEach { name ->
                    val type = when (name) {
                        "Crossfade" -> TransitionType.CROSSFADE
                        "Dip Black" -> TransitionType.DIP_TO_BLACK
                        "Wipe" -> TransitionType.WIPE_LEFT
                        else -> null
                    }
                    val isSelected = (clip.transitionIn == null && type == null) ||
                            (clip.transitionIn?.type == type)

                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) DokAccent else DokDivider,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                onCommand(
                                    EditorCommand.SetClipTransitionIn(
                                        clip.id,
                                        type?.let { TransitionConfig(type = it) }
                                    )
                                )
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = name,
                            color = if (isSelected) Color.Black else DokPrimaryText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InspectorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = DokSecondaryText, fontSize = 11.sp)
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    input.toFloatOrNull()?.let { onValueChange(it.coerceIn(range.start, range.endInclusive)) }
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = DokPrimaryText, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(92.dp).height(38.dp).testTag(testTag + "_numeric")
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { next -> text = next.toString(); onValueChange(next) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = DokAccent, activeTrackColor = DokAccent, inactiveTrackColor = DokDivider),
            modifier = Modifier.testTag(testTag)
        )
        Text(format.format(value), color = DokSecondaryText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}
