package com.example.dyastie.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
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
import com.example.dyastie.audio.AudioSyncResult
import com.example.ui.theme.*

@Composable
fun AudioSyncDialog(
    syncResult: AudioSyncResult,
    onApply: (Long) -> Unit,
    onCancel: () -> Unit
) {
    var manualOffset by remember { mutableStateOf(syncResult.suggestedOffsetMs.toFloat()) }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NleSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NleBorder),
            modifier = Modifier.width(420.dp).padding(16.dp).testTag("audio_sync_dialog")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Audio Sync",
                        tint = NleAccentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AUDIO SYNC ASSISTANT",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = NleTextPrimary
                    )
                }

                Text(
                    text = "Reference: ${syncResult.referenceClipName}\nTarget: ${syncResult.targetClipName}",
                    fontSize = 12.sp,
                    color = NleTextSecondary
                )

                // Sync Result Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NleSurfaceVariant, shape = RoundedCornerShape(8.dp))
                        .border(1.dp, NleAccentCyan.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Detected Alignment Offset",
                            fontSize = 11.sp,
                            color = NleTextSecondary
                        )
                        Text(
                            text = "${if (manualOffset >= 0) "+" else ""}${manualOffset.toInt()} ms",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = NleAccentCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Confidence: ${(syncResult.confidence * 100).toInt()}% based on transient cross-correlation",
                            fontSize = 11.sp,
                            color = NleWaveform
                        )
                    }
                }

                // Fine-tuning adjustment slider
                Column {
                    Text(
                        text = "Fine-Tune Manual Offset (ms):",
                        fontSize = 11.sp,
                        color = NleTextSecondary
                    )
                    Slider(
                        value = manualOffset,
                        onValueChange = { manualOffset = it },
                        valueRange = (syncResult.suggestedOffsetMs - 1000f)..(syncResult.suggestedOffsetMs + 1000f),
                        colors = SliderDefaults.colors(thumbColor = NleAccentCyan, activeTrackColor = NleAccentCyan)
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = NleTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onApply(manualOffset.toLong()) },
                        colors = ButtonDefaults.buttonColors(containerColor = NleAccentCyan),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("apply_sync_button")
                    ) {
                        Text("Apply Offset", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
