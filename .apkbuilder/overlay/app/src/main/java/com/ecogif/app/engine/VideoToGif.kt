package com.ecogif.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.ensureActive
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

class VideoInfo(val width: Int, val height: Int, val durationMs: Long, val rotation: Int)

object VideoToGif {

    fun probe(context: Context, uri: Uri): VideoInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (w <= 0 || h <= 0 || d <= 0) throw IllegalArgumentException("Unsupported video")
            // report the size the way the video is displayed
            return if (rot == 90 || rot == 270) VideoInfo(h, w, d, rot) else VideoInfo(w, h, d, rot)
        } finally {
            r.release()
        }
    }

    fun thumbnail(context: Context, uri: Uri): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }

    /** Output height for a given width, keeping the video's aspect ratio. */
    fun heightFor(info: VideoInfo, width: Int): Int =
        maxOf(1, Math.round(width.toFloat() * info.height / info.width))

    suspend fun convert(
        context: Context,
        uri: Uri,
        out: OutputStream,
        info: VideoInfo,
        startMs: Long,
        lengthMs: Long,
        fps: Int,
        targetW: Int,
        dither: Boolean,
        onProgress: (Float) -> Unit,
    ) {
        val targetH = heightFor(info, targetW)
        val frameCount = maxOf(1, (lengthMs * fps / 1000L).toInt())
        val stepMs = 1000f / fps
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val encoder = GifEncoder(out, targetW, targetH)
            encoder.start()

            val canScaleInDecoder = info.rotation == 0 || info.rotation == 180
            var timeAccum = 0f
            for (i in 0 until frameCount) {
                coroutineContext.ensureActive()
                val timeMs = (startMs + (i * stepMs)).toLong().coerceAtMost(info.durationMs - 1)
                val timeUs = timeMs * 1000L

                var bmp: Bitmap? = if (canScaleInDecoder) {
                    retriever.getScaledFrameAtTime(
                        timeUs, MediaMetadataRetriever.OPTION_CLOSEST, targetW, targetH
                    )
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                }
                if (bmp == null) {
                    onProgress((i + 1f) / frameCount)
                    continue
                }
                val scaled = BitmapOps.scale(bmp, targetW, targetH)
                val pixels = IntArray(targetW * targetH)
                scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
                // video frames are opaque
                for (p in pixels.indices) pixels[p] = pixels[p] or (0xFF shl 24)
                if (scaled !== bmp) scaled.recycle()
                bmp.recycle()
                bmp = null

                // keep the total duration exact even when 1000/fps is not an integer
                timeAccum += stepMs
                val delay = timeAccum.toInt()
                timeAccum -= delay
                encoder.addFrame(pixels, delay, dither)
                onProgress((i + 1f) / frameCount)
            }
            encoder.finish()
        } finally {
            retriever.release()
        }
    }
}
