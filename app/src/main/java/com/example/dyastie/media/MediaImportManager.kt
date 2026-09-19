package com.example.dyastie.media

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.util.Log
import com.example.dyastie.model.MediaItem
import com.example.dyastie.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MediaImportManager {
    private const val TAG = "MediaImportManager"

    data class ImportResult(
        val mediaItem: MediaItem,
        val wasCopiedToInternal: Boolean
    )

    fun isUriAccessible(context: Context, uri: Uri): Boolean {
        return try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: return false)
                file.exists() && file.canRead()
            } else {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun takePersistableUriPermission(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "file") return true
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed taking persistable URI permission for $uri: ${e.message}")
            false
        }
    }

    fun isRemovableOrExternalVolume(context: Context, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                val volume = storageManager?.getStorageVolume(uri)
                if (volume != null && volume.isRemovable) {
                    return true
                }
            } catch (e: Exception) {
                // Ignore fallback to path inspection
            }
        }
        val path = uri.path ?: ""
        return path.contains("/storage/") && !path.contains("/storage/emulated/")
    }

    suspend fun copyToInternalStorage(context: Context, sourceUri: Uri, originalName: String): Uri = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, "dyastie_media").apply {
            if (!exists()) mkdirs()
        }
        val sanitized = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(mediaDir, "${System.currentTimeMillis()}_$sanitized")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open input stream for $sourceUri")
        Uri.fromFile(destFile)
    }

    fun queryDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "media_${System.currentTimeMillis()}"
        }
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying display name for $uri: ${e.message}")
        }
        return uri.lastPathSegment ?: "media_${System.currentTimeMillis()}"
    }

    suspend fun extractMetadata(context: Context, uri: Uri, fileName: String): MediaItem = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val isImageMime = mimeType.startsWith("image/") ||
                fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) ||
                fileName.endsWith(".png", true) || fileName.endsWith(".webp", true)

        if (isImageMime) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val width = if (options.outWidth > 0) options.outWidth else 1920
            val height = if (options.outHeight > 0) options.outHeight else 1080

            return@withContext MediaItem(
                id = "media_${UUID.randomUUID().toString().take(8)}",
                uriString = uri.toString(),
                name = fileName,
                type = MediaType.IMAGE,
                durationMs = 5000L, // Still images have 5s default duration on timeline
                width = width,
                height = height,
                fps = 30f,
                hasAudio = false,
                hasVideo = true
            )
        }

        val retriever = MediaMetadataRetriever()
        var hasVideo = false
        var hasAudio = false
        var durationMs: Long = 0L
        var width = 1920
        var height = 1080
        var fps = 30f
        var channelCount = 2
        var sampleRate = 44100

        try {
            retriever.setDataSource(context, uri)

            hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
            hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null

            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                durationMs = durStr.toLongOrNull() ?: 0L
            }

            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (wStr != null && hStr != null) {
                val parsedW = wStr.toIntOrNull() ?: 1920
                val parsedH = hStr.toIntOrNull() ?: 1080
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) {
                    width = parsedH
                    height = parsedW
                } else {
                    width = parsedW
                    height = parsedH
                }
            }

            val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            if (fpsStr != null) {
                fps = fpsStr.toFloatOrNull() ?: 30f
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed on $uri: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }

        // Secondary verification & fallback with MediaExtractor to get precise channel count, sample rate, and duration
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (trackMime.startsWith("video/")) {
                    hasVideo = true
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) width = format.getInteger(MediaFormat.KEY_WIDTH)
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                    if (durationMs <= 0L && format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                    }
                } else if (trackMime.startsWith("audio/")) {
                    hasAudio = true
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (durationMs <= 0L && format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor failed on $uri: ${e.message}")
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                // ignore
            }
        }

        // Strictly disallow fake 10-second defaults: duration must be positive and real
        if (durationMs <= 0L) {
            throw IllegalArgumentException("Could not read duration for media file: $fileName. File may be corrupted or unsupported.")
        }

        val type = when {
            hasVideo -> MediaType.VIDEO
            hasAudio -> MediaType.AUDIO
            else -> MediaType.IMAGE
        }

        MediaItem(
            id = "media_${UUID.randomUUID().toString().take(8)}",
            uriString = uri.toString(),
            name = fileName,
            type = type,
            durationMs = durationMs,
            width = width,
            height = height,
            fps = fps,
            hasAudio = hasAudio,
            hasVideo = hasVideo,
            channelCount = channelCount,
            sampleRate = sampleRate,
            isSample = false,
            isOffline = false
        )
    }

    suspend fun importMedia(context: Context, rawUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(context, rawUri)
        val persisted = takePersistableUriPermission(context, rawUri)
        val onRemovable = isRemovableOrExternalVolume(context, rawUri)

        var finalUri = rawUri
        var wasCopied = false

        if (!persisted || onRemovable) {
            try {
                finalUri = copyToInternalStorage(context, rawUri, fileName)
                wasCopied = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed copying to internal media storage", e)
                if (!persisted) {
                    throw IllegalStateException("Cannot access or persist permission for: $fileName")
                }
            }
        }

        val item = extractMetadata(context, finalUri, fileName)
        ImportResult(item, wasCopied)
    }
}
