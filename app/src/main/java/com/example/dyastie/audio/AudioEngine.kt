package com.example.dyastie.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.dyastie.model.TimelineClip
import com.example.dyastie.model.Track
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class AudioEngine(private val context: Context) {
    private val activePlayers = ConcurrentHashMap<String, MediaPlayer>()
    private var isPlaying = false

    fun updatePlayback(
        timelineTimeMs: Long,
        isPlaying: Boolean,
        clips: List<TimelineClip>,
        tracks: List<Track>,
        mediaMap: Map<String, Uri>
    ) {
        this.isPlaying = isPlaying
        val hasSolo = tracks.any { !it.isVideo && it.isSolo }

        // Find active audio clips at timeline position
        val activeAudioClips = clips.filter { clip ->
            !clip.isVideoTrack && clip.containsTime(timelineTimeMs)
        }
        val activeClipIds = activeAudioClips.map { it.id }.toSet()

        // Stop & release players that are no longer active
        val iterator = activePlayers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!activeClipIds.contains(entry.key) || !isPlaying) {
                try {
                    entry.value.stop()
                    entry.value.release()
                } catch (e: Exception) {
                    Log.w("AudioEngine", "Error releasing player for ${entry.key}", e)
                }
                iterator.remove()
            }
        }

        if (!isPlaying) return

        // Play or update volume for active clips
        for (clip in activeAudioClips) {
            val track = tracks.find { it.id == clip.trackId }
            val isMuted = clip.isMuted || (track?.isMuted == true) || (hasSolo && track?.isSolo != true)

            val trackVolume = track?.volume ?: 1.0f
            val clipGainMultiplier = 10.0.pow(clip.gainDb / 20.0).toFloat()
            var baseVol = if (isMuted) 0f else (clip.volume * trackVolume * clipGainMultiplier).coerceIn(0f, 2f)

            // Apply Fade In / Fade Out
            val clipElapsedMs = timelineTimeMs - clip.timelineStartMs
            val clipRemainingMs = clip.timelineEndMs - timelineTimeMs

            if (clip.fadeInMs > 0 && clipElapsedMs < clip.fadeInMs) {
                baseVol *= (clipElapsedMs.toFloat() / clip.fadeInMs).coerceIn(0f, 1f)
            }
            if (clip.fadeOutMs > 0 && clipRemainingMs < clip.fadeOutMs) {
                baseVol *= (clipRemainingMs.toFloat() / clip.fadeOutMs).coerceIn(0f, 1f)
            }

            // Apply Pan (-1.0 to 1.0)
            val leftVol = (baseVol * (1.0f - max(0.0f, clip.pan))).coerceIn(0f, 1f)
            val rightVol = (baseVol * (1.0f - max(0.0f, -clip.pan))).coerceIn(0f, 1f)

            val player = activePlayers[clip.id]
            if (player != null) {
                try {
                    player.setVolume(leftVol, rightVol)
                } catch (e: Exception) {
                    Log.w("AudioEngine", "Error setting volume for ${clip.id}", e)
                }
            } else {
                val mediaUri = mediaMap[clip.mediaId] ?: continue
                try {
                    val newPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(context, mediaUri)
                        prepare()
                        val sourceTimeMs = clip.mapTimelineToSourceTime(timelineTimeMs).toInt()
                        seekTo(sourceTimeMs)
                        setVolume(leftVol, rightVol)
                        start()
                    }
                    activePlayers[clip.id] = newPlayer
                } catch (e: Exception) {
                    Log.w("AudioEngine", "Error creating player for audio clip ${clip.id}", e)
                }
            }
        }
    }

    fun release() {
        for (player in activePlayers.values) {
            try {
                player.stop()
                player.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        activePlayers.clear()
    }
}
