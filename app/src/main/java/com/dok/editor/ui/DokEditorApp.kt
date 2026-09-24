package com.dok.editor.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dok.editor.command.EditorCommand
import com.dok.editor.ui.components.*
import com.dok.editor.ui.theme.*
import com.dok.editor.viewmodel.*

@Composable
fun DokEditorApp(viewModel: EditorViewModel = viewModel(), modifier: Modifier = Modifier) {
    val project by viewModel.project.collectAsState()
    val currentTimeUs by viewModel.currentTimeUs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val selectedClipId by viewModel.selectedClipId.collectAsState()
    val snapping by viewModel.isSnappingEnabled.collectAsState()
    val zoom by viewModel.zoomLevel.collectAsState()
    val panel by viewModel.activePanel.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val inPoint by viewModel.inPointUs.collectAsState()
    val outPoint by viewModel.outPointUs.collectAsState()
    val exportState by viewModel.exportUiState.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val preset by viewModel.selectedExportPreset.collectAsState()
    var settings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focus = remember { FocusRequester() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            viewModel.dispatch(EditorCommand.ImportMediaClip(it.toString(), it.lastPathSegment ?: "Video", 1_000_000L))
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            val cm = e.isCtrlPressed || e.isMetaPressed
            when (e.key) {
                Key.Spacebar -> { viewModel.dispatch(EditorCommand.TogglePlayPause); true }
                Key.J -> { viewModel.dispatch(EditorCommand.ShuttleReverse); true }
                Key.K -> { viewModel.dispatch(EditorCommand.ShuttleStop); true }
                Key.L -> { viewModel.dispatch(EditorCommand.ShuttleForward); true }
                Key.LeftArrow -> { viewModel.dispatch(EditorCommand.StepFrames(-1)); true }
                Key.RightArrow -> { viewModel.dispatch(EditorCommand.StepFrames(1)); true }
                Key.Equals, Key.Plus -> { viewModel.dispatch(EditorCommand.ZoomTimeline(.25f)); true }
                Key.Minus -> { viewModel.dispatch(EditorCommand.ZoomTimeline(-.25f)); true }
                Key.B -> if (cm) { viewModel.dispatch(EditorCommand.SplitClipAtPlayhead); true } else false
                Key.Delete, Key.Backspace -> { viewModel.dispatch(if (e.isShiftPressed) EditorCommand.RippleDeleteSelectedClip else EditorCommand.DeleteSelectedClip); true }
                Key.Z -> if (cm) { viewModel.dispatch(if (e.isShiftPressed) EditorCommand.Redo else EditorCommand.Undo); true } else false
                Key.Y -> if (cm) { viewModel.dispatch(EditorCommand.Redo); true } else false
                else -> false
            }
        }.testTag("dok_editor_root")
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().background(DokBackground).padding(padding)) {
            val landscape = maxWidth >= 700.dp
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = DokSurfaceElevated, modifier = Modifier.width(70.dp)) {
                        Spacer(Modifier.height(8.dp))
                        NavigationRailItem(panel == EditorPanel.TIMELINE, { viewModel.setActivePanel(EditorPanel.TIMELINE) }, { Icon(Icons.Default.ViewTimeline, "Timeline") }, label={Text("Edit",fontSize=9.sp)})
                        NavigationRailItem(panel == EditorPanel.INSPECTOR, { viewModel.setActivePanel(EditorPanel.INSPECTOR) }, { Icon(Icons.Default.Tune, "Inspector") }, label={Text("Inspect",fontSize=9.sp)})
                        NavigationRailItem(panel == EditorPanel.DELIVER, { viewModel.setActivePanel(EditorPanel.DELIVER) }, { Icon(Icons.Default.FileDownload, "Deliver") }, label={Text("Deliver",fontSize=9.sp)})
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(false, { picker.launch(arrayOf("video/*")) }, { Icon(Icons.Default.AddCircle, "Import") }, label={Text("Import",fontSize=9.sp)})
                    }
                    VerticalDivider(color=DokDivider)
                    if (panel == EditorPanel.TIMELINE) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            ViewerPanel(project,currentTimeUs,isPlaying,playbackSpeed,viewModel::dispatch,Modifier.weight(1.15f).fillMaxWidth(),{settings=true})
                            HorizontalDivider(color=DokDivider)
                            TimelinePanel(project,currentTimeUs,selectedClipId,snapping,zoom,canUndo,canRedo,inPoint,outPoint,viewModel::dispatch,Modifier.weight(.85f).fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.weight(1f).fillMaxHeight()) {
                            Box(Modifier.width(330.dp).fillMaxHeight()) {
                                when(panel) {
                                    EditorPanel.INSPECTOR, EditorPanel.COLOR, EditorPanel.EFFECTS -> InspectorPanel(viewModel.getSelectedClip(),viewModel::dispatch)
                                    EditorPanel.DELIVER -> DeliverPanel(preset,exportState,exportProgress,viewModel::setSelectedExportPreset,viewModel::dispatch)
                                    else -> {}
                                }
                            }
                            VerticalDivider(color=DokDivider)
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                ViewerPanel(project,currentTimeUs,isPlaying,playbackSpeed,viewModel::dispatch,Modifier.weight(1f).fillMaxWidth(),{settings=true})
                                HorizontalDivider(color=DokDivider)
                                TimelinePanel(project,currentTimeUs,selectedClipId,snapping,zoom,canUndo,canRedo,inPoint,outPoint,viewModel::dispatch,Modifier.weight(.65f).fillMaxWidth())
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    ViewerPanel(project,currentTimeUs,isPlaying,playbackSpeed,viewModel::dispatch,Modifier.weight(1.05f).fillMaxWidth(),{settings=true})
                    HorizontalDivider(color=DokDivider)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when(panel) {
                            EditorPanel.TIMELINE -> TimelinePanel(project,currentTimeUs,selectedClipId,snapping,zoom,canUndo,canRedo,inPoint,outPoint,viewModel::dispatch,Modifier.fillMaxSize())
                            EditorPanel.INSPECTOR, EditorPanel.COLOR, EditorPanel.EFFECTS -> InspectorPanel(viewModel.getSelectedClip(),viewModel::dispatch)
                            EditorPanel.DELIVER -> DeliverPanel(preset,exportState,exportProgress,viewModel::setSelectedExportPreset,viewModel::dispatch)
                        }
                    }
                    NavigationBar(containerColor=DokSurfaceElevated, modifier=Modifier.height(58.dp)) {
                        NavigationBarItem(panel==EditorPanel.TIMELINE,{viewModel.setActivePanel(EditorPanel.TIMELINE)},{Icon(Icons.Default.ViewTimeline,"Edit")},label={Text("Edit")})
                        NavigationBarItem(panel==EditorPanel.INSPECTOR,{viewModel.setActivePanel(EditorPanel.INSPECTOR)},{Icon(Icons.Default.Tune,"Inspector")},label={Text("Inspect")})
                        NavigationBarItem(panel==EditorPanel.DELIVER,{viewModel.setActivePanel(EditorPanel.DELIVER)},{Icon(Icons.Default.FileDownload,"Deliver")},label={Text("Export")})
                        NavigationBarItem(false,{picker.launch(arrayOf("video/*"))},{Icon(Icons.Default.AddCircle,"Import")},label={Text("Import")})
                    }
                }
            }
        }
    }
    if (settings) ProjectSettingsDialog(project,{settings=false},viewModel::dispatch)
}
