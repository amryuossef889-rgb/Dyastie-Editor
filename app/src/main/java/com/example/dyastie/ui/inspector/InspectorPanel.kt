package com.example.dyastie.ui.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dyastie.model.EffectType
import com.example.ui.theme.*
import com.example.dyastie.viewmodel.DyastieViewModel

enum class InspectorTab {
    TRANSFORM,
    AUDIO,
    COLOR,
    EFFECTS,
    SPEED,
    TEXT
}

@Composable
fun InspectorPanel(
    viewModel: DyastieViewModel,
    modifier: Modifier = Modifier
) {
    val project by viewModel.project.collectAsState()
    val selectedClipIds by viewModel.selectedClipIds.collectAsState()

    val selectedClip = remember(project.clips, selectedClipIds) {
        project.clips.find { selectedClipIds.contains(it.id) }
    }

    var currentTab by remember { mutableStateOf(InspectorTab.TRANSFORM) }

    Column(
        modifier = modifier
            .background(NleSurface)
            .border(width = 1.dp, color = NleBorder)
    ) {
        // Inspector Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(NleSurfaceVariant)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "INSPECTOR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NleTextSecondary
            )
            if (selectedClip != null) {
                Text(
                    text = selectedClip.name,
                    fontSize = 10.sp,
                    color = NleAccentCyan,
                    maxLines = 1
                )
            }
        }

        if (selectedClip == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No clip selected.\nSelect a clip on the timeline to edit properties.",
                    fontSize = 12.sp,
                    color = NleTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            return
        }

        // Tab Row
        ScrollableTabRow(
            selectedTabIndex = currentTab.ordinal,
            containerColor = NleSurface,
            contentColor = NleAccentCyan,
            edgePadding = 4.dp,
            modifier = Modifier.height(32.dp)
        ) {
            InspectorTab.values().forEach { tab ->
                Tab(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    text = {
                        Text(
                            text = tab.name,
                            fontSize = 9.sp,
                            fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Divider(color = NleBorder, thickness = 1.dp)

        // Inspector Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (currentTab) {
                InspectorTab.TRANSFORM -> {
                    val tr = selectedClip.transform
                    Text("Scale: ${(tr.scaleX * 100).toInt()}%", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = tr.scaleX,
                        onValueChange = { s ->
                            viewModel.updateClipTransform(selectedClip.id, tr.copy(scaleX = s, scaleY = s))
                        },
                        valueRange = 0.2f..3.0f,
                        colors = SliderDefaults.colors(thumbColor = NleAccentCyan, activeTrackColor = NleAccentCyan)
                    )

                    Text("Position X: ${tr.posX.toInt()}px", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = tr.posX,
                        onValueChange = { x ->
                            viewModel.updateClipTransform(selectedClip.id, tr.copy(posX = x))
                        },
                        valueRange = -500f..500f
                    )

                    Text("Position Y: ${tr.posY.toInt()}px", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = tr.posY,
                        onValueChange = { y ->
                            viewModel.updateClipTransform(selectedClip.id, tr.copy(posY = y))
                        },
                        valueRange = -500f..500f
                    )

                    Text("Rotation: ${tr.rotationDeg.toInt()}°", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = tr.rotationDeg,
                        onValueChange = { r ->
                            viewModel.updateClipTransform(selectedClip.id, tr.copy(rotationDeg = r))
                        },
                        valueRange = -180f..180f
                    )

                    Text("Opacity: ${(tr.opacity * 100).toInt()}%", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = tr.opacity,
                        onValueChange = { o ->
                            viewModel.updateClipTransform(selectedClip.id, tr.copy(opacity = o))
                        },
                        valueRange = 0f..1f
                    )

                    Button(
                        onClick = {
                            viewModel.updateClipTransform(selectedClip.id, com.example.dyastie.model.VideoTransform())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NleSurfaceVariant),
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    ) {
                        Text("Reset Transform", fontSize = 11.sp, color = NleTextPrimary)
                    }
                }

                InspectorTab.AUDIO -> {
                    Text("Volume: ${(selectedClip.volume * 100).toInt()}%", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = selectedClip.volume,
                        onValueChange = { v ->
                            viewModel.updateClipAudio(selectedClip.id, v, selectedClip.pan, selectedClip.fadeInMs, selectedClip.fadeOutMs, selectedClip.gainDb)
                        },
                        valueRange = 0f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = NleWaveform, activeTrackColor = NleWaveform)
                    )

                    Text("Stereo Pan: ${(selectedClip.pan * 100).toInt()}%", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = selectedClip.pan,
                        onValueChange = { p ->
                            viewModel.updateClipAudio(selectedClip.id, selectedClip.volume, p, selectedClip.fadeInMs, selectedClip.fadeOutMs, selectedClip.gainDb)
                        },
                        valueRange = -1f..1f
                    )

                    Text("Audio Gain: ${selectedClip.gainDb.toInt()} dB", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = selectedClip.gainDb,
                        onValueChange = { g ->
                            viewModel.updateClipAudio(selectedClip.id, selectedClip.volume, selectedClip.pan, selectedClip.fadeInMs, selectedClip.fadeOutMs, g)
                        },
                        valueRange = -12f..12f
                    )

                    Text("Fade In: ${selectedClip.fadeInMs} ms", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = selectedClip.fadeInMs.toFloat(),
                        onValueChange = { f ->
                            viewModel.updateClipAudio(selectedClip.id, selectedClip.volume, selectedClip.pan, f.toLong(), selectedClip.fadeOutMs, selectedClip.gainDb)
                        },
                        valueRange = 0f..3000f
                    )

                    Text("Fade Out: ${selectedClip.fadeOutMs} ms", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = selectedClip.fadeOutMs.toFloat(),
                        onValueChange = { f ->
                            viewModel.updateClipAudio(selectedClip.id, selectedClip.volume, selectedClip.pan, selectedClip.fadeInMs, f.toLong(), selectedClip.gainDb)
                        },
                        valueRange = 0f..3000f
                    )
                }

                InspectorTab.COLOR -> {
                    val cg = selectedClip.colorGrading

                    Text("Brightness: ${(cg.brightness * 100).toInt()}%", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = cg.brightness,
                        onValueChange = { b ->
                            viewModel.updateClipColorGrading(selectedClip.id, cg.copy(brightness = b))
                        },
                        valueRange = -0.5f..0.5f
                    )

                    Text("Contrast: ${(cg.contrast * 100).toInt()}%", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = cg.contrast,
                        onValueChange = { c ->
                            viewModel.updateClipColorGrading(selectedClip.id, cg.copy(contrast = c))
                        },
                        valueRange = 0.5f..2.0f
                    )

                    Text("Saturation: ${(cg.saturation * 100).toInt()}%", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = cg.saturation,
                        onValueChange = { s ->
                            viewModel.updateClipColorGrading(selectedClip.id, cg.copy(saturation = s))
                        },
                        valueRange = 0.0f..2.5f
                    )

                    Text("Temperature: ${(cg.temperature * 100).toInt()}%", fontSize = 11.sp, color = NleTextPrimary)
                    Slider(
                        value = cg.temperature,
                        onValueChange = { t ->
                            viewModel.updateClipColorGrading(selectedClip.id, cg.copy(temperature = t))
                        },
                        valueRange = -1.0f..1.0f
                    )
                }

                InspectorTab.EFFECTS -> {
                    Text("Gaming Effects for Clip", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NleAccentPurple)
                    EffectType.values().forEach { fxType ->
                        val isEnabled = selectedClip.effects.any { it.type == fxType && it.isEnabled }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(fxType.displayName, fontSize = 11.sp, color = NleTextPrimary)
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { viewModel.toggleClipEffect(selectedClip.id, fxType) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }

                InspectorTab.SPEED -> {
                    Text("Current Speed: ${selectedClip.speed}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NleTextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0.25f, 0.5f, 1.0f, 2.0f, 4.0f).forEach { spd ->
                            Button(
                                onClick = { viewModel.updateClipSpeed(selectedClip.id, spd) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedClip.speed == spd) NleAccentCyan else NleSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).height(28.dp)
                            ) {
                                Text(
                                    text = "${spd}x",
                                    fontSize = 10.sp,
                                    color = if (selectedClip.speed == spd) Color.Black else NleTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                InspectorTab.TEXT -> {
                    val overlay = selectedClip.textOverlay
                    if (overlay == null) {
                        Text(
                            text = "This clip does not have a text overlay.\nAdd a Text clip from the Bin to edit typography.",
                            fontSize = 11.sp,
                            color = NleTextSecondary
                        )
                    } else {
                        var textContent by remember(overlay.id) { mutableStateOf(overlay.text) }
                        OutlinedTextField(
                            value = textContent,
                            onValueChange = {
                                textContent = it
                                viewModel.updateClipTextOverlay(selectedClip.id, overlay.copy(text = it))
                            },
                            label = { Text("Overlay Text") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Font Size: ${overlay.fontSizeSp.toInt()}sp", fontSize = 11.sp, color = NleTextPrimary)
                        Slider(
                            value = overlay.fontSizeSp,
                            onValueChange = { s ->
                                viewModel.updateClipTextOverlay(selectedClip.id, overlay.copy(fontSizeSp = s))
                            },
                            valueRange = 16f..72f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Bold Style", fontSize = 11.sp, color = NleTextPrimary)
                            Switch(
                                checked = overlay.isBold,
                                onCheckedChange = { b ->
                                    viewModel.updateClipTextOverlay(selectedClip.id, overlay.copy(isBold = b))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
