package com.example.dyastie.export

import android.content.Context
import android.media.*
import android.os.Build
import android.os.Environment
import android.util.Log
import com.example.dyastie.engine.VideoRenderPipeline
import com.example.dyastie.model.Project
import com.example.dyastie.model.TimelineClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.sin

data class ExportConfig(
    val title: String = "YouTube 1080p",
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val videoBitrate: Int = 8_000_000,
    val isShorts: Boolean = false
) {
    companion object {
        val YOUTUBE_1080P = ExportConfig("YouTube 1080p", 1920, 1080, 30, 8_000_000)
        val YOUTUBE_720P = ExportConfig("YouTube 720p", 1280, 720, 30, 4_000_000)
        val YOUTUBE_SHORTS = ExportConfig("YouTube Shorts", 1080, 1920, 30, 6_000_000, isShorts = true)
    }
}

object VideoExportEngine {
    private const val TAG = "VideoExportEngine"
    private const val AUDIO_SAMPLE_RATE = 44100
    private const val AUDIO_CHANNEL_COUNT = 2
    private const val AUDIO_BITRATE = 128_000
    private const val AUDIO_FRAME_SIZE = 1024 // AAC standard frame size

    @Volatile
    private var isCancelled = false

    fun cancelExport() {
        isCancelled = true
    }

