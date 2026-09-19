package com.example.dyastie.project

import android.content.Context
import android.util.Log
import com.example.dyastie.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProjectRepository(private val context: Context) {
    private val projectsDir: File = File(context.filesDir, "dyastie_projects").apply {
        if (!exists()) mkdirs()
    }
    private val autosaveFile = File(projectsDir, "autosave_project.json")

    suspend fun saveProject(project: Project, isAutosave: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = projectToJson(project)
            val targetFile = if (isAutosave) autosaveFile else File(projectsDir, "${project.id}.json")
            val tempFile = File(projectsDir, "${targetFile.name}.tmp_${System.currentTimeMillis()}")

            // Atomic write: write to temp file then rename
            tempFile.writeText(json.toString(2))
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            true
        } catch (e: Exception) {
            Log.e("ProjectRepo", "Failed saving project", e)
            false
        }
    }

    suspend fun loadLatestProject(): Project? = withContext(Dispatchers.IO) {
        try {
            if (autosaveFile.exists() && autosaveFile.length() > 0) {
                val json = JSONObject(autosaveFile.readText())
                return@withContext jsonToProject(json)
            }
            val files = projectsDir.listFiles { _, name -> name.endsWith(".json") && name != "autosave_project.json" }
            val latestFile = files?.maxByOrNull { it.lastModified() }
            if (latestFile != null) {
                val json = JSONObject(latestFile.readText())
                return@withContext jsonToProject(json)
            }
        } catch (e: Exception) {
            Log.e("ProjectRepo", "Failed loading project", e)
        }
        null
    }

    suspend fun listSavedProjects(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Pair<String, String>>()
        val files = projectsDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        for (f in files) {
            try {
                val json = JSONObject(f.readText())
                val id = json.optString("id", f.nameWithoutExtension)
                val name = json.optString("name", "Untitled")
                list.add(id to name)
            } catch (e: Exception) {
                // ignore corrupted
            }
        }
        list
    }

    internal fun projectToJson(project: Project): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("id", project.id)
        root.put("name", project.name)
        root.put("createdAt", project.createdAt)
        root.put("modifiedAt", System.currentTimeMillis())
        root.put("fps", project.fps.toDouble())
        root.put("width", project.width)
        root.put("height", project.height)
        root.put("isShorts", project.isShorts)

        // Media items
        val mediaArr = JSONArray()
        for (item in project.mediaItems) {
            val m = JSONObject()
            m.put("id", item.id)
            m.put("uri", item.uriString)
            m.put("name", item.name)
            m.put("type", item.type.name)
            m.put("durationMs", item.durationMs)
            m.put("width", item.width)
            m.put("height", item.height)
            m.put("fps", item.fps.toDouble())
            m.put("hasAudio", item.hasAudio)
            m.put("hasVideo", item.hasVideo)
            m.put("sampleRate", item.sampleRate)
            m.put("channelCount", item.channelCount)
            m.put("isSample", item.isSample)
            m.put("isOffline", item.isOffline)
            mediaArr.put(m)
        }
        root.put("mediaItems", mediaArr)

        // Tracks
        val tracksArr = JSONArray()
        for (t in project.tracks) {
            val to = JSONObject()
            to.put("id", t.id)
            to.put("name", t.name)
            to.put("isVideo", t.isVideo)
            to.put("isMuted", t.isMuted)
            to.put("isSolo", t.isSolo)
            to.put("isLocked", t.isLocked)
            to.put("isHidden", t.isHidden)
            to.put("volume", t.volume.toDouble())
            tracksArr.put(to)
        }
        root.put("tracks", tracksArr)

        // Clips
        val clipsArr = JSONArray()
        for (c in project.clips) {
            val co = JSONObject()
            co.put("id", c.id)
            co.put("mediaId", c.mediaId)
            co.put("name", c.name)
            co.put("trackId", c.trackId)
            co.put("isVideoTrack", c.isVideoTrack)
            co.put("timelineStartMs", c.timelineStartMs)
            co.put("timelineDurationMs", c.timelineDurationMs)
            co.put("sourceInMs", c.sourceInMs)
            co.put("sourceOutMs", c.sourceOutMs)
            if (c.linkedClipId != null) co.put("linkedClipId", c.linkedClipId)
            if (c.groupId != null) co.put("groupId", c.groupId)
            co.put("isLocked", c.isLocked)
            co.put("isMuted", c.isMuted)
            co.put("speed", c.speed.toDouble())
            co.put("volume", c.volume.toDouble())
            co.put("pan", c.pan.toDouble())
            co.put("fadeInMs", c.fadeInMs)
            co.put("fadeOutMs", c.fadeOutMs)
            co.put("gainDb", c.gainDb.toDouble())

            // Transform
            val tr = JSONObject()
            tr.put("posX", c.transform.posX.toDouble())
            tr.put("posY", c.transform.posY.toDouble())
            tr.put("scaleX", c.transform.scaleX.toDouble())
            tr.put("scaleY", c.transform.scaleY.toDouble())
            tr.put("rotationDeg", c.transform.rotationDeg.toDouble())
            tr.put("opacity", c.transform.opacity.toDouble())
            co.put("transform", tr)

            // Color Grading
            val cg = JSONObject()
            cg.put("brightness", c.colorGrading.brightness.toDouble())
            cg.put("contrast", c.colorGrading.contrast.toDouble())
            cg.put("saturation", c.colorGrading.saturation.toDouble())
            cg.put("temperature", c.colorGrading.temperature.toDouble())
            co.put("colorGrading", cg)

            // Effects
            if (c.effects.isNotEmpty()) {
                val fxArr = JSONArray()
                for (fx in c.effects) {
                    val fo = JSONObject()
                    fo.put("id", fx.id)
                    fo.put("type", fx.type.name)
                    fo.put("isEnabled", fx.isEnabled)
                    fo.put("intensity", fx.intensity.toDouble())
                    fo.put("speed", fx.speed.toDouble())
                    fo.put("durationMs", fx.durationMs)
                    fxArr.put(fo)
                }
                co.put("effects", fxArr)
            }

            // Keyframes
            if (c.keyframes.isNotEmpty()) {
                val kfArr = JSONArray()
                for (kf in c.keyframes) {
                    val ko = JSONObject()
                    ko.put("id", kf.id)
                    ko.put("timeOffsetMs", kf.timeOffsetMs)
                    ko.put("property", kf.property.name)
                    ko.put("value", kf.value.toDouble())
                    ko.put("easing", kf.easing)
                    kfArr.put(ko)
                }
                co.put("keyframes", kfArr)
            }

            // Transitions
            c.transitionIn?.let { ti ->
                val tio = JSONObject()
                tio.put("type", ti.type.name)
                tio.put("durationMs", ti.durationMs)
                co.put("transitionIn", tio)
            }
            c.transitionOut?.let { to ->
                val too = JSONObject()
                too.put("type", to.type.name)
                too.put("durationMs", to.durationMs)
                co.put("transitionOut", too)
            }

            // Text overlay
            c.textOverlay?.let { text ->
                val txt = JSONObject()
                txt.put("id", text.id)
                txt.put("text", text.text)
                txt.put("fontSizeSp", text.fontSizeSp.toDouble())
                txt.put("isBold", text.isBold)
                txt.put("isItalic", text.isItalic)
                txt.put("textColor", text.textColor)
                txt.put("presetName", text.presetName)
                co.put("textOverlay", txt)
            }

            clipsArr.put(co)
        }
        root.put("clips", clipsArr)

        // Markers
        val markersArr = JSONArray()
        for (m in project.markers) {
            val mo = JSONObject()
            mo.put("id", m.id)
            mo.put("timeMs", m.timeMs)
            mo.put("label", m.label)
            mo.put("color", m.color)
            markersArr.put(mo)
        }
        root.put("markers", markersArr)

        return root
    }

    internal fun jsonToProject(json: JSONObject): Project {
        val schemaVersion = json.optInt("schemaVersion", 1)
        val id = json.optString("id", "proj_${System.currentTimeMillis()}")
        val name = json.optString("name", "Dyastie Gaming Project")
        val fps = json.optDouble("fps", 30.0).toFloat()
        val width = json.optInt("width", 1920)
        val height = json.optInt("height", 1080)
        val isShorts = json.optBoolean("isShorts", false)

        val mediaItems = mutableListOf<MediaItem>()
        val mediaArr = json.optJSONArray("mediaItems")
        if (mediaArr != null) {
            for (i in 0 until mediaArr.length()) {
                val m = mediaArr.getJSONObject(i)
                mediaItems.add(
                    MediaItem(
                        id = m.getString("id"),
                        uriString = m.getString("uri"),
                        name = m.getString("name"),
                        type = MediaType.valueOf(m.optString("type", "VIDEO")),
                        durationMs = m.optLong("durationMs", 10000L),
                        width = m.optInt("width", 1920),
                        height = m.optInt("height", 1080),
                        fps = m.optDouble("fps", 30.0).toFloat(),
                        hasAudio = m.optBoolean("hasAudio", true),
                        hasVideo = m.optBoolean("hasVideo", true),
                        sampleRate = m.optInt("sampleRate", 44100),
                        channelCount = m.optInt("channelCount", 2),
                        isSample = m.optBoolean("isSample", false),
                        isOffline = m.optBoolean("isOffline", false)
                    )
                )
            }
        }

        val tracks = mutableListOf<Track>()
        val tracksArr = json.optJSONArray("tracks")
        if (tracksArr != null && tracksArr.length() > 0) {
            for (i in 0 until tracksArr.length()) {
                val to = tracksArr.getJSONObject(i)
                tracks.add(
                    Track(
                        id = to.getString("id"),
                        name = to.getString("name"),
                        isVideo = to.getBoolean("isVideo"),
                        isMuted = to.optBoolean("isMuted", false),
                        isSolo = to.optBoolean("isSolo", false),
                        isLocked = to.optBoolean("isLocked", false),
                        isHidden = to.optBoolean("isHidden", false),
                        volume = to.optDouble("volume", 1.0).toFloat()
                    )
                )
            }
        } else {
            tracks.addAll(Track.defaultTracks())
        }

        val clips = mutableListOf<TimelineClip>()
        val clipsArr = json.optJSONArray("clips")
        if (clipsArr != null) {
            for (i in 0 until clipsArr.length()) {
                val co = clipsArr.getJSONObject(i)
                val trObj = co.optJSONObject("transform")
                val transform = if (trObj != null) {
                    VideoTransform(
                        posX = trObj.optDouble("posX", 0.0).toFloat(),
                        posY = trObj.optDouble("posY", 0.0).toFloat(),
                        scaleX = trObj.optDouble("scaleX", 1.0).toFloat(),
                        scaleY = trObj.optDouble("scaleY", 1.0).toFloat(),
                        rotationDeg = trObj.optDouble("rotationDeg", 0.0).toFloat(),
                        opacity = trObj.optDouble("opacity", 1.0).toFloat()
                    )
                } else VideoTransform()

                val cgObj = co.optJSONObject("colorGrading")
                val colorGrading = if (cgObj != null) {
                    ColorGrading(
                        brightness = cgObj.optDouble("brightness", 0.0).toFloat(),
                        contrast = cgObj.optDouble("contrast", 1.0).toFloat(),
                        saturation = cgObj.optDouble("saturation", 1.0).toFloat(),
                        temperature = cgObj.optDouble("temperature", 0.0).toFloat()
                    )
                } else ColorGrading()

                val effectsList = mutableListOf<VideoEffect>()
                val fxArr = co.optJSONArray("effects")
                if (fxArr != null) {
                    for (k in 0 until fxArr.length()) {
                        val fo = fxArr.getJSONObject(k)
                        try {
                            effectsList.add(
                                VideoEffect(
                                    id = fo.getString("id"),
                                    type = EffectType.valueOf(fo.getString("type")),
                                    isEnabled = fo.optBoolean("isEnabled", true),
                                    intensity = fo.optDouble("intensity", 1.0).toFloat(),
                                    speed = fo.optDouble("speed", 1.0).toFloat(),
                                    durationMs = fo.optLong("durationMs", 1000L)
                                )
                            )
                        } catch (e: Exception) {}
                    }
                }

                val keyframesList = mutableListOf<ClipKeyframe>()
                val kfArr = co.optJSONArray("keyframes")
                if (kfArr != null) {
                    for (k in 0 until kfArr.length()) {
                        val ko = kfArr.getJSONObject(k)
                        try {
                            keyframesList.add(
                                ClipKeyframe(
                                    id = ko.getString("id"),
                                    timeOffsetMs = ko.getLong("timeOffsetMs"),
                                    property = KeyframeProperty.valueOf(ko.getString("property")),
                                    value = ko.getDouble("value").toFloat(),
                                    easing = ko.optString("easing", "LINEAR")
                                )
                            )
                        } catch (e: Exception) {}
                    }
                }

                val tiObj = co.optJSONObject("transitionIn")
                val transitionIn = if (tiObj != null) {
                    try {
                        ClipTransition(
                            type = TransitionType.valueOf(tiObj.getString("type")),
                            durationMs = tiObj.optLong("durationMs", 500L)
                        )
                    } catch (e: Exception) { null }
                } else null

                val toObj = co.optJSONObject("transitionOut")
                val transitionOut = if (toObj != null) {
                    try {
                        ClipTransition(
                            type = TransitionType.valueOf(toObj.getString("type")),
                            durationMs = toObj.optLong("durationMs", 500L)
                        )
                    } catch (e: Exception) { null }
                } else null

                val txtObj = co.optJSONObject("textOverlay")
                val textOverlay = if (txtObj != null) {
                    TextOverlay(
                        id = txtObj.getString("id"),
                        text = txtObj.getString("text"),
                        fontSizeSp = txtObj.optDouble("fontSizeSp", 28.0).toFloat(),
                        isBold = txtObj.optBoolean("isBold", true),
                        isItalic = txtObj.optBoolean("isItalic", false),
                        textColor = txtObj.optLong("textColor", 0xFFFFFFFF),
                        presetName = txtObj.optString("presetName", "CUSTOM")
                    )
                } else null

                clips.add(
                    TimelineClip(
                        id = co.getString("id"),
                        mediaId = co.getString("mediaId"),
                        name = co.getString("name"),
                        trackId = co.getString("trackId"),
                        isVideoTrack = co.getBoolean("isVideoTrack"),
                        timelineStartMs = co.getLong("timelineStartMs"),
                        timelineDurationMs = co.getLong("timelineDurationMs"),
                        sourceInMs = co.optLong("sourceInMs", 0L),
                        sourceOutMs = co.optLong("sourceOutMs", co.getLong("timelineDurationMs")),
                        linkedClipId = co.optString("linkedClipId").takeIf { it.isNotEmpty() },
                        groupId = co.optString("groupId").takeIf { it.isNotEmpty() },
                        isLocked = co.optBoolean("isLocked", false),
                        isMuted = co.optBoolean("isMuted", false),
                        speed = co.optDouble("speed", 1.0).toFloat(),
                        volume = co.optDouble("volume", 1.0).toFloat(),
                        pan = co.optDouble("pan", 0.0).toFloat(),
                        fadeInMs = co.optLong("fadeInMs", 0L),
                        fadeOutMs = co.optLong("fadeOutMs", 0L),
                        gainDb = co.optDouble("gainDb", 0.0).toFloat(),
                        transform = transform,
                        colorGrading = colorGrading,
                        effects = effectsList,
                        keyframes = keyframesList,
                        textOverlay = textOverlay,
                        transitionIn = transitionIn,
                        transitionOut = transitionOut
                    )
                )
            }
        }

        val markers = mutableListOf<TimelineMarker>()
        val markersArr = json.optJSONArray("markers")
        if (markersArr != null) {
            for (i in 0 until markersArr.length()) {
                val mo = markersArr.getJSONObject(i)
                markers.add(
                    TimelineMarker(
                        id = mo.getString("id"),
                        timeMs = mo.getLong("timeMs"),
                        label = mo.getString("label"),
                        color = mo.optLong("color", 0xFFFF4444)
                    )
                )
            }
        }

        return Project(
            id = id,
            name = name,
            fps = fps,
            width = width,
            height = height,
            isShorts = isShorts,
            tracks = tracks,
            clips = clips,
            markers = markers,
            mediaItems = mediaItems
        )
    }
}
