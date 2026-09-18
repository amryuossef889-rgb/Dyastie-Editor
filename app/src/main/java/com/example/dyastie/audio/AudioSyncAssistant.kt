package com.example.dyastie.audio

import com.example.dyastie.model.TimelineClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class AudioSyncResult(
    val suggestedOffsetMs: Long,
    val confidence: Float, // 0.0 to 1.0
    val referenceClipName: String,
    val targetClipName: String,
    val referenceTrackId: String,
    val targetTrackId: String
)

object AudioSyncAssistant {

    suspend fun calculateSyncOffset(
        referenceClip: TimelineClip,
        referenceWaveform: FloatArray,
        targetClip: TimelineClip,
        targetWaveform: FloatArray
    ): AudioSyncResult = withContext(Dispatchers.Default) {
        if (referenceWaveform.isEmpty() || targetWaveform.isEmpty()) {
            return@withContext AudioSyncResult(
                suggestedOffsetMs = 0L,
                confidence = 0f,
                referenceClipName = referenceClip.name,
                targetClipName = targetClip.name,
                referenceTrackId = referenceClip.trackId,
                targetTrackId = targetClip.trackId
            )
        }

        // Cross-correlation on amplitude envelopes / transients
        val msPerRefPoint = (referenceClip.timelineDurationMs.toFloat() / referenceWaveform.size).coerceAtLeast(1f)
        val msPerTargetPoint = (targetClip.timelineDurationMs.toFloat() / targetWaveform.size).coerceAtLeast(1f)

        // Resample both to a common grid (e.g. 20ms per bucket)
        val bucketMs = 20.0f
        val refBucketsCount = (referenceClip.timelineDurationMs / bucketMs).toInt().coerceAtLeast(10)
        val targetBucketsCount = (targetClip.timelineDurationMs / bucketMs).toInt().coerceAtLeast(10)

        val refGrid = FloatArray(refBucketsCount) { i ->
            val origIdx = ((i * bucketMs / referenceClip.timelineDurationMs) * referenceWaveform.size).toInt()
                .coerceIn(0, referenceWaveform.size - 1)
            referenceWaveform[origIdx]
        }

        val targetGrid = FloatArray(targetBucketsCount) { i ->
            val origIdx = ((i * bucketMs / targetClip.timelineDurationMs) * targetWaveform.size).toInt()
                .coerceIn(0, targetWaveform.size - 1)
            targetWaveform[origIdx]
        }

        // Search window: +/- 5 seconds (5000ms / 20ms = 250 buckets)
        val maxShiftBuckets = min(250, min(refBucketsCount / 2, targetBucketsCount / 2)).coerceAtLeast(10)

        var bestShiftBuckets = 0
        var maxCorrelation = -1.0f

        for (shift in -maxShiftBuckets..maxShiftBuckets) {
            var dot = 0.0f
            var norm1 = 0.0f
            var norm2 = 0.0f
            var overlapCount = 0

            val startRef = max(0, -shift)
            val endRef = min(refBucketsCount, targetBucketsCount - shift)

            for (r in startRef until endRef) {
                val t = r + shift
                if (t in 0 until targetBucketsCount) {
                    val v1 = refGrid[r]
                    val v2 = targetGrid[t]
                    dot += v1 * v2
                    norm1 += v1 * v1
                    norm2 += v2 * v2
                    overlapCount++
                }
            }

            if (overlapCount > 30 && norm1 > 0f && norm2 > 0f) {
                val corr = dot / (kotlin.math.sqrt(norm1.toDouble()) * kotlin.math.sqrt(norm2.toDouble())).toFloat()
                if (corr > maxCorrelation) {
                    maxCorrelation = corr
                    bestShiftBuckets = shift
                }
            }
        }

        val suggestedOffsetMs = (bestShiftBuckets * bucketMs).toLong()
        val confidence = (maxCorrelation).coerceIn(0.15f, 0.98f)

        AudioSyncResult(
            suggestedOffsetMs = suggestedOffsetMs,
            confidence = confidence,
            referenceClipName = referenceClip.name,
            targetClipName = targetClip.name,
            referenceTrackId = referenceClip.trackId,
            targetTrackId = targetClip.trackId
        )
    }
}
