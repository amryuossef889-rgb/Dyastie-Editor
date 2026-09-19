package com.example.dyastie.ui.bin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dyastie.model.EffectType
import com.example.dyastie.model.MediaItem
import com.example.dyastie.model.MediaType
import com.example.dyastie.viewmodel.DyastieViewModel
import com.example.ui.theme.*

enum class BinTab {
    MEDIA,
    AUDIO,
    IMAGES,
    EFFECTS,
    TRANSITIONS,
    TEXT
}

@Composable
fun MediaBin(
    viewModel: DyastieViewModel,
    modifier: Modifier = Modifier,
    onImportVisual: () -> Unit,
    onImportAudio: () -> Unit,
    onRelinkMedia: (MediaItem) -> Unit
) {
    var currentTab by remember { mutableStateOf(BinTab.MEDIA) }
    val project by viewModel.project.collectAsState()
    val selectedClipIds by viewModel.selectedClipIds.collectAsState()

    Column(
        modifier = modifier
            .background(NleSurface)
            .border(width = 1.dp, color = NleBorder)
    ) {
        // Bin Tabs Header
        ScrollableTabRow(
            selectedTabIndex = currentTab.ordinal,
            containerColor = NleSurfaceVariant,
            contentColor = NleAccentCyan,
            edgePadding = 4.dp,
            modifier = Modifier.height(36.dp)
        ) {
            BinTab.values().forEach { tab ->
                Tab(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    text = {
                        Text(
                            text = tab.name,
                            fontSize = 10.sp,
                            fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Tab Action Toolbar (Import Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${currentTab.name} BIN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NleTextSecondary
            )

            if (currentTab == BinTab.MEDIA || currentTab == BinTab.AUDIO || currentTab == BinTab.IMAGES) {
                Button(
                    onClick = {
                        if (currentTab == BinTab.AUDIO) onImportAudio() else onImportVisual()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NleAccentCyan),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(28.dp).testTag("import_media_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Import",
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (currentTab == BinTab.AUDIO) "Import Audio" else "Import Media",
                        fontSize = 11.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HorizontalDivider(color = NleBorder, thickness = 1.dp)

        // Content by Tab
        Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            when (currentTab) {
                BinTab.MEDIA, BinTab.AUDIO, BinTab.IMAGES -> {
                    val filtered = project.mediaItems.filter { item ->
                        when (currentTab) {
                            BinTab.MEDIA -> item.type == MediaType.VIDEO
                            BinTab.AUDIO -> item.type == MediaType.AUDIO
                            BinTab.IMAGES -> item.type == MediaType.IMAGE
                            else -> true
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No ${currentTab.name.lowercase()} imported.\nTap 'Import' to browse files.",
                                color = NleTextSecondary,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(filtered) { mediaItem ->
                                MediaBinCard(
                                    item = mediaItem,
                                    onAddToTimeline = { viewModel.addMediaToTimeline(mediaItem) },
                                    onRelink = { onRelinkMedia(mediaItem) }
                                )
                            }
                        }
                    }
                }

                BinTab.TEXT -> {
                    val textPresets = listOf(
                        Triple("CLUTCH", "1v4 CLUTCH MOMENT!", 0xFFFFD700L),
                        Triple("LEVEL UP", "LEVEL UP +1000 XP!", 0xFF00FF88L),
                        Triple("WIN", "VICTORY ROYALE!", 0xFF00D2FFL),
                        Triple("FAIL", "EPIC FAIL / RIP!", 0xFFFF3344L),
                        Triple("WARNING", "WARNING: BOSS ENCOUNTER", 0xFFFF8800L),
                        Triple("IMPACT", "HEADSHOT!", 0xFFFF0055L),
                        Triple("REACTION", "WHAT WAS THAT?!", 0xFFFFFFFFL),
                        Triple("SUBTITLE", "Type your dialogue here...", 0xFFEEEEEEL)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(textPresets) { (preset, defaultText, colorLong) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addTextPresetToTimeline(
                                            presetName = preset,
                                            text = defaultText,
                                            textColor = colorLong,
                                            strokeColor = 0xFF000000L
                                        )
                                    },
                                colors = CardDefaults.cardColors(containerColor = NleSurfaceVariant),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = preset,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(colorLong)
                                        )
                                        Text(
                                            text = defaultText,
                                            fontSize = 10.sp,
                                            color = NleTextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.AddCircle,
                                        contentDescription = "Add Text Preset",
                                        tint = NleAccentCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                BinTab.EFFECTS -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(EffectType.values()) { effectType ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val firstSelected = selectedClipIds.firstOrNull()
                                        if (firstSelected != null) {
                                            viewModel.toggleClipEffect(firstSelected, effectType)
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = NleSurfaceVariant),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = effectType.displayName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NleTextPrimary
                                        )
                                        Text(
                                            text = effectType.category,
                                            fontSize = 10.sp,
                                            color = NleAccentPurple
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = "Apply Effect",
                                        tint = NleAccentPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                BinTab.TRANSITIONS -> {
                    val transitions = listOf("Cross Dissolve", "Dip to Black", "Whip Pan", "Glitch Transition", "Impact Flash")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(transitions) { tr ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = NleSurfaceVariant),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = NleAccentCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = tr, fontSize = 12.sp, color = NleTextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaBinCard(
    item: MediaItem,
    onAddToTimeline: () -> Unit,
    onRelink: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NleSurfaceVariant),
        shape = RoundedCornerShape(6.dp),
        border = if (item.isOffline) BorderStroke(1.dp, NleErrorRed) else null
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (item.isOffline) NleErrorRed.copy(alpha = 0.3f)
                        else if (item.type == MediaType.VIDEO) NleVideoClip else NleAudioClip
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        item.isOffline -> Icons.Default.BrokenImage
                        item.type == MediaType.VIDEO -> Icons.Default.Videocam
                        item.type == MediaType.AUDIO -> Icons.Default.Audiotrack
                        item.type == MediaType.IMAGE -> Icons.Default.Image
                        else -> Icons.Default.Videocam
                    },
                    contentDescription = null,
                    tint = if (item.isOffline) NleErrorRed else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isOffline) NleErrorRed else NleTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isOffline) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(NleErrorRed, RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("OFFLINE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Relink",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NleAccentCyan,
                            modifier = Modifier
                                .clickable { onRelink() }
                                .padding(2.dp)
                                .testTag("relink_button_${item.id}")
                        )
                    }
                } else {
                    val info = when (item.type) {
                        MediaType.VIDEO -> "${item.durationMs / 1000}s • ${item.width}x${item.height} • ${item.fps.toInt()}fps"
                        MediaType.AUDIO -> "${item.durationMs / 1000}s • ${item.channelCount}ch • ${item.sampleRate}Hz"
                        MediaType.IMAGE -> "Still Image • ${item.width}x${item.height}"
                    }
                    Text(
                        text = info,
                        fontSize = 10.sp,
                        color = NleTextSecondary
                    )
                }
            }

            IconButton(
                onClick = onAddToTimeline,
                modifier = Modifier.size(30.dp).testTag("add_to_timeline_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = "Add to timeline",
                    tint = if (item.isOffline) NleTextSecondary else NleAccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
