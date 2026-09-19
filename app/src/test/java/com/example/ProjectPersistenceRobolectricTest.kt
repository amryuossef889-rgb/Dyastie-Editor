package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dyastie.model.*
import com.example.dyastie.project.ProjectRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectPersistenceRobolectricTest {

    private lateinit var context: Context
    private lateinit var repository: ProjectRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clean up test projects directory
        val dir = File(context.filesDir, "dyastie_projects")
        dir.deleteRecursively()
        repository = ProjectRepository(context)
    }

    @Test
    fun `test schema versioning and json serialization roundtrip`() {
        val mediaItem = MediaItem(
            id = "media_gameplay_01",
            uriString = "content://media/external/video/media/1234",
            name = "Gameplay_Apex.mp4",
            type = MediaType.VIDEO,
            durationMs = 60_000L,
            width = 1920,
            height = 1080,
            fps = 60f,
            sampleRate = 48000,
            channelCount = 2,
            hasAudio = true,
            hasVideo = true,
            isOffline = true,
            isSample = false
        )

        val vClip = TimelineClip(
            id = "clip_v1",
            mediaId = mediaItem.id,
            name = "Gameplay [V]",
            trackId = "V1",
            isVideoTrack = true,
            timelineStartMs = 1000L,
            timelineDurationMs = 10_000L,
            sourceInMs = 5000L,
            sourceOutMs = 15_000L,
            linkedClipId = "clip_a1",
            groupId = "group_gameplay_01"
        )

        val aClip = TimelineClip(
            id = "clip_a1",
            mediaId = mediaItem.id,
            name = "Gameplay [A]",
            trackId = "A1",
            isVideoTrack = false,
            timelineStartMs = 1000L,
            timelineDurationMs = 10_000L,
            sourceInMs = 5000L,
            sourceOutMs = 15_000L,
            linkedClipId = "clip_v1",
            groupId = "group_gameplay_01"
        )

        val project = Project(
            id = "test_project_schema_1",
            name = "Schema Version Test",
            fps = 60f,
            width = 1920,
            height = 1080,
            tracks = listOf(
                Track(id = "V1", name = "V1", isVideo = true, heightDp = 64),
                Track(id = "A1", name = "A1", isVideo = false, heightDp = 48)
            ),
            clips = listOf(vClip, aClip),
            mediaItems = listOf(mediaItem)
        )

        val json = repository.projectToJson(project)

        // Verify schema version is present
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("test_project_schema_1", json.getString("id"))

        // Deserialize back
        val restored = repository.jsonToProject(json)
        assertEquals(project.id, restored.id)
        assertEquals(project.name, restored.name)
        assertEquals(1, restored.mediaItems.size)

        val restoredMedia = restored.mediaItems.first()
        assertEquals(mediaItem.id, restoredMedia.id)
        assertEquals(mediaItem.sampleRate, restoredMedia.sampleRate)
        assertEquals(mediaItem.channelCount, restoredMedia.channelCount)
        assertTrue(restoredMedia.isOffline)

        assertEquals(2, restored.clips.size)
        val restoredV = restored.clips.find { it.id == "clip_v1" }!!
        val restoredA = restored.clips.find { it.id == "clip_a1" }!!
        assertEquals("clip_a1", restoredV.linkedClipId)
        assertEquals("clip_v1", restoredA.linkedClipId)
        assertEquals("group_gameplay_01", restoredV.groupId)
        assertEquals("group_gameplay_01", restoredA.groupId)
    }

    @Test
    fun `test atomic file saving and loading latest project`() = runBlocking {
        val project = Project.createEmpty("Atomic Test Project")
        val saved = repository.saveProject(project, isAutosave = false)
        assertTrue(saved)

        // Verify project file exists and no leftover tmp file
        val dir = File(context.filesDir, "dyastie_projects")
        val tmpFiles = dir.listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals(0, tmpFiles.size)

        val loaded = repository.loadLatestProject()
        assertNotNull(loaded)
        assertEquals(project.id, loaded!!.id)
        assertEquals(project.name, loaded.name)
    }

    @Test
    fun `test autosave atomic saving and loading precedence`() = runBlocking {
        val normalProject = Project.createEmpty("Normal Save")
        val autosaveProject = Project.createEmpty("Autosave Save").copy(
            id = "autosave_proj_id",
            name = "Autosaved State"
        )

        repository.saveProject(normalProject, isAutosave = false)
        repository.saveProject(autosaveProject, isAutosave = true)

        // loadLatestProject prioritizes autosave file when available
        val latest = repository.loadLatestProject()
        assertNotNull(latest)
        assertEquals("autosave_proj_id", latest!!.id)
        assertEquals("Autosaved State", latest.name)
    }
}
