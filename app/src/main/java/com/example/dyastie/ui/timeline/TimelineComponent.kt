package com.example.dyastie.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dyastie.model.TimelineClip
import com.example.dyastie.model.Track
import com.example.ui.theme.*
import com.example.dyastie.viewmodel.DyastieViewModel
import com.example.dyastie.viewmodel.EditTool
import kotlin.math.max
import kotlin.math.roundToLong

@Composable
fun TimelineComponent(
    viewModel: DyastieViewModel,
    modifier: Modifier = Modifier,
    onClipContextMenu: (TimelineClip, Offset) -> Unit
) {
    val project by viewModel.project.collectAsState()
    val selectedClipIds by viewModel.selectedClipIds.collectAsState()
    val playheadMs by viewModel.playback.playheadPositionMs.collectAsState()
    val zoom by viewModel.timelineZoom.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val snappingEnabled by viewModel.snappingEnabled.collectAsState()

    // Base scale: 100 pixels per second at zoom 1.0f -> 0.1 px per ms
    val pxPerMs = 0.1f * zoom
    val totalTimelineWidthDp = ((project.durationMs * pxPerMs) + 600f).dp

    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NleBackground)
    ) {
        // Timeline Header / Ruler & Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(NleSurfaceVariant)
                .border(width = 1.dp, color = NleBorder),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track header corner label
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
                    .background(NleSurface)
                    .border(width = 1.dp, color = NleBorder)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "TRACKS (${project.tracks.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NleTextSecondary
                )
            }

            // Timecode Ruler
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val clickedMs = (offset.x / pxPerMs).toLong().coerceAtLeast(0L)
                            viewModel.playback.seekTo(clickedMs, project)
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .width(totalTimelineWidthDp)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val targetMs = (change.position.x / pxPerMs).toLong().coerceAtLeast(0L)
                                viewModel.playback.seekTo(targetMs, project)
                            }
                        }
                ) {
                    val stepMs = when {
                        zoom < 0.5f -> 5000L
                        zoom < 1.5f -> 1000L
                        zoom < 3.0f -> 500L
                        else -> 100L
                    }

                    var t = 0L
                    while (t <= project.durationMs + 10_000L) {
                        val x = t * pxPerMs
                        val isSecond = (t % 1000L == 0L)
                        val tickHeight = if (isSecond) size.height * 0.5f else size.height * 0.25f

                        drawLine(
                            color = if (isSecond) Color.LightGray else Color.DarkGray,
                            start = Offset(x, size.height - tickHeight),
                            end = Offset(x, size.height),
                            strokeWidth = if (isSecond) 1.5f else 1f
                        )
                        t += stepMs
                    }

                    // Draw Markers
                    for (marker in project.markers) {
                        val mx = marker.timeMs * pxPerMs
                        drawCircle(
                            color = Color(marker.color),
                            radius = 6f,
                            center = Offset(mx, 8f)
                        )
                    }

                    // Ruler Playhead Needle Top
                    val px = playheadMs * pxPerMs
                    drawLine(
                        color = NlePlayhead,
                        start = Offset(px, 0f),
                        end = Offset(px, size.height),
                        strokeWidth = 3f
                    )
                }
            }
        }

        // Tracks Lanes Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Left Track Headers
            Column(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
                    .verticalScroll(verticalScrollState)
                    .background(NleSurface)
                    .border(width = 1.dp, color = NleBorder)
            ) {
                for (track in project.tracks) {
                    TrackHeaderItem(
                        track = track,
                        onToggleMute = { viewModel.toggleTrackMute(track.id) },
                        onToggleSolo = { viewModel.toggleTrackSolo(track.id) },
                        onToggleLock = { viewModel.toggleTrackLock(track.id) },
                        onToggleHide = { viewModel.toggleTrackHide(track.id) }
                    )
                }
            }

            // Right Multi-Track Grid
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
                    .verticalScroll(verticalScrollState)
                    .background(NleBackground)
            ) {
                // Background Track Lanes
                Column(modifier = Modifier.width(totalTimelineWidthDp)) {
                    for (track in project.tracks) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(track.heightDp.dp)
                                .border(width = 0.5.dp, color = NleBorder.copy(alpha = 0.5f))
                        )
                    }
                }

                // Render Clips
                Box(modifier = Modifier.width(totalTimelineWidthDp)) {
                    // Calculate Y offset for each track
                    var currentY = 0
                    for (track in project.tracks) {
                        val trackHeight = track.heightDp
                        val clipsOnTrack = project.clips.filter { it.trackId == track.id }

                        for (clip in clipsOnTrack) {
                            val clipX = (clip.timelineStartMs * pxPerMs).dp
                            val clipWidth = max((clip.timelineDurationMs * pxPerMs).toInt(), 24).dp
                            val isSelected = selectedClipIds.contains(clip.id)
                            val waveform = if (!clip.isVideoTrack) viewModel.getWaveformForClip(clip) else null

                            ClipView(
                                clip = clip,
                                isSelected = isSelected,
                                waveform = waveform,
                                modifier = Modifier
                                    .offset(x = clipX, y = currentY.dp)
                                    .width(clipWidth)
                                    .height(trackHeight.dp - 4.dp)
                                    .padding(vertical = 2.dp),
                                onSelect = { isMulti ->
                                    if (activeTool == EditTool.BLADE) {
                                        viewModel.playback.seekTo(clip.timelineStartMs + (clip.timelineDurationMs / 2), project)
                                        viewModel.splitAtPlayhead()
                                    } else {
                                        viewModel.selectClip(clip.id, isMulti)
                                    }
                                },
                                onTrimStart = { deltaPx ->
                                    val deltaMs = (deltaPx / pxPerMs).toLong()
                                    val newStart = clip.timelineStartMs + deltaMs
                                    val newDuration = clip.timelineDurationMs - deltaMs
                                    viewModel.trimClip(clip.id, newStart, newDuration, isTrimStart = true)
                                },
                                onTrimEnd = { deltaPx ->
                                    val deltaMs = (deltaPx / pxPerMs).toLong()
                                    val newDuration = clip.timelineDurationMs + deltaMs
                                    viewModel.trimClip(clip.id, clip.timelineStartMs, newDuration, isTrimStart = false)
                                },
                                onDragMove = { deltaPx ->
                                    val deltaMs = (deltaPx / pxPerMs).toLong()
                                    viewModel.moveClip(clip.id, deltaMs)
                                },
                                onContextMenu = { screenPos ->
                                    onClipContextMenu(clip, screenPos)
                                }
                            )
                        }
                        currentY += trackHeight
                    }
                }

                // Red Playhead Line spanning all lanes
                val playheadX = (playheadMs * pxPerMs).dp
                Box(
                    modifier = Modifier
                        .offset(x = playheadX - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(NlePlayhead)
                )
            }
        }
    }
}

