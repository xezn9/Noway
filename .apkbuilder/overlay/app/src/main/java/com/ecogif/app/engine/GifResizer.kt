package com.ecogif.app.engine

import kotlinx.coroutines.ensureActive
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

object GifResizer {

    /**
     * @param ecoMode drops every second frame (and doubles the delay of the kept one):
     *                roughly half the file size, same duration.
     */
    suspend fun resize(
        data: ByteArray,
        out: OutputStream,
        targetW: Int,
        targetH: Int,
        ecoMode: Boolean,
        dither: Boolean,
        onProgress: (Float) -> Unit,
        cropX: Int = 0,
        cropY: Int = 0,
        cropW: Int = -1,
        cropH: Int = -1,
    ) {
        val info = GifDecoder.probe(data)
        val total = maxOf(1, info.frameCount)
        val decoder = GifDecoder(data)
        val srcW = decoder.width
        val srcH = decoder.height
        val cx = cropX.coerceIn(0, srcW - 1)
        val cy = cropY.coerceIn(0, srcH - 1)
        val cw = (if (cropW <= 0) srcW else cropW).coerceIn(1, srcW - cx)
        val ch = (if (cropH <= 0) srcH else cropH).coerceIn(1, srcH - cy)
        val encoder = GifEncoder(out, targetW, targetH)
        encoder.start()

        var pendingPixels: IntArray? = null
        var pendingDelay = 0
        var index = 0

        while (true) {
            coroutineContext.ensureActive()
            val frame = decoder.readFrame() ?: break
            val keep = !ecoMode || index % 2 == 0
            if (keep) {
                pendingPixels?.let { encoder.addFrame(it, pendingDelay, dither) }
                val cropped = BitmapOps.cropPixels(frame.pixels, srcW, srcH, cx, cy, cw, ch)
                pendingPixels = BitmapOps.scalePixels(cropped, cw, ch, targetW, targetH)
                pendingDelay = frame.delayMs
            } else {
                pendingDelay += frame.delayMs
            }
            index++
            onProgress(index.toFloat() / total)
        }
        pendingPixels?.let { encoder.addFrame(it, pendingDelay, dither) }
        encoder.finish()
        onProgress(1f)
    }
}
