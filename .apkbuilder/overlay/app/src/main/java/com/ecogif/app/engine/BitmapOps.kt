package com.ecogif.app.engine

import android.graphics.Bitmap

object BitmapOps {

    /** Scales with repeated halving for big reductions (much less aliasing than one big step). */
    fun scale(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        if (src.width == dstW && src.height == dstH) return src
        var cur = src
        while (cur.width / 2 >= dstW && cur.height / 2 >= dstH) {
            val half = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = half
        }
        val result = Bitmap.createScaledBitmap(cur, dstW, dstH, true)
        if (cur !== src && cur !== result) cur.recycle()
        return result
    }

    /** ARGB pixels -> scaled ARGB pixels. */
    fun scalePixels(pixels: IntArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): IntArray {
        if (srcW == dstW && srcH == dstH) return pixels
        val bmp = Bitmap.createBitmap(pixels, srcW, srcH, Bitmap.Config.ARGB_8888)
        val scaled = scale(bmp, dstW, dstH)
        val out = IntArray(dstW * dstH)
        scaled.getPixels(out, 0, dstW, 0, 0, dstW, dstH)
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        return out
    }

    fun toPixels(bmp: Bitmap): IntArray {
        val out = IntArray(bmp.width * bmp.height)
        bmp.getPixels(out, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return out
    }

    fun firstFrame(gif: ByteArray): Bitmap? = try {
        val dec = GifDecoder(gif)
        dec.readFrame()?.let { Bitmap.createBitmap(it.pixels, dec.width, dec.height, Bitmap.Config.ARGB_8888) }
    } catch (e: Exception) {
        null
    }
}
