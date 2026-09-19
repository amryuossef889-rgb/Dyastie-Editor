package com.example

import com.example.dyastie.audio.AudioSyncAssistant
import com.example.dyastie.model.*
import com.example.dyastie.viewmodel.ProxyResolution
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DyastieEditorLogicTest {

    @Test
    fun `test timeline clip time mapping`() {
        val clip = TimelineClip(
            id = "clip_1",
            mediaId = "media_1",
            name = "Test Clip",
            trackId = "V1",
            isVideoTrack = true,
            timelineStartMs = 2000L,
            timelineDurationMs = 5000L,
            sourceInMs = 1000L,
            sourceOutMs = 6000L,
            speed = 1.0f
        )

        assertTrue(clip.containsTime(2000L))
        assertTrue(clip.containsTime(4500L))
        assertFalse(clip.containsTime(1999L))
        assertFalse(clip.containsTime(7001L))

        assertEquals(1000L, clip.mapTimelineToSourceTime(2000L))
        assertEquals(3500L, clip.mapTimelineToSourceTime(4500L))
    }

    @Test
    fun `test audio sync calculation on synthetic peak waveforms`() = runBlocking {
        val refClip = TimelineClip(
            id = "ref_a1",
            mediaId = "m1",
            name = "Gameplay Audio",
            trackId = "A1",
            isVideoTrack = false,
            timelineStartMs = 0L,
            timelineDurationMs = 10_000L,
            sourceInMs = 0L,
            sourceOutMs = 10_000L
        )

        val targetClip = TimelineClip(
            id = "tgt_a2",
            mediaId = "m2",
            name = "External Voice Mic",
            trackId = "A2",
            isVideoTrack = false,
            timelineStartMs = 0L,
            timelineDurationMs = 10_000L,
            sourceInMs = 0L,
            sourceOutMs = 10_000L
        )

        val size = 500
        val refWave = FloatArray(size) { 0.2f }
        val tgtWave = FloatArray(size) { 0.2f }

        // Peak at 200 in ref, peak at 210 in target
        refWave[200] = 0.95f
        refWave[201] = 0.85f
        tgtWave[210] = 0.95f
        tgtWave[211] = 0.85f

        val result = AudioSyncAssistant.calculateSyncOffset(refClip, refWave, targetClip, tgtWave)
        assertNotNull(result)
        assertEquals("Gameplay Audio", result.referenceClipName)
        assertEquals("External Voice Mic", result.targetClipName)
        assertTrue(result.confidence > 0f)
    }

    @Test
    fun `test keyframe linear interpolation`() {
        val kfs = listOf(
            ClipKeyframe("kf1", 0L, KeyframeProperty.SCALE, 1.0f),
            ClipKeyframe("kf2", 1000L, KeyframeProperty.SCALE, 2.0f)
        )

        // Before start
        assertEquals(1.0f, ClipKeyframe.interpolate(kfs, KeyframeProperty.SCALE, -100L, 1.0f), 0.001f)
        // Midpoint
        assertEquals(1.5f, ClipKeyframe.interpolate(kfs, KeyframeProperty.SCALE, 500L, 1.0f), 0.001f)
        // At end
        assertEquals(2.0f, ClipKeyframe.interpolate(kfs, KeyframeProperty.SCALE, 1000L, 1.0f), 0.001f)
        // After end
        assertEquals(2.0f, ClipKeyframe.interpolate(kfs, KeyframeProperty.SCALE, 1500L, 1.0f), 0.001f)
    }

    @Test
    fun `test proxy resolutions`() {
        assertEquals(1.0f, ProxyResolution.ORIGINAL.scale, 0.001f)
        assertTrue(ProxyResolution.PROXY_720P.scale < 1.0f)
        assertTrue(ProxyResolution.PROXY_480P.scale < ProxyResolution.PROXY_720P.scale)
    }

    @Test
    fun `test project duration computation`() {
        val project = Project.createEmpty("Test Project")
        assertTrue(project.durationMs >= 10_000L)
    }
}
