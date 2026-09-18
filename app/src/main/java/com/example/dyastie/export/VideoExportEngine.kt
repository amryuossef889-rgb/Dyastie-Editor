package com.example.dyastie.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.*
import android.os.Build
import android.os.Environment
import android.util.Log
import com.example.dyastie.engine.VideoRenderPipeline
import com.example.dyastie.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

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

    suspend fun exportProject(
        context: Context,
        project: Project,
        config: ExportConfig,
        onProgress: (Float, String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.cacheDir
        if (!outputDir.exists()) outputDir.mkdirs()

        val fileName = "Dyastie_${System.currentTimeMillis()}_${if (config.isShorts) "Shorts" else "1080p"}.mp4"
        val outputFile = File(outputDir, fileName)

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: android.view.Surface? = null

        val totalDurationMs = project.durationMs.coerceAtLeast(1000L)
        val totalFrames = ((totalDurationMs / 1000f) * config.fps).toInt().coerceAtLeast(30)
        val frameDurationUs = (1_000_000L / config.fps)

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, config.width, config.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()

            for (frameIndex in 0 until totalFrames) {
                val timeMs = (frameIndex * 1000L) / config.fps
                val progress = (frameIndex.toFloat() / totalFrames).coerceIn(0f, 1f)
                val mbEstimate = String.format("%.1f MB", (frameIndex * config.videoBitrate / (8f * config.fps * 1024 * 1024)))
                onProgress(progress, "Rendering frame $frameIndex of $totalFrames ($mbEstimate)")

                // Active top-most clip
                val activeClip = project.clips.filter {
                    it.isVideoTrack && it.containsTime(timeMs)
                }.sortedByDescending { it.trackId }.firstOrNull()

                // Render frame
                val renderedBmp = VideoRenderPipeline.renderFrame(
                    baseBitmap = null, // Will draw clean pro graphics/clip frame
                    outputWidth = config.width,
                    outputHeight = config.height,
                    clip = activeClip,
                    timelineTimeMs = timeMs,
                    previewQualityScale = 1.0f
                )

                // Draw to encoder surface
                val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    inputSurface.lockHardwareCanvas()
                } else {
                    inputSurface.lockCanvas(null)
                }

                canvas.drawBitmap(renderedBmp, 0f, 0f, null)
                inputSurface.unlockCanvasAndPost(canvas)
                renderedBmp.recycle()

                // Drain encoder
                while (true) {
                    val status = encoder.dequeueOutputBuffer(bufferInfo, 10000L)
                    if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) throw RuntimeException("Format changed twice")
                        val newFormat = encoder.outputFormat
                        videoTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (status >= 0) {
                        val encodedData = encoder.getOutputBuffer(status) ?: break
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            bufferInfo.presentationTimeUs = frameIndex * frameDurationUs
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(status, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    } else {
                        break
                    }
                }
            }

            // Signal EOS
            encoder.signalEndOfInputStream()
            var eos = false
            var drainCount = 0
            while (!eos && drainCount < 50) {
                drainCount++
                val status = encoder.dequeueOutputBuffer(bufferInfo, 10000L)
                if (status >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        eos = true
                    }
                    if (bufferInfo.size != 0 && muxerStarted) {
                        val encodedData = encoder.getOutputBuffer(status)
                        if (encodedData != null) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }
                    }
                    encoder.releaseOutputBuffer(status, false)
                }
            }

            onProgress(1.0f, "Export Complete: ${outputFile.name}")
            return@withContext outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Video export failed", e)
            outputFile.delete()
            return@withContext null
        } finally {
            try {
                encoder?.stop()
                encoder?.release()
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
}
