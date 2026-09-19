package com.example.dyastie.ui.preview

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.dyastie.viewmodel.DyastieViewModel

@Composable
fun PreviewMonitor(
    viewModel: DyastieViewModel,
    modifier: Modifier = Modifier,
    onToggleFullscreen: () -> Unit = {}
) {
    val project by viewModel.project.collectAsState()
    val isPlaying by viewModel.playback.isPlaying.collectAsState()
    val playheadMs by viewModel.playback.playheadPositionMs.collectAsState()
    val currentFrame by viewModel.playback.currentFrame.collectAsState()
    val proxyRes by viewModel.proxyResolution.collectAsState()

    // Timecode calculation: HH:MM:SS:FF at 30 fps
    val totalSeconds = playheadMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val frames = ((playheadMs % 1000) * 30 / 1000).toInt()
    val timecodeStr = String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)

    Column(
        modifier = modifier
            .background(NleSurface)
            .border(width = 1.dp, color = NleBorder)
    ) {
        // Monitor Top Bar (Resolution, Timecode, Quality)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(NleSurfaceVariant)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PROGRAM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NleAccentCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${project.width}x${project.height} (${project.fps.toInt()} fps)",
                    fontSize = 10.sp,
                    color = NleTextSecondary
                )
            }

            // Central Pro Timecode readout
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(width = 1.dp, color = NleBorder, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = timecodeStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NleAccentCyan
                )
            }

            // Proxy Mode Switcher (1080p, 720p, 480p)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Mode:",
                    fontSize = 10.sp,
                    color = NleTextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                com.example.dyastie.viewmodel.ProxyResolution.values().forEach { res ->
                    val isSelected = proxyRes == res
                    val label = when (res) {
                        com.example.dyastie.viewmodel.ProxyResolution.ORIGINAL -> "1080p"
                        com.example.dyastie.viewmodel.ProxyResolution.PROXY_720P -> "720p"
                        com.example.dyastie.viewmodel.ProxyResolution.PROXY_480P -> "480p"
                    }
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) (if (res == com.example.dyastie.viewmodel.ProxyResolution.ORIGINAL) NleAccentCyan else Color(0xFFFFB300)) else NleTextSecondary,
                        modifier = Modifier
                            .clickable { viewModel.setProxyResolution(res) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Central Video Monitor Canvas (16:9 or 9:16 aspect ratio)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (currentFrame != null) {
                Image(
                    bitmap = currentFrame!!.asImageBitmap(),
                    contentDescription = "Video Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Prominent Proxy Badge when Proxy mode is active
                if (proxyRes != com.example.dyastie.viewmodel.ProxyResolution.ORIGINAL) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFCC8800).copy(alpha = 0.85f))
                            .border(1.dp, Color(0xFFFFD54F), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${proxyRes.title.uppercase()} (FAST TAB A7)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            } else {
                Text(
                    text = "READY",
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            }
        }

        // Transport Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(NleSurfaceVariant)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Markers / Mark In / Mark Out
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.playback.seekTo(0L, project) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FirstPage,
                        contentDescription = "Home",
                        tint = NleTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.addMarkerAtPlayhead() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Add Marker",
                        tint = NleAccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Center: Prev Frame, Reverse, Play/Pause, Forward, Next Frame
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.playback.stepFrame(project, -1) },
                    modifier = Modifier.size(32.dp).testTag("prev_frame_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Frame",
                        tint = NleTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.playback.togglePlayPause(project) },
                    modifier = Modifier
                        .size(38.dp)
                        .background(NleAccentCyan.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = NleAccentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.playback.stepFrame(project, 1) },
                    modifier = Modifier.size(32.dp).testTag("next_frame_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Frame",
                        tint = NleTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.playback.pause(project)
                        viewModel.playback.seekTo(0L, project)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = NleTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Right: Fullscreen
            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = NleTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
