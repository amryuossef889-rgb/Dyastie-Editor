package com.example.dyastie.model

data class TimelineClip(
    val id: String,
    val mediaId: String,
    val name: String,
    val trackId: String,
    val isVideoTrack: Boolean,
    val timelineStartMs: Long,
    val timelineDurationMs: Long,
    val sourceInMs: Long,
    val sourceOutMs: Long,
    val linkedClipId: String? = null,
    val groupId: String? = null,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false,
    val isSelected: Boolean = false,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val pan: Float = 0.0f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val gainDb: Float = 0f,
    val transform: VideoTransform = VideoTransform(),
    val colorGrading: ColorGrading = ColorGrading(),
    val effects: List<VideoEffect> = emptyList(),
    val keyframes: List<ClipKeyframe> = emptyList(),
    val textOverlay: TextOverlay? = null,
    val transitionIn: ClipTransition? = null,
    val transitionOut: ClipTransition? = null
) {
    val timelineEndMs: Long get() = timelineStartMs + timelineDurationMs

    fun containsTime(timeMs: Long): Boolean {
        return timeMs in timelineStartMs until timelineEndMs
    }

    fun mapTimelineToSourceTime(timelineTimeMs: Long): Long {
        val offset = (timelineTimeMs - timelineStartMs).coerceAtLeast(0L)
        val sourceOffset = (offset * speed).toLong()
        return (sourceInMs + sourceOffset).coerceIn(sourceInMs, sourceOutMs)
    }
}
