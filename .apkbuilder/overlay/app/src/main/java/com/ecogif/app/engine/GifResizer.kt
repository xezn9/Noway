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
    ) {
        val info = GifDecoder.probe(data)
        val total = maxOf(1, info.frameCount)
        val decoder = GifDecoder(data)
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
                pendingPixels = BitmapOps.scalePixels(frame.pixels, decoder.width, decoder.height, targetW, targetH)
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
