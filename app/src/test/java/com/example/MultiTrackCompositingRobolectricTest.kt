package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.dyastie.engine.VideoRenderPipeline
import com.example.dyastie.model.*
import com.example.dyastie.project.ProjectRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiTrackCompositingRobolectricTest {

    private lateinit var context: Context
    private lateinit var repository: ProjectRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val dir = File(context.filesDir, "dyastie_projects")
        dir.deleteRecursively()
        repository = ProjectRepository(context)
    }

    @Test
    fun `test multi-track composite frame rendering`() {
        // Track V1: Background video
        val clipV1 = TimelineClip(
            id = "clip_v1",
            mediaId = "media_gameplay",
            name = "Gameplay [V1]",
            trackId = "V1",
            isVideoTrack = true,
            timelineStartMs = 0L,
            timelineDurationMs = 10_000L,
            sourceInMs = 0L,
            sourceOutMs = 10_000L,
            transform = VideoTransform(scaleX = 1f, scaleY = 1f)
        )

        // Track V2: Picture-in-Picture Webcam
        val clipV2 = TimelineClip(
            id = "clip_v2",
            mediaId = "media_facecam",
            name = "Webcam [V2]",
            trackId = "V2",
            isVideoTrack = true,
            timelineStartMs = 0L,
            timelineDurationMs = 10_000L,
            sourceInMs = 0L,
            sourceOutMs = 10_000L,
            transform = VideoTransform(scaleX = 0.35f, scaleY = 0.35f, posX = 500f, posY = 300f)
        )

        val bmpV1 = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val bmpV2 = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }

        val rendered = VideoRenderPipeline.renderCompositeFrame(
            clipsWithBitmaps = listOf(clipV1 to bmpV1, clipV2 to bmpV2),
            outputWidth = 640,
            outputHeight = 360,
            timelineTimeMs = 2500L,
            previewQualityScale = 1.0f
        )

        assertNotNull(rendered)
        assertEquals(640, rendered.width)
        assertEquals(360, rendered.height)
    }

    @Test
    fun `test transitions rendering with crossfade and wipe`() {
        val clipWithTransitions = TimelineClip(
            id = "clip_trans",
            mediaId = "media_trans",
            name = "Transition Clip",
            trackId = "V1",
            isVideoTrack = true,
            timelineStartMs = 1000L,
            timelineDurationMs = 5000L,
            sourceInMs = 0L,
            sourceOutMs = 5000L,
            transitionIn = ClipTransition(type = TransitionType.CROSSFADE, durationMs = 1000L),
            transitionOut = ClipTransition(type = TransitionType.WIPE_RIGHT, durationMs = 1000L)
        )

        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }

        // Test during Transition In
        val frameIn = VideoRenderPipeline.renderCompositeFrame(
            clipsWithBitmaps = listOf(clipWithTransitions to bmp),
            outputWidth = 320,
            outputHeight = 180,
            timelineTimeMs = 1500L // 500ms into 1000ms transition in
        )
        assertNotNull(frameIn)

        // Test during Transition Out
        val frameOut = VideoRenderPipeline.renderCompositeFrame(
            clipsWithBitmaps = listOf(clipWithTransitions to bmp),
            outputWidth = 320,
            outputHeight = 180,
            timelineTimeMs = 5500L // 500ms into 1000ms transition out (ends at 6000ms)
        )
        assertNotNull(frameOut)
    }

    @Test
    fun `test keyframe and effect persistence roundtrip`() {
        val clip = TimelineClip(
            id = "clip_complex",
            mediaId = "media_01",
            name = "Complex Clip",
            trackId = "V1",
            isVideoTrack = true,
            timelineStartMs = 0L,
            timelineDurationMs = 5000L,
            sourceInMs = 0L,
            sourceOutMs = 5000L,
            colorGrading = ColorGrading(brightness = 0.2f, contrast = 1.3f, saturation = 1.5f, temperature = -0.2f),
            effects = listOf(VideoEffect(id = "fx_shake", type = EffectType.SHAKE, intensity = 0.8f)),
            keyframes = listOf(
                ClipKeyframe(id = "kf1", timeOffsetMs = 0L, property = KeyframeProperty.SCALE, value = 1.0f),
                ClipKeyframe(id = "kf2", timeOffsetMs = 2000L, property = KeyframeProperty.SCALE, value = 1.5f)
            ),
            transitionIn = ClipTransition(type = TransitionType.DIP_TO_BLACK, durationMs = 600L),
            transitionOut = ClipTransition(type = TransitionType.CROSSFADE, durationMs = 800L)
        )

        val project = Project(
            id = "test_transitions_kf",
            name = "Transitions Project",
            clips = listOf(clip)
        )

        val json = repository.projectToJson(project)
        val restored = repository.jsonToProject(json)

        val restoredClip = restored.clips.first()
        assertEquals(clip.id, restoredClip.id)
        assertEquals(0.2f, restoredClip.colorGrading.brightness, 0.01f)
        assertEquals(1.3f, restoredClip.colorGrading.contrast, 0.01f)
        assertEquals(1, restoredClip.effects.size)
        assertEquals(EffectType.SHAKE, restoredClip.effects.first().type)
        assertEquals(2, restoredClip.keyframes.size)
        assertEquals(KeyframeProperty.SCALE, restoredClip.keyframes[0].property)
        assertNotNull(restoredClip.transitionIn)
        assertEquals(TransitionType.DIP_TO_BLACK, restoredClip.transitionIn?.type)
        assertEquals(600L, restoredClip.transitionIn?.durationMs)
        assertNotNull(restoredClip.transitionOut)
        assertEquals(TransitionType.CROSSFADE, restoredClip.transitionOut?.type)
        assertEquals(800L, restoredClip.transitionOut?.durationMs)
    }
}