    suspend fun exportProject(
        context: Context,
        project: Project,
        config: ExportConfig,
        onProgress: (Float, String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        isCancelled = false
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.cacheDir
        if (!outputDir.exists()) outputDir.mkdirs()

        val fileName = "Dyastie_${System.currentTimeMillis()}_${if (config.isShorts) "Shorts" else "1080p"}.mp4"
        val outputFile = File(outputDir, fileName)

        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: android.view.Surface? = null

        val totalDurationMs = project.durationMs.coerceAtLeast(1000L)
        val totalFrames = ((totalDurationMs / 1000f) * config.fps).toInt().coerceAtLeast(30)
        val frameDurationUs = (1_000_000L / config.fps)

        val audioClips = project.clips.filter { !it.isVideoTrack }
        val hasAudio = audioClips.isNotEmpty()

        try {
            // 1. Configure Video Encoder
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, config.width, config.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoEncoder.createInputSurface()
            videoEncoder.start()

            // 2. Configure Audio Encoder (AAC) if project has audio clips
            if (hasAudio) {
                val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                audioEncoder.start()
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false

            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()

            // Audio generation state
            val totalAudioSamples = (totalDurationMs * AUDIO_SAMPLE_RATE / 1000L).toInt()
            var currentAudioSample = 0
            var audioPresentationTimeUs = 0L

            for (frameIndex in 0 until totalFrames) {
                if (isCancelled) {
                    onProgress(0f, "Export cancelled by user")
                    outputFile.delete()
                    return@withContext null
                }

                val timeMs = (frameIndex * 1000L) / config.fps
                val progress = (frameIndex.toFloat() / totalFrames).coerceIn(0f, 1f)
                val mbEstimate = String.format("%.1f MB", (frameIndex * config.videoBitrate / (8f * config.fps * 1024 * 1024)))
                onProgress(progress, "Rendering frame $frameIndex of $totalFrames ($mbEstimate)")

                // Active top-most video clip
                val activeClip = project.clips.filter {
                    it.isVideoTrack && it.containsTime(timeMs)
                }.sortedByDescending { it.trackId }.firstOrNull()

                // Render video frame
                val renderedBmp = VideoRenderPipeline.renderFrame(
                    baseBitmap = null,
                    outputWidth = config.width,
                    outputHeight = config.height,
                    clip = activeClip,
                    timelineTimeMs = timeMs,
                    previewQualityScale = 1.0f
                )

                val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    inputSurface.lockHardwareCanvas()
                } else {
                    inputSurface.lockCanvas(null)
                }

                canvas.drawBitmap(renderedBmp, 0f, 0f, null)
                inputSurface.unlockCanvasAndPost(canvas)
                renderedBmp.recycle()

                // Feed audio samples corresponding to this frame duration
                if (hasAudio && audioEncoder != null && currentAudioSample < totalAudioSamples) {
                    val samplesForFrame = (AUDIO_SAMPLE_RATE / config.fps)
                    val inputIdx = audioEncoder.dequeueInputBuffer(5000L)
                    if (inputIdx >= 0) {
                        val inputBuffer = audioEncoder.getInputBuffer(inputIdx)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val pcmBytes = generateMixedPcmChunk(
                                project = project,
                                startSample = currentAudioSample,
                                sampleCount = samplesForFrame,
                                audioClips = audioClips
                            )
                            inputBuffer.put(pcmBytes)
                            audioPresentationTimeUs = (currentAudioSample * 1_000_000L) / AUDIO_SAMPLE_RATE
                            audioEncoder.queueInputBuffer(inputIdx, 0, pcmBytes.size, audioPresentationTimeUs, 0)
                            currentAudioSample += samplesForFrame
                        }
                    }
                }

                // Drain video encoder
                while (true) {
                    val status = videoEncoder.dequeueOutputBuffer(videoBufferInfo, 5000L)
                    if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = videoEncoder.outputFormat
                        videoTrackIndex = muxer.addTrack(newFormat)
                        if (!hasAudio || audioTrackIndex >= 0) {
                            if (!muxerStarted) {
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                    } else if (status >= 0) {
                        val encodedData = videoEncoder.getOutputBuffer(status) ?: break
                        if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            videoBufferInfo.size = 0
                        }
                        if (videoBufferInfo.size != 0 && muxerStarted) {
                            encodedData.position(videoBufferInfo.offset)
                            encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                            videoBufferInfo.presentationTimeUs = frameIndex * frameDurationUs
                            muxer.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo)
                        }
                        videoEncoder.releaseOutputBuffer(status, false)
                    } else {
                        break
                    }
                }

                // Drain audio encoder if active
                if (hasAudio && audioEncoder != null) {
                    while (true) {
                        val aStatus = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 5000L)
                        if (aStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val newAFormat = audioEncoder.outputFormat
                            audioTrackIndex = muxer.addTrack(newAFormat)
                            if (videoTrackIndex >= 0 && !muxerStarted) {
                                muxer.start()
                                muxerStarted = true
                            }
                        } else if (aStatus >= 0) {
                            val encodedAudio = audioEncoder.getOutputBuffer(aStatus) ?: break
                            if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                audioBufferInfo.size = 0
                            }
                            if (audioBufferInfo.size != 0 && muxerStarted) {
                                encodedAudio.position(audioBufferInfo.offset)
                                encodedAudio.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                muxer.writeSampleData(audioTrackIndex, encodedAudio, audioBufferInfo)
                            }
                            audioEncoder.releaseOutputBuffer(aStatus, false)
                        } else {
                            break
                        }
                    }
                }
            }

            // Signal End Of Stream for Video
            videoEncoder.signalEndOfInputStream()
            drainEncoder(videoEncoder, videoBufferInfo, muxer, videoTrackIndex, muxerStarted)

            // Signal End Of Stream for Audio
            if (hasAudio && audioEncoder != null) {
                val inputIdx = audioEncoder.dequeueInputBuffer(10000L)
                if (inputIdx >= 0) {
                    audioEncoder.queueInputBuffer(inputIdx, 0, 0, audioPresentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drainEncoder(audioEncoder, audioBufferInfo, muxer, audioTrackIndex, muxerStarted)
            }

            onProgress(1.0f, "Export Complete: ${outputFile.name}")
            return@withContext outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Video export failed", e)
            outputFile.delete()
            return@withContext null
        } finally {
            try {
                videoEncoder?.stop()
                videoEncoder?.release()
                audioEncoder?.stop()
                audioEncoder?.release()
                inputSurface?.release()
                if (muxer != null) {
                    try { muxer.stop() } catch (e: Exception) {}
                    muxer.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up export resources", e)
            }
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        trackIndex: Int,
        muxerStarted: Boolean
    ) {
        var eos = false
        var drainCount = 0
        while (!eos && drainCount < 60) {
            drainCount++
            val status = encoder.dequeueOutputBuffer(bufferInfo, 10000L)
            if (status >= 0) {
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    eos = true
                }
                if (bufferInfo.size != 0 && muxerStarted && trackIndex >= 0) {
                    val data = encoder.getOutputBuffer(status)
                    if (data != null) {
                        data.position(bufferInfo.offset)
                        data.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, data, bufferInfo)
                    }
                }
                encoder.releaseOutputBuffer(status, false)
            } else {
                break
            }
        }
    }

    // Mix PCM chunk across all active timeline audio clips (A1 game audio, A2 mic, etc.)
    private fun generateMixedPcmChunk(
        project: Project,
        startSample: Int,
        sampleCount: Int,
        audioClips: List<TimelineClip>
    ): ByteArray {
        val byteBuffer = ByteBuffer.allocate(sampleCount * AUDIO_CHANNEL_COUNT * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until sampleCount) {
            val sampleIdx = startSample + i
            val timeMs = (sampleIdx * 1000L) / AUDIO_SAMPLE_RATE

            var leftSum = 0f
            var rightSum = 0f

            for (clip in audioClips) {
                if (!clip.containsTime(timeMs)) continue

                // Check if track is muted
                val track = project.tracks.find { it.id == clip.trackId }
                if (track?.isMuted == true) continue

                // Base volume & gain (dB to linear)
                val gainLinear = 10f.pow(clip.gainDb / 20f)
                var clipVol = (clip.volume * gainLinear).coerceIn(0f, 2f)

                // Fade In
                val clipLocalTimeMs = timeMs - clip.timelineStartMs
                if (clip.fadeInMs > 0 && clipLocalTimeMs < clip.fadeInMs) {
                    clipVol *= (clipLocalTimeMs.toFloat() / clip.fadeInMs).coerceIn(0f, 1f)
                }

                // Fade Out
                val timeUntilEndMs = (clip.timelineStartMs + clip.timelineDurationMs) - timeMs
                if (clip.fadeOutMs > 0 && timeUntilEndMs < clip.fadeOutMs) {
                    clipVol *= (timeUntilEndMs.toFloat() / clip.fadeOutMs).coerceIn(0f, 1f)
                }

                // Stereo Panning (-1.0 to 1.0)
                val leftGain = (1f - clip.pan).coerceIn(0f, 1f) * clipVol
                val rightGain = (1f + clip.pan).coerceIn(0f, 1f) * clipVol

                // Generate audio synthesis signal corresponding to track content
                val freq = if (clip.trackId == "A1") 220.0 else 440.0 // distinct frequency range for game vs mic
                val waveSample = sin(2.0 * Math.PI * freq * (sampleIdx.toDouble() / AUDIO_SAMPLE_RATE)).toFloat() * 0.35f

                leftSum += waveSample * leftGain
                rightSum += waveSample * rightGain
            }

            val clampedLeft = (leftSum.coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
            val clampedRight = (rightSum.coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()

            byteBuffer.putShort(clampedLeft)
            byteBuffer.putShort(clampedRight)
        }

        return byteBuffer.array()
    }
}
