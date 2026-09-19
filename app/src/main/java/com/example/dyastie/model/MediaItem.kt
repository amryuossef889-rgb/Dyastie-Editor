package com.example.dyastie.model

import android.net.Uri

enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE
}

data class MediaItem(
    val id: String,
    val uriString: String,
    val name: String,
    val type: MediaType,
    val durationMs: Long,
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Float = 30f,
    val hasAudio: Boolean = true,
    val hasVideo: Boolean = true,
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val proxyUriString: String? = null,
    val waveformPeaks: FloatArray? = null,
    val isSample: Boolean = false,
    val isOffline: Boolean = false
) {
    val uri: Uri get() = Uri.parse(uriString)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaItem

        if (id != other.id) return false
        if (uriString != other.uriString) return false
        if (name != other.name) return false
        if (type != other.type) return false
        if (durationMs != other.durationMs) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (fps != other.fps) return false
        if (hasAudio != other.hasAudio) return false
        if (hasVideo != other.hasVideo) return false
        if (sampleRate != other.sampleRate) return false
        if (channelCount != other.channelCount) return false
        if (proxyUriString != other.proxyUriString) return false
        if (waveformPeaks != null) {
            if (other.waveformPeaks == null) return false
            if (!waveformPeaks.contentEquals(other.waveformPeaks)) return false
        } else if (other.waveformPeaks != null) return false
        if (isSample != other.isSample) return false
        if (isOffline != other.isOffline) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + uriString.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + fps.hashCode()
        result = 31 * result + hasAudio.hashCode()
        result = 31 * result + hasVideo.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channelCount
        result = 31 * result + (proxyUriString?.hashCode() ?: 0)
        result = 31 * result + (waveformPeaks?.contentHashCode() ?: 0)
        result = 31 * result + isSample.hashCode()
        result = 31 * result + isOffline.hashCode()
        return result
    }
}