@Composable
fun TrackHeaderItem(
    track: Track,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleHide: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(track.heightDp.dp)
            .border(width = 0.5.dp, color = NleBorder)
            .background(if (track.isLocked) NleSurfaceVariant else NleSurface)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (track.isVideo) Icons.Default.Videocam else Icons.Default.Audiotrack,
                contentDescription = null,
                tint = if (track.isVideo) NleAccentCyan else NleWaveform,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = track.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = NleTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mute Button
            Text(
                text = "M",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (track.isMuted) Color.Red else NleTextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (track.isMuted) Color.Red.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onToggleMute() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            // Solo Button (for Audio)
            if (!track.isVideo) {
                Text(
                    text = "S",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (track.isSolo) Color.Yellow else NleTextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (track.isSolo) Color.Yellow.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onToggleSolo() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            // Lock Button
            Icon(
                imageVector = if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Lock Track",
                tint = if (track.isLocked) Color.Red else NleTextSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onToggleLock() }
            )
        }
    }
}

@Composable
fun ClipView(
    clip: TimelineClip,
    isSelected: Boolean,
    waveform: FloatArray?,
    modifier: Modifier = Modifier,
    onSelect: (Boolean) -> Unit,
    onTrimStart: (Float) -> Unit,
    onTrimEnd: (Float) -> Unit,
    onDragMove: (Float) -> Unit,
    onContextMenu: (Offset) -> Unit
) {
    val clipColor = when {
        clip.textOverlay != null -> NleTextClip
        clip.isVideoTrack -> NleVideoClip
        else -> NleAudioClip
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(clipColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) NleAccentCyan else Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(clip.id) {
                detectTapGestures(
                    onTap = { onSelect(false) },
                    onDoubleTap = { onSelect(true) },
                    onLongPress = { offset -> onContextMenu(offset) }
                )
            }
            .pointerInput(clip.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragMove(dragAmount.x)
                }
            }
            .testTag("clip_${clip.id}")
    ) {
        // Draw Waveform if Audio Clip
        if (!clip.isVideoTrack && waveform != null && waveform.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val midY = size.height / 2f
                val stepX = size.width / waveform.size.toFloat()
                for (i in waveform.indices) {
                    val peakHeight = waveform[i] * (size.height * 0.85f)
                    val x = i * stepX
                    drawLine(
                        color = NleWaveform,
                        start = Offset(x, midY - peakHeight / 2f),
                        end = Offset(x, midY + peakHeight / 2f),
                        strokeWidth = max(1f, stepX * 0.85f)
                    )
                }
            }
        }

        // Clip Title & Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Link Badge (Section 35: VISUAL LINK INDICATOR)
                if (clip.linkedClipId != null) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Linked to audio/video",
                        tint = NleAccentCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                // Group Badge
                if (clip.groupId != null) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Grouped",
                        tint = Color.Yellow,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = clip.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = String.format("%.1fs", clip.timelineDurationMs / 1000f),
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        // Left Trim Handle (visible when selected)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(NleAccentCyan.copy(alpha = 0.8f))
                    .pointerInput(clip.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onTrimStart(dragAmount.x)
                        }
                    }
            )

            // Right Trim Handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(NleAccentCyan.copy(alpha = 0.8f))
                    .pointerInput(clip.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onTrimEnd(dragAmount.x)
                        }
                    }
            )
        }
    }
}
