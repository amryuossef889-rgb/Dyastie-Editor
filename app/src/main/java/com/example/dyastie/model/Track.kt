package com.example.dyastie.model

data class Track(
    val id: String,
    val name: String,
    val isVideo: Boolean,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isLocked: Boolean = false,
    val isHidden: Boolean = false,
    val volume: Float = 1.0f,
    val heightDp: Int = if (isVideo) 64 else 54
) {
    companion object {
        fun defaultTracks(): List<Track> = listOf(
            Track(id = "V4", name = "V4 FX/Text", isVideo = true),
            Track(id = "V3", name = "V3 Overlay", isVideo = true),
            Track(id = "V2", name = "V2 Cam/Face", isVideo = true),
            Track(id = "V1", name = "V1 Gameplay", isVideo = true),
            Track(id = "A1", name = "A1 Game Audio", isVideo = false),
            Track(id = "A2", name = "A2 Voice Mic", isVideo = false),
            Track(id = "A3", name = "A3 Background Music", isVideo = false),
            Track(id = "A4", name = "A4 Sound FX", isVideo = false)
        )
    }
}

data class TimelineMarker(
    val id: String,
    val timeMs: Long,
    val label: String,
    val color: Long = 0xFFFF4444
)
