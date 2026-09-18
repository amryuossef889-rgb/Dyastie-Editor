package com.example.dyastie.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

object WaveformExtractor {
    private const val TAG = "WaveformExtractor"
    private val memoryCache = ConcurrentHashMap<String, FloatArray>()

    suspend fun getWaveform(
        context: Context,
        uri: Uri,
        durationMs: Long,
        pointsCount: Int = 500
    ): FloatArray = withContext(Dispatchers.IO) {
        val cacheKey = "${uri}_$pointsCount"
        memoryCache[cacheKey]?.let { return@withContext it }

        // Check local disk cache
        val diskCacheFile = File(context.cacheDir, "waveform_${uri.hashCode()}_$pointsCount.bin")
        if (diskCacheFile.exists() && diskCacheFile.length() > 0) {
            try {
                val bytes = diskCacheFile.readBytes()
                val floats = FloatArray(bytes.size / 4)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
                memoryCache[cacheKey] = floats
                return@withContext floats
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading disk cache for $uri", e)
            }
        }

        val peaks = try {
            extractPeaks(context, uri, durationMs, pointsCount)
        } catch (e: Exception) {
            Log.w(TAG, "Audio decoding failed for $uri, falling back to heuristic waveform", e)
            generateFallbackWaveform(uri.toString(), pointsCount)
        }

        memoryCache[cacheKey] = peaks

        // Persist to disk cache
        try {
            val byteBuffer = ByteBuffer.allocate(peaks.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (p in peaks) byteBuffer.putFloat(p)
            diskCacheFile.writeBytes(byteBuffer.array())
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving waveform disk cache", e)
        }

        peaks
    }

    private fun extractPeaks(
        context: Context,
        uri: Uri,
        durationMs: Long,
        pointsCount: Int
    ): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            extractor.release()
            return generateFallbackWaveform(uri.toString(), pointsCount)
        }

        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }

        if (audioTrackIndex < 0 || audioFormat == null) {
            extractor.release()
            return generateFallbackWaveform(uri.toString(), pointsCount)
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""

        val rawPeaks = FloatArray(pointsCount)
        val stepUs = (durationMs * 1000L) / pointsCount.coerceAtLeast(1)

        try {
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            var sampleIdx = 0
            val maxSamplesToDecode = pointsCount * 2

            // Fast decimation loop: step through intervals and decode brief chunks
            for (i in 0 until pointsCount) {
                val targetTimeUs = i * stepUs
                extractor.seekTo(targetTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val inputIndex = decoder.dequeueInputBuffer(10000L)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize > 0) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isEos = true
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000L)
                if (outIndex >= 0) {
                    val outBuffer = decoder.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        var sum = 0.0
                        var count = 0
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val shortBuffer = outBuffer.asShortBuffer()
                        val step = (shortBuffer.remaining() / 16).coerceAtLeast(1)
                        var p = 0
                        while (p < shortBuffer.remaining()) {
                            val sample = shortBuffer.get(p).toDouble() / 32768.0
                            sum += sample * sample
                            count++
                            p += step
                        }

                        val rms = if (count > 0) sqrt(sum / count).toFloat() else 0.05f
                        rawPeaks[i] = (rms * 2.5f).coerceIn(0.04f, 1.0f)
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                } else {
                    rawPeaks[i] = if (i > 0) rawPeaks[i - 1] * 0.9f else 0.1f
                }

                if (isEos) break
            }

            decoder.stop()
            decoder.release()
        } catch (e: Exception) {
            Log.w(TAG, "Direct codec extraction interrupted: ${e.message}")
            extractor.release()
            return generateFallbackWaveform(uri.toString(), pointsCount)
        }

        extractor.release()

        // Normalize peaks
        val maxPeak = rawPeaks.maxOrNull()?.coerceAtLeast(0.1f) ?: 1.0f
        return FloatArray(pointsCount) { idx ->
            (rawPeaks[idx] / maxPeak).coerceIn(0.05f, 1.0f)
        }
    }

    fun generateFallbackWaveform(seedKey: String, count: Int): FloatArray {
        val random = java.util.Random(seedKey.hashCode().toLong())
        val peaks = FloatArray(count)
        var current = 0.3f
        for (i in 0 until count) {
            val delta = (random.nextFloat() - 0.48f) * 0.25f
            current = (current + delta).coerceIn(0.08f, 0.95f)
            // periodic gaming spikes (gunshots, punch, speech)
            if (i % 25 == 0 && random.nextFloat() > 0.3f) {
                current = (0.7f + random.nextFloat() * 0.3f).coerceIn(0.1f, 1.0f)
            }
            peaks[i] = current
        }
        return peaks
    }
}
