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
import com.dok.editor.input.ShortcutStore
import com.dok.editor.ui.theme.*
import com.dok.editor.viewmodel.*

@Composable
fun DokEditorApp(viewModel: EditorViewModel = viewModel(), modifier: Modifier = Modifier) {
    val project by viewModel.project.collectAsState()
    val currentTimeUs by viewModel.currentTimeUs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val selectedClipId by viewModel.selectedClipId.collectAsState()
    val mediaAssets by viewModel.mediaAssets.collectAsState()
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
    val renderJobs by viewModel.renderQueueState.collectAsState()
    var settings by remember { mutableStateOf(false) }
    var shortcutSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val effectLibrary = remember(context) { com.dok.editor.engine.effects.ExternalEffectLibrary(context) }
    val focus = remember { FocusRequester() }

    val srtImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importSrtSubtitles(it) }
    }
    val srtExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri: Uri? ->
        uri?.let { viewModel.writeSrtToUri(it) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            viewModel.dispatch(EditorCommand.ImportMediaClip(it.toString(), it.lastPathSegment ?: "Media", 1_000_000L))
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            val keyName = when (e.key) {
                Key.Spacebar -> "Space"
                Key.J -> "J"
                Key.K -> "K"
                Key.L -> "L"
                Key.B -> "B"
                Key.Z -> "Z"
                Key.Y -> "Y"
                Key.DirectionLeft -> "Left"
                Key.DirectionRight -> "Right"
                Key.Delete -> "Delete"
                Key.Backspace -> "Backspace"
                Key.Plus, Key.Equals -> "+"
                Key.Minus -> "-"
                else -> null
            }
            val modifierPrefix = buildString {
                if (e.isCtrlPressed) append("Ctrl+")
                if (e.isAltPressed) append("Alt+")
                if (e.isShiftPressed) append("Shift+")
            }
            if (keyName != null) {
                ShortcutStore.commandFor(context, modifierPrefix + keyName)?.let {
                    viewModel.dispatch(it)
                    return@onKeyEvent true
                }
            }
            false
        }.testTag("dok_editor_root")
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().background(DokBackground).padding(padding)) {
            val landscape = maxWidth >= 700.dp
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = DokSurfaceElevated, modifier = Modifier.width(70.dp)) {
                        Spacer(Modifier.height(8.dp))
                        NavigationRailItem(panel == EditorPanel.MEDIA_POOL, { viewModel.setActivePanel(EditorPanel.MEDIA_POOL) }, { Icon(Icons.Default.VideoLibrary, "Media Pool") }, label={Text("Media",fontSize=9.sp)})
                        NavigationRailItem(panel == EditorPanel.TIMELINE, { viewModel.setActivePanel(EditorPanel.TIMELINE) }, { Icon(Icons.Default.ViewTimeline, "Timeline") }, label={Text("Edit",fontSize=9.sp)})
                        NavigationRailItem(panel == EditorPanel.INSPECTOR, { viewModel.setActivePanel(EditorPanel.INSPECTOR) }, { Icon(Icons.Default.Tune, "Inspector") }, label={Text("Inspect",fontSize=9.sp)})
                        NavigationRailItem(panel == EditorPanel.DELIVER, { viewModel.setActivePanel(EditorPanel.DELIVER) }, { Icon(Icons.Default.FileDownload, "Deliver") }, label={Text("Deliver",fontSize=9.sp)})
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(false, { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }, { Icon(Icons.Default.AddCircle, "Import") }, label={Text("Import",fontSize=9.sp)})
                    }
                    VerticalDivider(color=DokDivider)
                    if (panel == EditorPanel.MEDIA_POOL) {
                        Row(Modifier.weight(1f).fillMaxHeight()) {
                            MediaPoolPanel(mediaAssets, viewModel::dispatch, Modifier.width(360.dp).fillMaxHeight())
                            VerticalDivider(color=DokDivider)
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                ViewerPanel(project,currentTimeUs,isPlaying,playbackSpeed,viewModel::dispatch,Modifier.weight(1.15f).fillMaxWidth(),{settings=true})
                                HorizontalDivider(color=DokDivider)
                                TimelinePanel(project,currentTimeUs,selectedClipId,snapping,zoom,canUndo,canRedo,inPoint,outPoint,viewModel::dispatch,Modifier.weight(.85f).fillMaxWidth())
                            }
                        }
                    } else if (panel == EditorPanel.TIMELINE) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            ViewerPanel(project,currentTimeUs,isPlaying,playbackSpeed,viewModel::dispatch,Modifier.weight(1.15f).fillMaxWidth(),{settings=true})
                            HorizontalDivider(color=DokDivider)
                            TimelinePanel(project,currentTimeUs,selectedClipId,snapping,zoom,canUndo,canRedo,inPoint,outPoint,viewModel::dispatch,Modifier.weight(.85f).fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.weight(1f).fillMaxHeight()) {
                            Box(Modifier.width(330.dp).fillMaxHeight()) {
                                when(panel) {
                                    EditorPanel.MEDIA_POOL -> MediaPoolPanel(mediaAssets, viewModel::dispatch)
                                    EditorPanel.INSPECTOR, EditorPanel.COLOR -> InspectorPanel(viewModel.getSelectedClip(),viewModel::dispatch)
                                    EditorPanel.EFFECTS -> EffectsLibraryPanel(effectLibrary, viewModel::applyExternalEffectAsset, viewModel::insertExternalAudioEffect, viewModel::addExternalEffectAsset, viewModel::removeExternalEffectAsset)
                                    EditorPanel.DELIVER -> DeliverPanel(
                                        preset, exportState, exportProgress,
                                        viewModel::setSelectedExportPreset, viewModel::dispatch,
                                        onImportSrt = { srtImportPicker.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) },
                                        onExportSrt = { srtExportPicker.launch("DOK-subtitles.srt") },
                                        renderJobs = renderJobs,
                                        onQueueExport = { viewModel.enqueueCurrentExport(preset) },
                                        onStartQueue = viewModel::startQueuedExports,
                                        onCancelQueue = viewModel::cancelQueuedExport,
                                        onRemoveQueue = viewModel::removeQueuedExport
                                    )
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
                            EditorPanel.MEDIA_POOL -> MediaPoolPanel(mediaAssets, viewModel::dispatch)
                            EditorPanel.TIMELINE -> TimelinePanel(project,currentTimeUs,selectedClipId,snapping,zoom,canUndo,canRedo,inPoint,outPoint,viewModel::dispatch,Modifier.fillMaxSize())
                            EditorPanel.INSPECTOR, EditorPanel.COLOR -> InspectorPanel(viewModel.getSelectedClip(),viewModel::dispatch)
                            EditorPanel.EFFECTS -> EffectsLibraryPanel(effectLibrary, viewModel::applyExternalEffectAsset, viewModel::insertExternalAudioEffect, viewModel::addExternalEffectAsset, viewModel::removeExternalEffectAsset)
                            EditorPanel.DELIVER -> DeliverPanel(
                                preset, exportState, exportProgress,
                                viewModel::setSelectedExportPreset, viewModel::dispatch,
                                onImportSrt = { srtImportPicker.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) },
                                onExportSrt = { srtExportPicker.launch("DOK-subtitles.srt") }
                            )
                        }
                    }
                    NavigationBar(containerColor=DokSurfaceElevated, modifier=Modifier.height(58.dp)) {
                        NavigationBarItem(panel==EditorPanel.MEDIA_POOL,{viewModel.setActivePanel(EditorPanel.MEDIA_POOL)},{Icon(Icons.Default.VideoLibrary,"Media")},label={Text("Media")})
                        NavigationBarItem(panel==EditorPanel.TIMELINE,{viewModel.setActivePanel(EditorPanel.TIMELINE)},{Icon(Icons.Default.ViewTimeline,"Edit")},label={Text("Edit")})
                        NavigationBarItem(panel==EditorPanel.INSPECTOR,{viewModel.setActivePanel(EditorPanel.INSPECTOR)},{Icon(Icons.Default.Tune,"Inspector")},label={Text("Inspect")})
                        NavigationBarItem(panel==EditorPanel.DELIVER,{viewModel.setActivePanel(EditorPanel.DELIVER)},{Icon(Icons.Default.FileDownload,"Deliver")},label={Text("Export")})
                        NavigationBarItem(false,{picker.launch(arrayOf("video/*", "image/*", "audio/*"))},{Icon(Icons.Default.AddCircle,"Import")},label={Text("Import")})
                    }
                }
            }
        }
    }
    if (settings) ProjectSettingsDialog(project,{settings=false},viewModel::dispatch,{shortcutSettings=true})
    if (shortcutSettings) ShortcutSettingsDialog(context,{shortcutSettings=false})
}
