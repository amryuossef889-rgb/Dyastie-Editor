package com.example.dyastie.model

data class Project(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val fps: Float = 30f,
    val width: Int = 1920,
    val height: Int = 1080,
    val isShorts: Boolean = false,
    val tracks: List<Track> = Track.defaultTracks(),
    val clips: List<TimelineClip> = emptyList(),
    val markers: List<TimelineMarker> = emptyList(),
    val mediaItems: List<MediaItem> = emptyList()
) {
    val durationMs: Long
        get() = clips.maxOfOrNull { it.timelineEndMs }?.coerceAtLeast(10_000L) ?: 30_000L

    companion object {
        fun createEmpty(name: String = "Untitled Gaming Project"): Project {
            return Project(
                id = "proj_${System.currentTimeMillis()}",
                name = name
            )
        }
    }
}
