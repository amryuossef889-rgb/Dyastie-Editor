package com.example.dyastie.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.example.dyastie.model.TimelineClip
import com.example.dyastie.viewmodel.DyastieViewModel
import com.example.ui.theme.*

@Composable
fun ClipContextMenu(
    clip: TimelineClip,
    screenPosition: Offset,
    viewModel: DyastieViewModel,
    onDismiss: () -> Unit
) {
    val mediaItem = viewModel.project.value.mediaItems.find { it.id == clip.mediaId }

    Popup(
        onDismissRequest = onDismiss,
        alignment = Alignment.TopStart
    ) {
        Card(
            modifier = Modifier
                .offset(x = (screenPosition.x / 2.5f).dp, y = (screenPosition.y / 2.5f).dp)
                .width(220.dp)
                .border(1.dp, NleBorder, RoundedCornerShape(6.dp)),
            colors = CardDefaults.cardColors(containerColor = NleSurfaceVariant),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                ContextMenuItem(Icons.Default.ContentCut, "Cut", "Ctrl+X") {
                    viewModel.cutSelected()
                    onDismiss()
                }
                ContextMenuItem(Icons.Default.ContentCopy, "Copy", "Ctrl+C") {
                    viewModel.copySelected()
                    onDismiss()
                }
                ContextMenuItem(Icons.Default.CallSplit, "Split at Playhead", "S") {
                    viewModel.splitAtPlayhead()
                    onDismiss()
                }
                ContextMenuItem(Icons.Default.Delete, "Delete", "Del") {
                    viewModel.deleteSelectedClips()
                    onDismiss()
                }
                ContextMenuItem(Icons.Default.FastForward, "Ripple Delete", "Shift+Del") {
                    viewModel.rippleDeleteSelectedClips()
                    onDismiss()
                }

                HorizontalDivider(color = NleBorder, thickness = 1.dp)

                if (clip.linkedClipId != null) {
                    ContextMenuItem(Icons.Default.LinkOff, "Unlink Video / Audio", "Ctrl+L") {
                        viewModel.unlinkClip(clip.id)
                        onDismiss()
                    }
                } else {
                    ContextMenuItem(Icons.Default.Link, "Link Video / Audio", "Ctrl+L") {
                        viewModel.toggleLinkSelected()
                        onDismiss()
                    }
                }

                if (clip.groupId != null) {
                    ContextMenuItem(Icons.Default.FolderOff, "Ungroup", "Ctrl+G") {
                        viewModel.ungroupSelectedClips()
                        onDismiss()
                    }
                } else {
                    ContextMenuItem(Icons.Default.Folder, "Group Clips", "Ctrl+G") {
                        viewModel.groupSelectedClips()
                        onDismiss()
                    }
                }

                if (!clip.isVideoTrack || clip.linkedClipId != null) {
                    ContextMenuItem(Icons.Default.Sync, "Audio Sync Assistant", "") {
                        viewModel.launchAudioSyncAssistant()
                        onDismiss()
                    }
                }

                if (mediaItem != null && (mediaItem.isOffline || !mediaItem.isSample)) {
                    HorizontalDivider(color = NleBorder, thickness = 1.dp)
                    ContextMenuItem(Icons.Default.BrokenImage, "Relink Media File", "") {
                        viewModel.requestRelink(mediaItem)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    shortcut: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = NleTextSecondary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 11.sp, color = NleTextPrimary)
        }
        if (shortcut.isNotEmpty()) {
            Text(text = shortcut, fontSize = 9.sp, color = NleTextSecondary)
        }
    }
}
