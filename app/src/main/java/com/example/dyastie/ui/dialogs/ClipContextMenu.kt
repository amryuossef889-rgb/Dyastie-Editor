package com.example.dyastie.ui.dialogs

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.dyastie.model.TimelineClip
import com.example.ui.theme.*
import com.example.dyastie.viewmodel.DyastieViewModel

@Composable
fun ClipContextMenu(
    clip: TimelineClip,
    screenPosition: Offset,
    viewModel: DyastieViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = NleSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NleBorder),
            modifier = Modifier.width(260.dp).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = clip.name,
                    fontSize = 11.sp,
                    color = NleAccentCyan,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    maxLines = 1
                )
                Divider(color = NleBorder, thickness = 1.dp)

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

                Divider(color = NleBorder, thickness = 1.dp)

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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NleTextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 12.sp, color = NleTextPrimary)
        }
        if (shortcut.isNotEmpty()) {
            Text(text = shortcut, fontSize = 10.sp, color = NleTextSecondary)
        }
    }
}
