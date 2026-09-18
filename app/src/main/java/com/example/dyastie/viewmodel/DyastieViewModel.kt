package com.example.dyastie.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dyastie.audio.AudioEngine
import com.example.dyastie.audio.AudioSyncAssistant
import com.example.dyastie.audio.AudioSyncResult
import com.example.dyastie.audio.WaveformExtractor
import com.example.dyastie.engine.VideoPlaybackController
import com.example.dyastie.export.ExportConfig
import com.example.dyastie.export.VideoExportEngine
import com.example.dyastie.model.*
import com.example.dyastie.project.ProjectRepository
import com.example.dyastie.project.UndoRedoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class EditTool {
    SELECT,
    BLADE,
    TRIM,
    RIPPLE,
    HAND,
    ZOOM
}

data class ExportStatus(
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val exportedFile: File? = null,
    val error: String? = null
)

class DyastieViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ProjectRepository(application)
    private val undoRedo = UndoRedoManager()
    private val audioEngine = AudioEngine(application)
    val playback = VideoPlaybackController(application, audioEngine)

    private val _project = MutableStateFlow(Project.createEmpty())
    val project: StateFlow<Project> = _project.asStateFlow()

    private val _selectedClipIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedClipIds: StateFlow<Set<String>> = _selectedClipIds.asStateFlow()

    private val _activeTool = MutableStateFlow(EditTool.SELECT)
    val activeTool: StateFlow<EditTool> = _activeTool.asStateFlow()

    private val _timelineZoom = MutableStateFlow(1.0f) // 0.2f to 5.0f
    val timelineZoom: StateFlow<Float> = _timelineZoom.asStateFlow()

    private val _snappingEnabled = MutableStateFlow(true)
    val snappingEnabled: StateFlow<Boolean> = _snappingEnabled.asStateFlow()

    private val _clipboardClips = MutableStateFlow<List<TimelineClip>>(emptyList())

    private val _audioSyncResult = MutableStateFlow<AudioSyncResult?>(null)
    val audioSyncResult: StateFlow<AudioSyncResult?> = _audioSyncResult.asStateFlow()

    private val _isAnalyzingSync = MutableStateFlow(false)
    val isAnalyzingSync: StateFlow<Boolean> = _isAnalyzingSync.asStateFlow()

    private val _exportStatus = MutableStateFlow(ExportStatus())
    val exportStatus: StateFlow<ExportStatus> = _exportStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val waveformCache = mutableMapOf<String, FloatArray>()

    init {
        loadInitialProject()
        startAutosaveLoop()
    }

    private fun loadInitialProject() {
        viewModelScope.launch {
            val saved = repo.loadLatestProject()
            if (saved != null && (saved.clips.isNotEmpty() || saved.mediaItems.isNotEmpty())) {
                _project.value = saved
            } else {
                // Populate sample gaming setup for immediate interactive editing
                createSampleGamingProject()
            }
            playback.renderCurrentTime(_project.value)
        }
    }

    private fun createSampleGamingProject() {
        val gameVid = MediaItem(
            id = "media_gameplay",
            uriString = "sample://gameplay_minecraft_pvp.mp4",
            name = "Gameplay_Clutch_Moment.mp4",
            type = MediaType.VIDEO,
            durationMs = 24_000L,
            width = 1920,
            height = 1080,
            fps = 30f,
            hasAudio = true,
            hasVideo = true,
            isSample = true
        )

        val voiceMic = MediaItem(
            id = "media_voiceover",
            uriString = "sample://stream_mic_voice.wav",
            name = "External_Rode_Mic_Voice.wav",
            type = MediaType.AUDIO,
            durationMs = 24_000L,
            hasAudio = true,
            hasVideo = false,
            isSample = true
        )

        val sfxItem = MediaItem(
            id = "media_sfx",
            uriString = "sample://gaming_impact_sfx.wav",
            name = "Victory_Fanfare_SFX.wav",
            type = MediaType.AUDIO,
            durationMs = 8_000L,
            hasAudio = true,
            hasVideo = false,
            isSample = true
        )

        val v1ClipId = "clip_v1_main"
        val a1ClipId = "clip_a1_audio"

        val v1Clip = TimelineClip(
            id = v1ClipId,
            mediaId = gameVid.id,
            name = "Minecraft 1v4 Clutch [V]",
            trackId = "V1",
            isVideoTrack = true,
            timelineStartMs = 0L,
            timelineDurationMs = 24_000L,
            sourceInMs = 0L,
            sourceOutMs = 24_000L,
            linkedClipId = a1ClipId,
            transform = VideoTransform(),
            colorGrading = ColorGrading(saturation = 1.2f, contrast = 1.1f),
            effects = listOf(
                VideoEffect(id = "fx1", type = EffectType.VIGNETTE, intensity = 0.4f),
                VideoEffect(id = "fx2", type = EffectType.SHAKE, isEnabled = false, intensity = 0.8f)
            )
        )

        val a1Clip = TimelineClip(
            id = a1ClipId,
            mediaId = gameVid.id,
            name = "Game Sound (PvP + Impacts) [A]",
            trackId = "A1",
            isVideoTrack = false,
            timelineStartMs = 0L,
            timelineDurationMs = 24_000L,
            sourceInMs = 0L,
            sourceOutMs = 24_000L,
            linkedClipId = v1ClipId,
            volume = 0.8f
        )

        val a2VoiceClip = TimelineClip(
            id = "clip_a2_voice",
            mediaId = voiceMic.id,
            name = "Live Commentary (USB Mic) [A]",
            trackId = "A2",
            isVideoTrack = false,
            timelineStartMs = 500L, // slightly un-synced by default to test Audio Sync Assistant!
            timelineDurationMs = 23_500L,
            sourceInMs = 0L,
            sourceOutMs = 23_500L,
            volume = 1.1f,
            gainDb = 2.0f
        )

        val textClip = TimelineClip(
            id = "clip_text_win",
            mediaId = "media_text",
            name = "Text: CLUTCH VICTORY!",
            trackId = "V4",
            isVideoTrack = true,
            timelineStartMs = 8_000L,
            timelineDurationMs = 4_000L,
            sourceInMs = 0L,
            sourceOutMs = 4_000L,
            textOverlay = TextOverlay(
                id = "txt_1",
                text = "INSANE 1v4 CLUTCH!",
                fontSizeSp = 36f,
                isBold = true,
                textColor = 0xFFFFD700,
                strokeColor = 0xFF000000,
                strokeWidth = 4f,
                presetName = "CLUTCH"
            )
        )

        val proj = Project(
            id = "proj_gaming_starter",
            name = "Gaming Montage 01",
            mediaItems = listOf(gameVid, voiceMic, sfxItem),
            clips = listOf(v1Clip, a1Clip, a2VoiceClip, textClip)
        )

        _project.value = proj
        saveCurrentState()

        // Generate waveforms for the sample clips
        viewModelScope.launch {
            loadWaveformForClip(a1Clip, gameVid)
            loadWaveformForClip(a2VoiceClip, voiceMic)
        }
    }

    private fun startAutosaveLoop() {
        viewModelScope.launch {
            while (true) {
                delay(30_000L) // 30s autosave requirement
                repo.saveProject(_project.value, isAutosave = true)
            }
        }
    }

    fun saveProjectNow() {
        viewModelScope.launch {
            val success = repo.saveProject(_project.value, isAutosave = false)
            _statusMessage.value = if (success) "Project saved successfully" else "Failed to save project"
        }
    }

    fun saveStateOnAppPause() {
        viewModelScope.launch {
            repo.saveProject(_project.value, isAutosave = true)
        }
    }

    private fun saveCurrentState() {
        undoRedo.pushState(_project.value)
    }

    // Tools & Selection
    fun setTool(tool: EditTool) {
        _activeTool.value = tool
    }

    fun setZoom(zoom: Float) {
        _timelineZoom.value = zoom.coerceIn(0.25f, 6.0f)
    }

    fun toggleSnapping() {
        _snappingEnabled.value = !_snappingEnabled.value
    }

    fun selectClip(clipId: String, isMultiSelect: Boolean = false) {
        val current = _selectedClipIds.value.toMutableSet()
        if (isMultiSelect) {
            if (current.contains(clipId)) current.remove(clipId) else current.add(clipId)
        } else {
            current.clear()
            current.add(clipId)
            // If linked, also highlight its counterpart visually!
            val clip = _project.value.clips.find { it.id == clipId }
            clip?.linkedClipId?.let { linkedId ->
                current.add(linkedId)
            }
        }
        _selectedClipIds.value = current
    }

    fun clearSelection() {
        _selectedClipIds.value = emptySet()
    }

    fun selectAll() {
        _selectedClipIds.value = _project.value.clips.map { it.id }.toSet()
    }

    // Media Import
    fun importMedia(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)

                val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
                val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 10_000L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
                retriever.release()

                val fileName = uri.lastPathSegment ?: "Imported_Media_${System.currentTimeMillis()}"
                val type = if (hasVideo) MediaType.VIDEO else if (hasAudio) MediaType.AUDIO else MediaType.IMAGE

                val mediaItem = MediaItem(
                    id = "media_${UUID.randomUUID().toString().take(8)}",
                    uriString = uri.toString(),
                    name = fileName,
                    type = type,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    hasAudio = hasAudio,
                    hasVideo = hasVideo
                )

                withContext(Dispatchers.Main) {
                    saveCurrentState()
                    val updatedMedia = _project.value.mediaItems + mediaItem
                    _project.value = _project.value.copy(mediaItems = updatedMedia)
                    _statusMessage.value = "Imported $fileName"
                    // Add directly to timeline at playhead if user imports
                    addMediaToTimeline(mediaItem)
                }
            } catch (e: Exception) {
                Log.e("DyastieVM", "Failed to import media", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to import: ${e.localizedMessage}"
                }
            }
        }
    }

    fun addMediaToTimeline(mediaItem: MediaItem, customStartMs: Long? = null) {
        saveCurrentState()
        val playhead = customStartMs ?: playback.playheadPositionMs.value
        val duration = mediaItem.durationMs.coerceAtLeast(1000L)

        val newClips = mutableListOf<TimelineClip>()

        if (mediaItem.hasVideo) {
            val vClipId = "clip_${UUID.randomUUID().toString().take(8)}"
            val aClipId = if (mediaItem.hasAudio) "clip_${UUID.randomUUID().toString().take(8)}" else null

            val videoClip = TimelineClip(
                id = vClipId,
                mediaId = mediaItem.id,
                name = "${mediaItem.name} [V]",
                trackId = "V1",
                isVideoTrack = true,
                timelineStartMs = playhead,
                timelineDurationMs = duration,
                sourceInMs = 0L,
                sourceOutMs = duration,
                linkedClipId = aClipId
            )
            newClips.add(videoClip)

            if (mediaItem.hasAudio && aClipId != null) {
                val audioClip = TimelineClip(
                    id = aClipId,
                    mediaId = mediaItem.id,
                    name = "${mediaItem.name} [A]",
                    trackId = "A1",
                    isVideoTrack = false,
                    timelineStartMs = playhead,
                    timelineDurationMs = duration,
                    sourceInMs = 0L,
                    sourceOutMs = duration,
                    linkedClipId = vClipId
                )
                newClips.add(audioClip)
                viewModelScope.launch { loadWaveformForClip(audioClip, mediaItem) }
            }
        } else if (mediaItem.hasAudio) {
            val audioClip = TimelineClip(
                id = "clip_${UUID.randomUUID().toString().take(8)}",
                mediaId = mediaItem.id,
                name = "${mediaItem.name} [A]",
                trackId = "A2", // External audio / voice
                isVideoTrack = false,
                timelineStartMs = playhead,
                timelineDurationMs = duration,
                sourceInMs = 0L,
                sourceOutMs = duration
            )
            newClips.add(audioClip)
            viewModelScope.launch { loadWaveformForClip(audioClip, mediaItem) }
        }

        _project.value = _project.value.copy(clips = _project.value.clips + newClips)
        playback.renderCurrentTime(_project.value)
        _selectedClipIds.value = newClips.map { it.id }.toSet()
        _statusMessage.value = "Added to timeline (${newClips.size} tracks)"
    }

    private suspend fun loadWaveformForClip(clip: TimelineClip, mediaItem: MediaItem) {
        val peaks = WaveformExtractor.getWaveform(
            context = getApplication(),
            uri = mediaItem.uri,
            durationMs = mediaItem.durationMs
        )
        waveformCache[clip.id] = peaks
        waveformCache[mediaItem.id] = peaks
    }

    fun getWaveformForClip(clip: TimelineClip): FloatArray? {
        return waveformCache[clip.id] ?: waveformCache[clip.mediaId]
    }

    // Split Clip (Section 13, Keyboard Shortcut S)
    fun splitAtPlayhead() {
        val playhead = playback.playheadPositionMs.value
        val selectedIds = _selectedClipIds.value

        // Target clips: selected clips containing playhead, or any clip containing playhead if none selected
        val targets = if (selectedIds.isNotEmpty()) {
            _project.value.clips.filter { selectedIds.contains(it.id) && it.containsTime(playhead) }
        } else {
            _project.value.clips.filter { it.containsTime(playhead) }
        }

        if (targets.isEmpty()) {
            _statusMessage.value = "No clip at playhead to split"
            return
        }

        saveCurrentState()
        val allClips = _project.value.clips.toMutableList()
        val newlyCreatedIds = mutableSetOf<String>()

        // Collect all related linked clips to split synchronously
        val clipsToSplit = mutableSetOf<TimelineClip>()
        for (c in targets) {
            clipsToSplit.add(c)
            c.linkedClipId?.let { linkedId ->
                allClips.find { it.id == linkedId && it.containsTime(playhead) }?.let { clipsToSplit.add(it) }
            }
        }

        for (clip in clipsToSplit) {
            val splitOffsetMs = playhead - clip.timelineStartMs
            if (splitOffsetMs <= 100L || splitOffsetMs >= clip.timelineDurationMs - 100L) continue

            val firstPartDuration = splitOffsetMs
            val secondPartDuration = clip.timelineDurationMs - splitOffsetMs

            val sourceSplitMs = clip.mapTimelineToSourceTime(playhead)

            val part1 = clip.copy(
                timelineDurationMs = firstPartDuration,
                sourceOutMs = sourceSplitMs
            )

            val part2Id = "clip_${UUID.randomUUID().toString().take(8)}"
            val part2 = clip.copy(
                id = part2Id,
                timelineStartMs = playhead,
                timelineDurationMs = secondPartDuration,
                sourceInMs = sourceSplitMs,
                linkedClipId = null // Will re-link after
            )

            val idx = allClips.indexOfFirst { it.id == clip.id }
            if (idx >= 0) {
                allClips[idx] = part1
                allClips.add(part2)
                newlyCreatedIds.add(part2Id)
            }
        }

        _project.value = _project.value.copy(clips = allClips)
        _selectedClipIds.value = newlyCreatedIds
        playback.renderCurrentTime(_project.value)
        _statusMessage.value = "Split ${clipsToSplit.size} clip(s)"
    }

    // Trim Clip
    fun trimClip(clipId: String, newStartMs: Long, newDurationMs: Long, isTrimStart: Boolean) {
        val clip = _project.value.clips.find { it.id == clipId } ?: return
        if (newDurationMs < 200L) return

        saveCurrentState()
        val allClips = _project.value.clips.toMutableList()

        // Check if linked: trim counterpart together
        val linkedClip = clip.linkedClipId?.let { id -> allClips.find { it.id == id } }

        fun applyTrim(target: TimelineClip): TimelineClip {
            return if (isTrimStart) {
                val delta = newStartMs - target.timelineStartMs
                val newSourceIn = (target.sourceInMs + delta * target.speed).toLong()
                target.copy(
                    timelineStartMs = newStartMs,
                    timelineDurationMs = newDurationMs,
                    sourceInMs = newSourceIn.coerceIn(0L, target.sourceOutMs - 100L)
                )
            } else {
                val newSourceOut = (target.sourceInMs + newDurationMs * target.speed).toLong()
                target.copy(
                    timelineDurationMs = newDurationMs,
                    sourceOutMs = newSourceOut.coerceAtLeast(target.sourceInMs + 100L)
                )
            }
        }

        val idx = allClips.indexOfFirst { it.id == clip.id }
        if (idx >= 0) allClips[idx] = applyTrim(clip)

        if (linkedClip != null) {
            val linkedIdx = allClips.indexOfFirst { it.id == linkedClip.id }
            if (linkedIdx >= 0) allClips[linkedIdx] = applyTrim(linkedClip)
        }

        _project.value = _project.value.copy(clips = allClips)
        playback.renderCurrentTime(_project.value)
    }

    // Move Clip
    fun moveClip(clipId: String, deltaMs: Long, targetTrackId: String? = null) {
        val clip = _project.value.clips.find { it.id == clipId } ?: return
        saveCurrentState()
        val allClips = _project.value.clips.toMutableList()

        val linkedClip = clip.linkedClipId?.let { id -> allClips.find { it.id == id } }

        val newStart = (clip.timelineStartMs + deltaMs).coerceAtLeast(0L)
        val newTrack = if (targetTrackId != null && targetTrackId.startsWith(if (clip.isVideoTrack) "V" else "A")) {
            targetTrackId
        } else clip.trackId

        val idx = allClips.indexOfFirst { it.id == clip.id }
        if (idx >= 0) {
            allClips[idx] = clip.copy(timelineStartMs = newStart, trackId = newTrack)
        }

        if (linkedClip != null) {
            val linkedIdx = allClips.indexOfFirst { it.id == linkedClip.id }
            if (linkedIdx >= 0) {
                allClips[linkedIdx] = linkedClip.copy(
                    timelineStartMs = (linkedClip.timelineStartMs + deltaMs).coerceAtLeast(0L)
                )
            }
        }

        _project.value = _project.value.copy(clips = allClips)
        playback.renderCurrentTime(_project.value)
    }

    // Delete Clips
    fun deleteSelectedClips() {
        val selected = _selectedClipIds.value
        if (selected.isEmpty()) return

        saveCurrentState()
        val remaining = _project.value.clips.filterNot { selected.contains(it.id) }
        _project.value = _project.value.copy(clips = remaining)
        _selectedClipIds.value = emptySet()
        playback.renderCurrentTime(_project.value)
        _statusMessage.value = "Deleted ${selected.size} clip(s)"
    }

    // Ripple Delete
    fun rippleDeleteSelectedClips() {
        val selected = _selectedClipIds.value
        if (selected.isEmpty()) return

        saveCurrentState()
        val clipsToDelete = _project.value.clips.filter { selected.contains(it.id) }
        val earliestStart = clipsToDelete.minOfOrNull { it.timelineStartMs } ?: 0L
        val maxDuration = clipsToDelete.maxOfOrNull { it.timelineDurationMs } ?: 0L

        val remaining = _project.value.clips.filterNot { selected.contains(it.id) }.map { c ->
            if (c.timelineStartMs >= earliestStart) {
                c.copy(timelineStartMs = (c.timelineStartMs - maxDuration).coerceAtLeast(0L))
            } else c
        }

        _project.value = _project.value.copy(clips = remaining)
        _selectedClipIds.value = emptySet()
        playback.renderCurrentTime(_project.value)
        _statusMessage.value = "Ripple deleted"
    }

    // Link / Unlink (Section 5)
    fun toggleLinkSelected() {
        val selected = _selectedClipIds.value
        if (selected.size < 2) {
            // If single selected clip is already linked, unlink it!
            if (selected.size == 1) {
                val clip = _project.value.clips.find { it.id == selected.first() }
                if (clip?.linkedClipId != null) {
                    unlinkClip(clip.id)
                    return
                }
            }
            _statusMessage.value = "Select 1 video and 1 audio clip to link"
            return
        }

        val clips = _project.value.clips.filter { selected.contains(it.id) }
        val videoClip = clips.find { it.isVideoTrack }
        val audioClip = clips.find { !it.isVideoTrack }

        if (videoClip != null && audioClip != null) {
            saveCurrentState()
            val updated = _project.value.clips.map { c ->
                when (c.id) {
                    videoClip.id -> c.copy(linkedClipId = audioClip.id)
                    audioClip.id -> c.copy(linkedClipId = videoClip.id)
                    else -> c
                }
            }
            _project.value = _project.value.copy(clips = updated)
            _statusMessage.value = "Linked ${videoClip.name} and ${audioClip.name}"
        }
    }

    fun unlinkClip(clipId: String) {
        val clip = _project.value.clips.find { it.id == clipId } ?: return
        val partnerId = clip.linkedClipId
        saveCurrentState()
        val updated = _project.value.clips.map { c ->
            if (c.id == clip.id || c.id == partnerId) c.copy(linkedClipId = null) else c
        }
        _project.value = _project.value.copy(clips = updated)
        _statusMessage.value = "Unlinked clip"
    }

    // Group / Ungroup (Section 11)
    fun groupSelectedClips() {
        val selected = _selectedClipIds.value
        if (selected.size < 2) {
            _statusMessage.value = "Select 2 or more clips to group"
            return
        }
        saveCurrentState()
        val groupId = "group_${UUID.randomUUID().toString().take(6)}"
        val updated = _project.value.clips.map { c ->
            if (selected.contains(c.id)) c.copy(groupId = groupId) else c
        }
        _project.value = _project.value.copy(clips = updated)
        _statusMessage.value = "Grouped ${selected.size} clips"
    }

    fun ungroupSelectedClips() {
        val selected = _selectedClipIds.value
        saveCurrentState()
        val updated = _project.value.clips.map { c ->
            if (selected.contains(c.id)) c.copy(groupId = null) else c
        }
        _project.value = _project.value.copy(clips = updated)
        _statusMessage.value = "Ungrouped"
    }

    // Audio Sync Assistant (Section 8)
    fun launchAudioSyncAssistant() {
        viewModelScope.launch {
            _isAnalyzingSync.value = true
            _statusMessage.value = "Analyzing audio transients and waveform correlation..."

            val a1Clip = _project.value.clips.find { it.trackId == "A1" }
            val a2Clip = _project.value.clips.find { it.trackId == "A2" }

            if (a1Clip == null || a2Clip == null) {
                _statusMessage.value = "Audio sync requires clips on A1 and A2"
                _isAnalyzingSync.value = false
                return@launch
            }

            val w1 = getWaveformForClip(a1Clip) ?: WaveformExtractor.generateFallbackWaveform(a1Clip.name, 400)
            val w2 = getWaveformForClip(a2Clip) ?: WaveformExtractor.generateFallbackWaveform(a2Clip.name, 400)

            val result = AudioSyncAssistant.calculateSyncOffset(
                referenceClip = a1Clip,
                referenceWaveform = w1,
                targetClip = a2Clip,
                targetWaveform = w2
            )

            _audioSyncResult.value = result
            _isAnalyzingSync.value = false
            _statusMessage.value = "Sync suggestion: ${result.suggestedOffsetMs}ms (confidence ${(result.confidence * 100).toInt()}%)"
        }
    }

    fun applyAudioSync(offsetMs: Long) {
        val res = _audioSyncResult.value ?: return
        val target = _project.value.clips.find { it.name == res.targetClipName || it.trackId == res.targetTrackId }
        if (target != null) {
            saveCurrentState()
            val newStart = (target.timelineStartMs + offsetMs).coerceAtLeast(0L)
            val updated = _project.value.clips.map { c ->
                if (c.id == target.id) c.copy(timelineStartMs = newStart) else c
            }
            _project.value = _project.value.copy(clips = updated)
            playback.renderCurrentTime(_project.value)
            _statusMessage.value = "Applied sync offset of ${offsetMs}ms"
        }
        _audioSyncResult.value = null
    }

    fun cancelAudioSync() {
        _audioSyncResult.value = null
    }

    // Inspector Updates
    fun updateClipTransform(clipId: String, transform: VideoTransform) {
        val updated = _project.value.clips.map { c ->
            if (c.id == clipId) c.copy(transform = transform) else c
        }
        _project.value = _project.value.copy(clips = updated)
        playback.renderCurrentTime(_project.value)
    }

    fun updateClipColorGrading(clipId: String, color: ColorGrading) {
        val updated = _project.value.clips.map { c ->
            if (c.id == clipId) c.copy(colorGrading = color) else c
        }
        _project.value = _project.value.copy(clips = updated)
        playback.renderCurrentTime(_project.value)
    }

    fun updateClipAudio(clipId: String, volume: Float, pan: Float, fadeInMs: Long, fadeOutMs: Long, gainDb: Float) {
        val updated = _project.value.clips.map { c ->
            if (c.id == clipId) c.copy(volume = volume, pan = pan, fadeInMs = fadeInMs, fadeOutMs = fadeOutMs, gainDb = gainDb) else c
        }
        _project.value = _project.value.copy(clips = updated)
    }

    fun updateClipSpeed(clipId: String, speed: Float) {
        saveCurrentState()
        val clip = _project.value.clips.find { it.id == clipId } ?: return
        val newDuration = ((clip.sourceOutMs - clip.sourceInMs) / speed).toLong()
        val updated = _project.value.clips.map { c ->
            if (c.id == clipId) c.copy(speed = speed, timelineDurationMs = newDuration) else c
        }
        _project.value = _project.value.copy(clips = updated)
        playback.renderCurrentTime(_project.value)
        _statusMessage.value = "Speed set to ${speed}x"
    }

    fun toggleClipEffect(clipId: String, effectType: EffectType) {
        saveCurrentState()
        val updated = _project.value.clips.map { c ->
            if (c.id == clipId) {
                val existing = c.effects.find { it.type == effectType }
                val newFx = if (existing != null) {
                    c.effects.map { if (it.type == effectType) it.copy(isEnabled = !it.isEnabled) else it }
                } else {
                    c.effects + VideoEffect(id = "fx_${UUID.randomUUID()}", type = effectType, isEnabled = true)
                }
                c.copy(effects = newFx)
            } else c
        }
        _project.value = _project.value.copy(clips = updated)
        playback.renderCurrentTime(_project.value)
    }

    fun updateClipTextOverlay(clipId: String, textOverlay: TextOverlay) {
        saveCurrentState()
        val updated = _project.value.clips.map { c ->
            if (c.id == clipId) c.copy(textOverlay = textOverlay) else c
        }
        _project.value = _project.value.copy(clips = updated)
        playback.renderCurrentTime(_project.value)
    }

    fun addTextPresetToTimeline(presetName: String, text: String, textColor: Long, strokeColor: Long) {
        saveCurrentState()
        val playhead = playback.playheadPositionMs.value
        val textClip = TimelineClip(
            id = "clip_text_${UUID.randomUUID().toString().take(6)}",
            mediaId = "media_text",
            name = "Text: $presetName",
            trackId = "V4",
            isVideoTrack = true,
            timelineStartMs = playhead,
            timelineDurationMs = 3000L,
            sourceInMs = 0L,
            sourceOutMs = 3000L,
            textOverlay = TextOverlay(
                id = "txt_${UUID.randomUUID()}",
                text = text,
                fontSizeSp = 34f,
                isBold = true,
                textColor = textColor,
                strokeColor = strokeColor,
                strokeWidth = 4f,
                presetName = presetName
            )
        )
        _project.value = _project.value.copy(clips = _project.value.clips + textClip)
        _selectedClipIds.value = setOf(textClip.id)
        playback.renderCurrentTime(_project.value)
        _statusMessage.value = "Added text overlay: $presetName"
    }

    // Track Controls (Mute, Solo, Lock, Hide, Volume)
    fun toggleTrackMute(trackId: String) {
        val updated = _project.value.tracks.map { t ->
            if (t.id == trackId) t.copy(isMuted = !t.isMuted) else t
        }
        _project.value = _project.value.copy(tracks = updated)
    }

    fun toggleTrackSolo(trackId: String) {
        val updated = _project.value.tracks.map { t ->
            if (t.id == trackId) t.copy(isSolo = !t.isSolo) else t
        }
        _project.value = _project.value.copy(tracks = updated)
    }

    fun toggleTrackLock(trackId: String) {
        val updated = _project.value.tracks.map { t ->
            if (t.id == trackId) t.copy(isLocked = !t.isLocked) else t
        }
        _project.value = _project.value.copy(tracks = updated)
    }

    fun toggleTrackHide(trackId: String) {
        val updated = _project.value.tracks.map { t ->
            if (t.id == trackId) t.copy(isHidden = !t.isHidden) else t
        }
        _project.value = _project.value.copy(tracks = updated)
        playback.renderCurrentTime(_project.value)
    }

    // Markers (Section 4)
    fun addMarkerAtPlayhead() {
        val playhead = playback.playheadPositionMs.value
        val marker = TimelineMarker(
            id = "marker_${UUID.randomUUID().toString().take(6)}",
            timeMs = playhead,
            label = "Marker ${(playhead / 1000f).toInt()}s"
        )
        _project.value = _project.value.copy(markers = _project.value.markers + marker)
        _statusMessage.value = "Marker added at ${playhead}ms"
    }

    // Clipboard (Copy / Paste)
    fun copySelected() {
        val selected = _project.value.clips.filter { _selectedClipIds.value.contains(it.id) }
        _clipboardClips.value = selected
        _statusMessage.value = "Copied ${selected.size} clip(s)"
    }

    fun cutSelected() {
        copySelected()
        deleteSelectedClips()
    }

    fun pasteAtPlayhead() {
        val clipboard = _clipboardClips.value
        if (clipboard.isEmpty()) return

        saveCurrentState()
        val playhead = playback.playheadPositionMs.value
        val earliest = clipboard.minOfOrNull { it.timelineStartMs } ?: 0L

        val pastedClips = clipboard.map { c ->
            val offset = c.timelineStartMs - earliest
            c.copy(
                id = "clip_${UUID.randomUUID().toString().take(8)}",
                timelineStartMs = playhead + offset,
                linkedClipId = null
            )
        }

        _project.value = _project.value.copy(clips = _project.value.clips + pastedClips)
        _selectedClipIds.value = pastedClips.map { it.id }.toSet()
        playback.renderCurrentTime(_project.value)
        _statusMessage.value = "Pasted ${pastedClips.size} clip(s)"
    }

    // Undo / Redo (Section 28)
    fun undo() {
        val prev = undoRedo.undo(_project.value)
        if (prev != null) {
            _project.value = prev
            playback.renderCurrentTime(prev)
            _statusMessage.value = "Undo"
        }
    }

    fun redo() {
        val next = undoRedo.redo(_project.value)
        if (next != null) {
            _project.value = next
            playback.renderCurrentTime(next)
            _statusMessage.value = "Redo"
        }
    }

    val canUndo: Boolean get() = undoRedo.canUndo
    val canRedo: Boolean get() = undoRedo.canRedo

    // Export
    fun startExport(config: ExportConfig) {
        viewModelScope.launch {
            _exportStatus.value = ExportStatus(isExporting = true, progress = 0f, statusText = "Initializing encoder...")
            val resultFile = VideoExportEngine.exportProject(
                context = getApplication(),
                project = _project.value,
                config = config,
                onProgress = { prog, text ->
                    _exportStatus.value = _exportStatus.value.copy(progress = prog, statusText = text)
                }
            )

            if (resultFile != null) {
                _exportStatus.value = ExportStatus(
                    isExporting = false,
                    progress = 1.0f,
                    statusText = "Exported: ${resultFile.name}",
                    exportedFile = resultFile
                )
                _statusMessage.value = "Exported to Movies folder!"
            } else {
                _exportStatus.value = ExportStatus(
                    isExporting = false,
                    statusText = "Export failed",
                    error = "Could not encode video file"
                )
            }
        }
    }

    fun dismissExport() {
        _exportStatus.value = ExportStatus()
    }

    override fun onCleared() {
        super.onCleared()
        playback.release()
    }
}
