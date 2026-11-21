package com.ecogif.app.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Creates / finishes files in the public gallery folders (no storage permission needed on API 29+). */
object MediaSaver {

    enum class Kind(val mime: String, val ext: String, val dir: String) {
        GIF("image/gif", "gif", Environment.DIRECTORY_PICTURES),
        VIDEO("video/mp4", "mp4", Environment.DIRECTORY_MOVIES),
    }

    class Pending(val uri: Uri, val displayPath: String)

    fun create(context: Context, kind: Kind): Pending {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "EcoGif_$stamp.${kind.ext}"
        val relative = "${kind.dir}/EcoGif"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, kind.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = when (kind) {
            Kind.GIF -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Kind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create the output file")
        return Pending(uri, "$relative/$name")
    }

    fun publish(context: Context, pending: Pending) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        context.contentResolver.update(pending.uri, values, null, null)
    }

    fun discard(context: Context, pending: Pending) {
        try {
            context.contentResolver.delete(pending.uri, null, null)
        } catch (_: Exception) {
        }
    }

    fun sizeOf(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.SIZE)
                if (i >= 0 && !c.isNull(i)) return c.getLong(i)
            }
        }
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    }

    fun nameOf(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return c.getString(i) ?: ""
            }
        }
        return uri.lastPathSegment ?: ""
    }

    fun readAll(context: Context, uri: Uri, maxBytes: Long = 150L * 1024 * 1024): ByteArray {
        val size = sizeOf(context, uri)
        if (size > maxBytes) throw IllegalArgumentException("File is too large (max ${maxBytes / 1024 / 1024} MB)")
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not open the file")
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> String.format(Locale.US, "%.2f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
