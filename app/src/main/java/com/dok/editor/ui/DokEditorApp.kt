package com.dok.editor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember\nimport androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dok.editor.command.EditorCommand
import com.dok.editor.ui.components.DeliverPanel
import com.dok.editor.ui.components.InspectorPanel
import com.dok.editor.ui.components.TimelinePanel
import com.dok.editor.ui.components.ViewerPanel
import com.dok.editor.ui.theme.DokAccent
import com.dok.editor.ui.theme.DokBackground
import com.dok.editor.ui.theme.DokDivider
import com.dok.editor.ui.theme.DokSecondaryText
import com.dok.editor.ui.theme.DokSurfaceElevated
import com.dok.editor.viewmodel.EditorPanel
import com.dok.editor.viewmodel.EditorViewModel

@Composable
fun DokEditorApp(
    viewModel: EditorViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val project by viewModel.project.collectAsState()
    val currentTimeUs by viewModel.currentTimeUs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val selectedClipId by viewModel.selectedClipId.collectAsState()
    val isSnappingEnabled by viewModel.isSnappingEnabled.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val activePanel by viewModel.activePanel.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val inPointUs by viewModel.inPointUs.collectAsState()
    val outPointUs by viewModel.outPointUs.collectAsState()
    val exportUiState by viewModel.exportUiState.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val selectedExportPreset by viewModel.selectedExportPreset.collectAsState()

    val focusRequester = remember { FocusRequester() }\n    var showProjectSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Media picker for importing gaming clips
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            viewModel.dispatch(
                EditorCommand.ImportMediaClip(
                    uri = it.toString(),
                    name = it.lastPathSegment ?: "Imported_Clip.mp4",
                    durationUs = 5_000_000L
                )
            )
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed
                    when (event.key) {
                        Key.Spacebar -> {
                            viewModel.dispatch(EditorCommand.TogglePlayPause)
                            true
                        }
                        Key.J -> {
                            viewModel.dispatch(EditorCommand.ShuttleReverse)
                            true
                        }
                        Key.K -> {
                            viewModel.dispatch(EditorCommand.ShuttleStop)
                            true
                        }
                        Key.L -> {
                            viewModel.dispatch(EditorCommand.ShuttleForward)
                            true
                        }
                        Key.I -> {
                            viewModel.dispatch(EditorCommand.SetInPoint(currentTimeUs))
                            true
                        }
                        Key.O -> {
                            viewModel.dispatch(EditorCommand.SetOutPoint(currentTimeUs))
                            true
                        }
                        Key.B -> {
                            if (ctrlOrMeta) {
                                viewModel.dispatch(EditorCommand.SplitClipAtPlayhead)
                                true
                            } else false
                        }
                        Key.Delete, Key.Backspace -> {
                            if (event.isShiftPressed) {
                                viewModel.dispatch(EditorCommand.RippleDeleteSelectedClip)
                            } else {
                                viewModel.dispatch(EditorCommand.DeleteSelectedClip)
                            }
                            true
                        }
                        Key.Z -> {
                            if (ctrlOrMeta) {
                                if (event.isShiftPressed) {
                                    viewModel.dispatch(EditorCommand.Redo)
                                } else {
                                    viewModel.dispatch(EditorCommand.Undo)
                                }
                                true
                            } else false
                        }
                        Key.Y -> {
                            if (ctrlOrMeta) {
                                viewModel.dispatch(EditorCommand.Redo)
                                true
                            } else false
                        }
                        Key.DirectionLeft -> {
                            viewModel.dispatch(EditorCommand.StepFrames(-1))
                            true
                        }
                        Key.DirectionRight -> {
                            viewModel.dispatch(EditorCommand.StepFrames(1))
                            true
                        }
                        Key.Equals, Key.Plus -> {
                            viewModel.dispatch(EditorCommand.ZoomTimeline(0.25f))
                            true
                        }
                        Key.Minus -> {
                            viewModel.dispatch(EditorCommand.ZoomTimeline(-0.25f))
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .testTag("dok_editor_root")
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(DokBackground)
                .padding(innerPadding)
        ) {
            val isLandscape = maxWidth > 600.dp

            if (isLandscape) {
                // Desktop NLE Landscape arrangement (Phase 9)
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Navigation Rail
                    NavigationRail(
                        containerColor = DokSurfaceElevated,
                        contentColor = DokAccent,
                        modifier = Modifier.width(64.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        NavigationRailItem(
                            selected = activePanel == EditorPanel.TIMELINE,
                            onClick = { viewModel.setActivePanel(EditorPanel.TIMELINE) },
                            icon = { Icon(Icons.Default.ViewTimeline, contentDescription = "Timeline") },
                            label = { Text("Timeline", fontSize = 10.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = DokAccent,
                                unselectedIconColor = DokSecondaryText,
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_rail_timeline")
                        )
                        NavigationRailItem(
                            selected = activePanel == EditorPanel.INSPECTOR,
                            onClick = { viewModel.setActivePanel(EditorPanel.INSPECTOR) },
                            icon = { Icon(Icons.Default.Tune, contentDescription = "Inspector") },
                            label = { Text("Inspector", fontSize = 10.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = DokAccent,
                                unselectedIconColor = DokSecondaryText,
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_rail_inspector")
                        )
                        NavigationRailItem(
                            selected = activePanel == EditorPanel.DELIVER,
                            onClick = { viewModel.setActivePanel(EditorPanel.DELIVER) },
                            icon = { Icon(Icons.Default.FileDownload, contentDescription = "Deliver") },
                            label = { Text("Deliver", fontSize = 10.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = DokAccent,
                                unselectedIconColor = DokSecondaryText,
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_rail_deliver")
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        NavigationRailItem(
                            selected = false,
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            icon = { Icon(Icons.Default.Add, contentDescription = "Import Media") },
                            label = { Text("Import", fontSize = 10.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                unselectedIconColor = DokSecondaryText
                            ),
                            modifier = Modifier.testTag("nav_rail_import")
                        )
                    }

                    Divider(color = DokDivider, modifier = Modifier.fillMaxHeight().width(1.dp))

                    // Main Content: Left side (Docked Inspector/Deliver) + Right side (Viewer + Timeline)
                    Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (activePanel != EditorPanel.TIMELINE) {
                            Box(modifier = Modifier.width(320.dp).fillMaxHeight()) {
                                when (activePanel) {
                                    EditorPanel.INSPECTOR, EditorPanel.COLOR, EditorPanel.EFFECTS -> {
                                        InspectorPanel(
                                            selectedClip = viewModel.getSelectedClip(),
                                            onCommand = viewModel::dispatch
                                        )
                                    }
                                    EditorPanel.DELIVER -> {
                                        DeliverPanel(
                                            selectedPreset = selectedExportPreset,
                                            exportUiState = exportUiState,
                                            exportProgress = exportProgress,
                                            onSelectPreset = viewModel::setSelectedExportPreset,
                                            onCommand = viewModel::dispatch
                                        )
                                    }
                                    else -> {}
                                }
                            }
                            Divider(color = DokDivider, modifier = Modifier.fillMaxHeight().width(1.dp))
                        }

                        // Right Column: Master Viewer + Full-width Timeline
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            ViewerPanel(
                                project = project,
                                currentTimeUs = currentTimeUs,
                                isPlaying = isPlaying,
                                playbackSpeed = playbackSpeed,
                                onCommand = viewModel::dispatch,
                                modifier = Modifier.fillMaxWidth().weight(1.1f)
                            )

                            Divider(color = DokDivider, thickness = 1.dp)

                            TimelinePanel(
                                project = project,
                                currentTimeUs = currentTimeUs,
                                selectedClipId = selectedClipId,
                                isSnappingEnabled = isSnappingEnabled,
                                zoomLevel = zoomLevel,
                                canUndo = canUndo,
                                canRedo = canRedo,
                                inPointUs = inPointUs,
                                outPointUs = outPointUs,
                                onCommand = viewModel::dispatch,
                                modifier = Modifier.fillMaxWidth().weight(0.9f)
                            )
                        }
                    }
                }
            } else {
                // Portrait Layout (Phase 8)
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top: Master Viewer & Transport
                    ViewerPanel(
                        project = project,
                        currentTimeUs = currentTimeUs,
                        isPlaying = isPlaying,
                        playbackSpeed = playbackSpeed,
                        onCommand = viewModel::dispatch,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Divider(color = DokDivider, thickness = 1.dp)

                    // Middle: Active Panel (Timeline, Inspector, or Deliver)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (activePanel) {
                            EditorPanel.TIMELINE -> {
                                TimelinePanel(
                                    project = project,
                                    currentTimeUs = currentTimeUs,
                                    selectedClipId = selectedClipId,
                                    isSnappingEnabled = isSnappingEnabled,
                                    zoomLevel = zoomLevel,
                                    canUndo = canUndo,
                                    canRedo = canRedo,
                                    inPointUs = inPointUs,
                                    outPointUs = outPointUs,
                                    onCommand = viewModel::dispatch,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            EditorPanel.INSPECTOR, EditorPanel.COLOR, EditorPanel.EFFECTS -> {
                                InspectorPanel(
                                    selectedClip = viewModel.getSelectedClip(),
                                    onCommand = viewModel::dispatch,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            EditorPanel.DELIVER -> {
                                DeliverPanel(
                                    selectedPreset = selectedExportPreset,
                                    exportUiState = exportUiState,
                                    exportProgress = exportProgress,
                                    onSelectPreset = viewModel::setSelectedExportPreset,
                                    onCommand = viewModel::dispatch,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Divider(color = DokDivider, thickness = 1.dp)

                    // Bottom Navigation Bar
                    NavigationBar(
                        containerColor = DokSurfaceElevated,
                        contentColor = DokAccent,
                        tonalElevation = 4.dp,
                        modifier = Modifier.height(56.dp).testTag("bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = activePanel == EditorPanel.TIMELINE,
                            onClick = { viewModel.setActivePanel(EditorPanel.TIMELINE) },
                            icon = { Icon(Icons.Default.ViewTimeline, contentDescription = "Timeline") },
                            label = { Text("Timeline", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = DokAccent,
                                selectedTextColor = DokAccent,
                                unselectedIconColor = DokSecondaryText,
                                unselectedTextColor = DokSecondaryText,
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_item_timeline")
                        )
                        NavigationBarItem(
                            selected = activePanel == EditorPanel.INSPECTOR,
                            onClick = { viewModel.setActivePanel(EditorPanel.INSPECTOR) },
                            icon = { Icon(Icons.Default.Tune, contentDescription = "Inspector") },
                            label = { Text("Inspector", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = DokAccent,
                                selectedTextColor = DokAccent,
                                unselectedIconColor = DokSecondaryText,
                                unselectedTextColor = DokSecondaryText,
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_item_inspector")
                        )
                        NavigationBarItem(
                            selected = activePanel == EditorPanel.DELIVER,
                            onClick = { viewModel.setActivePanel(EditorPanel.DELIVER) },
                            icon = { Icon(Icons.Default.FileDownload, contentDescription = "Deliver") },
                            label = { Text("Deliver", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = DokAccent,
                                selectedTextColor = DokAccent,
                                unselectedIconColor = DokSecondaryText,
                                unselectedTextColor = DokSecondaryText,
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_item_deliver")
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            icon = { Icon(Icons.Default.Add, contentDescription = "Import Media") },
                            label = { Text("Import", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                unselectedIconColor = DokSecondaryText,
                                unselectedTextColor = DokSecondaryText
                            ),
                            modifier = Modifier.testTag("nav_item_import")
                        )
                    }
                }
            }
        }
    }
}\n    if (showProjectSettings) {\n        ProjectSettingsDialog(project = project, onDismiss = { showProjectSettings = false }, onCommand = viewModel::dispatch)\n    }\n
