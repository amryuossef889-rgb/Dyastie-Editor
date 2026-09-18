package com.example.dyastie.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.example.dyastie.audio.AudioEngine
import com.example.dyastie.model.MediaItem
import com.example.dyastie.model.MediaType
import com.example.dyastie.model.Project
import com.example.dyastie.model.TimelineClip
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.max

class VideoPlaybackController(
    private val context: Context,
    private val audioEngine: AudioEngine
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackJob: Job? = null

    private val _playheadPositionMs = MutableStateFlow(0L)
    val playheadPositionMs: StateFlow<Long> = _playheadPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _previewQuality = MutableStateFlow(1.0f) // 1.0 = Full, 0.5 = 1/2, 0.25 = 1/4
    val previewQuality: StateFlow<Float> = _previewQuality.asStateFlow()

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val retrievers = HashMap<String, MediaMetadataRetriever>()
    private val frameCache = object : LruCache<String, Bitmap>(32) {}

    fun setPreviewQuality(quality: Float) {
        _previewQuality.value = quality
    }

    fun seekTo(timeMs: Long, project: Project) {
        val clampedTime = timeMs.coerceIn(0L, project.durationMs)
        _playheadPositionMs.value = clampedTime
        renderCurrentTime(project)
        syncAudio(project)
    }

    fun play(project: Project) {
        if (_isPlaying.value) return
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = coroutineScope.launch {
            var lastTime = System.currentTimeMillis()
            while (isActive && _isPlaying.value) {
                val now = System.currentTimeMillis()
                val delta = now - lastTime
                lastTime = now

                val nextTime = _playheadPositionMs.value + delta
                if (nextTime >= project.durationMs) {
                    _playheadPositionMs.value = 0L
                    _isPlaying.value = false
                    syncAudio(project)
                    renderCurrentTime(project)
                    break
                } else {
                    _playheadPositionMs.value = nextTime
                    renderCurrentTime(project)
                    syncAudio(project)
                }
                delay(30) // ~30 fps tick
            }
        }
    }

    fun pause(project: Project) {
        _isPlaying.value = false
        playbackJob?.cancel()
        syncAudio(project)
    }

    fun togglePlayPause(project: Project) {
        if (_isPlaying.value) pause(project) else play(project)
    }

    fun stepFrame(project: Project, frames: Int) {
        pause(project)
        val frameMs = (1000f / project.fps).toLong().coerceAtLeast(16L)
        seekTo(_playheadPositionMs.value + frames * frameMs, project)
    }

    fun renderCurrentTime(project: Project) {
        val currentTimeMs = _playheadPositionMs.value
        // Find topmost active video track clip
        val videoClips = project.clips.filter { clip ->
            clip.isVideoTrack && clip.containsTime(currentTimeMs)
        }.sortedByDescending { it.trackId } // V4 > V3 > V2 > V1

        val activeClip = videoClips.firstOrNull { clip ->
            val track = project.tracks.find { it.id == clip.trackId }
            track?.isHidden != true && !clip.isLocked
        }

        coroutineScope.launch(Dispatchers.Default) {
            val baseBitmap = if (activeClip != null) {
                val mediaItem = project.mediaItems.find { it.id == activeClip.mediaId }
                val sourceTimeMs = activeClip.mapTimelineToSourceTime(currentTimeMs)
                getFrameAtTime(mediaItem, sourceTimeMs)
            } else null

            val rendered = VideoRenderPipeline.renderFrame(
                baseBitmap = baseBitmap,
                outputWidth = project.width,
                outputHeight = project.height,
                clip = activeClip,
                timelineTimeMs = currentTimeMs,
                previewQualityScale = _previewQuality.value
            )

            withContext(Dispatchers.Main) {
                _currentFrame.value = rendered
            }
        }
    }

    private fun getFrameAtTime(mediaItem: MediaItem?, sourceTimeMs: Long): Bitmap? {
        if (mediaItem == null) return null

        if (mediaItem.isSample || mediaItem.type == MediaType.IMAGE) {
            return generateSampleGamingFrame(mediaItem, sourceTimeMs)
        }

        val cacheKey = "${mediaItem.id}_${sourceTimeMs / 100}" // Cache per ~100ms
        frameCache.get(cacheKey)?.let { return it }

        try {
            val retriever = retrievers.getOrPut(mediaItem.id) {
                MediaMetadataRetriever().apply {
                    setDataSource(context, mediaItem.uri)
                }
            }
            val frame = retriever.getFrameAtTime(
                sourceTimeMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            if (frame != null) {
                frameCache.put(cacheKey, frame)
                return frame
            }
        } catch (e: Exception) {
            Log.w("VideoPlayback", "Error extracting frame for ${mediaItem.id}", e)
        }

        return generateSampleGamingFrame(mediaItem, sourceTimeMs)
    }

    private fun generateSampleGamingFrame(mediaItem: MediaItem, sourceTimeMs: Long): Bitmap {
        val w = 640
        val h = 360
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Draw synthetic high-octane gaming frame
        val bgPaint = Paint().apply {
            color = when {
                mediaItem.name.contains("Minecraft", ignoreCase = true) -> Color.rgb(34, 139, 34)
                mediaItem.name.contains("FC Mobile", ignoreCase = true) -> Color.rgb(20, 80, 160)
                mediaItem.name.contains("Yellow", ignoreCase = true) -> Color.rgb(200, 160, 20)
                else -> Color.rgb(28, 32, 45)
            }
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Gaming HUD elements
        val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
        }
        canvas.drawText(mediaItem.name, 40f, 60f, hudPaint)

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            textSize = 20f
        }
        canvas.drawText("CLIP TIME: ${sourceTimeMs}ms", 40f, 95f, timePaint)

        // Animated gameplay crosshair / radar
        val angle = (sourceTimeMs * 0.1f) % 360f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            strokeWidth = 3f
        }
        canvas.drawCircle(w / 2f, h / 2f, 40f, linePaint.apply { style = Paint.Style.STROKE })
        canvas.drawLine(w / 2f - 50, h / 2f, w / 2f + 50, h / 2f, linePaint)
        canvas.drawLine(w / 2f, h / 2f - 50, w / 2f, h / 2f + 50, linePaint)

        return bmp
    }

    private fun syncAudio(project: Project) {
        val mediaMap = project.mediaItems.associate { it.id to it.uri }
        audioEngine.updatePlayback(
            timelineTimeMs = _playheadPositionMs.value,
            isPlaying = _isPlaying.value,
            clips = project.clips,
            tracks = project.tracks,
            mediaMap = mediaMap
        )
    }

    fun release() {
        playbackJob?.cancel()
        for (retriever in retrievers.values) {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        retrievers.clear()
        frameCache.evictAll()
        audioEngine.release()
    }
}
