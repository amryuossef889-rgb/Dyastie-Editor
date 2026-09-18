package com.example.dyastie.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.dyastie.model.TimelineClip
import com.example.dyastie.ui.bin.MediaBin
import com.example.dyastie.ui.dialogs.AudioSyncDialog
import com.example.dyastie.ui.dialogs.ClipContextMenu
import com.example.dyastie.ui.dialogs.ExportDialog
import com.example.dyastie.ui.inspector.InspectorPanel
import com.example.dyastie.ui.preview.PreviewMonitor
import com.example.ui.theme.NleBackground
import com.example.dyastie.ui.timeline.TimelineComponent
import com.example.dyastie.ui.topbar.TopMenuBar
import com.example.dyastie.viewmodel.DyastieViewModel
import com.example.dyastie.viewmodel.EditTool

@Composable
fun DyastieEditorScreen(
    viewModel: DyastieViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val project by viewModel.project.collectAsState()
    val audioSyncResult by viewModel.audioSyncResult.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var contextMenuClip by remember { mutableStateOf<Pair<TimelineClip, Offset>?>(null) }

    // Media Picker Launcher for Video/Audio/Images
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMedia(it, context) }
    }

    // Physical USB Keyboard Shortcuts focus handler
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NleBackground)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Spacebar -> {
                            viewModel.playback.togglePlayPause(project)
                            true
                        }
                        Key.DirectionLeft -> {
                            viewModel.playback.stepFrame(project, -1)
                            true
                        }
                        Key.DirectionRight -> {
                            viewModel.playback.stepFrame(project, 1)
                            true
                        }
                        Key.S -> {
                            viewModel.splitAtPlayhead()
                            true
                        }
                        Key.V -> {
                            viewModel.setTool(EditTool.SELECT)
                            true
                        }
                        Key.B -> {
                            viewModel.setTool(EditTool.BLADE)
                            true
                        }
                        Key.Delete, Key.Backspace -> {
                            viewModel.deleteSelectedClips()
                            true
                        }
                        Key.Z -> {
                            if (event.isCtrlPressed) {
                                viewModel.undo()
                                true
                            } else false
                        }
                        Key.Y -> {
                            if (event.isCtrlPressed) {
                                viewModel.redo()
                                true
                            } else false
                        }
                        Key.C -> {
                            if (event.isCtrlPressed) {
                                viewModel.copySelected()
                                true
                            } else false
                        }
                        Key.X -> {
                            if (event.isCtrlPressed) {
                                viewModel.cutSelected()
                                true
                            } else false
                        }
                        Key.V -> {
                            if (event.isCtrlPressed) {
                                viewModel.pasteAtPlayhead()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Menu & Toolbar
            TopMenuBar(
                viewModel = viewModel,
                onOpenExport = { showExportDialog = true }
            )

            // 2. Middle Workstation Panes (Media Bin + Preview Monitor + Inspector)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f)
            ) {
                // Left: Media Bin
                MediaBin(
                    viewModel = viewModel,
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight(),
                    onImportRequest = { mediaPickerLauncher.launch("*/*") }
                )

                // Middle: Preview Monitor
                PreviewMonitor(
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                // Right: Inspector Panel
                InspectorPanel(
                    viewModel = viewModel,
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                )
            }

            // 3. Bottom Multi-Track Timeline
            TimelineComponent(
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.95f),
                onClipContextMenu = { clip, screenPos ->
                    contextMenuClip = clip to screenPos
                }
            )
        }

        // Dialogs
        audioSyncResult?.let { syncResult ->
            AudioSyncDialog(
                syncResult = syncResult,
                onApply = { offsetMs -> viewModel.applyAudioSync(offsetMs) },
                onCancel = { viewModel.cancelAudioSync() }
            )
        }

        if (showExportDialog || exportStatus.isExporting || exportStatus.exportedFile != null) {
            ExportDialog(
                exportStatus = exportStatus,
                onStartExport = { config -> viewModel.startExport(config) },
                onDismiss = {
                    showExportDialog = false
                    viewModel.dismissExport()
                }
            )
        }

        contextMenuClip?.let { (clip, pos) ->
            ClipContextMenu(
                clip = clip,
                screenPosition = pos,
                viewModel = viewModel,
                onDismiss = { contextMenuClip = null }
            )
        }
    }
}
