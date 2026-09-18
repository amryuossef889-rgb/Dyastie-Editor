package com.example.dyastie.ui.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.dyastie.viewmodel.DyastieViewModel
import com.example.dyastie.viewmodel.EditTool

@Composable
fun TopMenuBar(
    viewModel: DyastieViewModel,
    onOpenExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val project by viewModel.project.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val snappingEnabled by viewModel.snappingEnabled.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val zoom by viewModel.timelineZoom.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(NleSurface)
            .border(width = 1.dp, color = NleBorder)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Branding & Project Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NleAccentCyan)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "DYASTIE",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = Color.Black
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = project.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = NleTextPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = { viewModel.saveProjectNow() },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save Project",
                    tint = NleTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Center: Primary NLE Editing Tools (Select, Blade, Split, Ripple, Link, Audio Sync, Snapping, Zoom)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ToolButton(
                icon = Icons.Default.NearMe,
                label = "Select [V]",
                isActive = activeTool == EditTool.SELECT,
                onClick = { viewModel.setTool(EditTool.SELECT) },
                testTag = "tool_select"
            )

            ToolButton(
                icon = Icons.Default.ContentCut,
                label = "Blade [B]",
                isActive = activeTool == EditTool.BLADE,
                onClick = { viewModel.setTool(EditTool.BLADE) },
                testTag = "tool_blade"
            )

            IconButton(
                onClick = { viewModel.splitAtPlayhead() },
                modifier = Modifier.size(32.dp).testTag("split_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CallSplit,
                    contentDescription = "Split at Playhead",
                    tint = NleAccentCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { viewModel.deleteSelectedClips() },
                modifier = Modifier.size(32.dp).testTag("delete_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = NleTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { viewModel.rippleDeleteSelectedClips() },
                modifier = Modifier.size(32.dp).testTag("ripple_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Ripple Delete",
                    tint = NleTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { viewModel.toggleLinkSelected() },
                modifier = Modifier.size(32.dp).testTag("link_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Link/Unlink",
                    tint = NleAccentCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Audio Sync Assistant Button
            Button(
                onClick = { viewModel.launchAudioSyncAssistant() },
                colors = ButtonDefaults.buttonColors(containerColor = NleSurfaceVariant),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp).testTag("audio_sync_assistant_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = NleWaveform,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sync Audio", fontSize = 11.sp, color = NleWaveform, fontWeight = FontWeight.Bold)
            }

            // Snapping toggle
            IconButton(
                onClick = { viewModel.toggleSnapping() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (snappingEnabled) Icons.Default.Adjust else Icons.Default.PanoramaFishEye,
                    contentDescription = "Toggle Snapping",
                    tint = if (snappingEnabled) NleAccentCyan else NleTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Zoom Controls
            IconButton(
                onClick = { viewModel.setZoom(zoom * 0.8f) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Zoom Out",
                    tint = NleTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = { viewModel.setZoom(zoom * 1.25f) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom In",
                    tint = NleTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Undo / Redo
            IconButton(
                onClick = { viewModel.undo() },
                enabled = viewModel.canUndo,
                modifier = Modifier.size(30.dp).testTag("undo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = if (viewModel.canUndo) NleTextPrimary else NleTextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = { viewModel.redo() },
                enabled = viewModel.canRedo,
                modifier = Modifier.size(30.dp).testTag("redo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = if (viewModel.canRedo) NleTextPrimary else NleTextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Right: Status Message & Export Button
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = statusMessage,
                fontSize = 10.sp,
                color = NleTextSecondary,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 140.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onOpenExport,
                colors = ButtonDefaults.buttonColors(containerColor = NleAccentCyan),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).testTag("export_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "EXPORT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) NleAccentCyan.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isActive) NleAccentCyan else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) NleAccentCyan else NleTextPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}
