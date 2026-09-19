package com.example.dyastie.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.dyastie.export.ExportConfig
import com.example.ui.theme.*
import com.example.dyastie.viewmodel.ExportStatus

@Composable
fun ExportDialog(
    exportStatus: ExportStatus,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedPreset by remember { mutableStateOf(ExportConfig.YOUTUBE_1080P) }

    Dialog(onDismissRequest = {
        if (!exportStatus.isExporting) onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NleSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NleBorder),
            modifier = Modifier.width(460.dp).padding(16.dp).testTag("export_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Export",
                            tint = NleAccentCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EXPORT VIDEO (H.264 + AAC)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NleTextPrimary
                        )
                    }

                    if (!exportStatus.isExporting) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = NleTextSecondary)
                        }
                    }
                }

                if (!exportStatus.isExporting && exportStatus.exportedFile == null) {
                    Text(
                        text = "Hardware-accelerated MP4 encoder (Video + Mixed Audio tracks):",
                        fontSize = 12.sp,
                        color = NleTextSecondary
                    )

                    listOf(
                        ExportConfig.YOUTUBE_1080P to "1920x1080 • 30fps • 8 Mbps (Standard Gaming Quality)",
                        ExportConfig.YOUTUBE_720P to "1280x720 • 30fps • 4 Mbps (Fast / Tab A7 Optimized)",
                        ExportConfig.YOUTUBE_SHORTS to "1080x1920 • Vertical • 6 Mbps (YouTube Shorts / TikTok)"
                    ).forEach { (preset, desc) ->
                        val isSelected = selectedPreset.title == preset.title
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPreset = preset }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) NleAccentCyan else NleBorder,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = NleSurfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = preset.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) NleAccentCyan else NleTextPrimary
                                )
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = NleTextSecondary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = NleTextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onStartExport(selectedPreset) },
                            colors = ButtonDefaults.buttonColors(containerColor = NleAccentCyan),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("start_export_button")
                        ) {
                            Text("Start Export", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (exportStatus.isExporting) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Encoding Hardware MP4 (Video + Audio)...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NleTextPrimary
                        )

                        LinearProgressIndicator(
                            progress = { exportStatus.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = NleAccentCyan,
                            trackColor = NleBorder
                        )

                        Text(
                            text = "${(exportStatus.progress * 100).toInt()}% • ${exportStatus.statusText}",
                            fontSize = 12.sp,
                            color = NleTextSecondary
                        )

                        OutlinedButton(
                            onClick = onCancelExport,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Cancel Export", color = Color(0xFFFF5252))
                        }
                    }
                } else {
                    // Completed
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Export Completed Successfully!",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NleWaveform
                        )
                        Text(
                            text = "Saved to Movies:\n${exportStatus.exportedFile?.absolutePath}",
                            fontSize = 12.sp,
                            color = NleTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = NleAccentCyan),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
