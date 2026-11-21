package com.ecogif.app.engine

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Streaming GIF89a encoder: median-cut palette per frame (local colour table),
 * optional Floyd-Steinberg dithering, LZW compression.
 */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    private val loopCount: Int = 0, // 0 = forever
) {
    // reusable buffers for the quantizer
    private val hist = IntArray(32768)
    private val sumR = IntArray(32768)
    private val sumG = IntArray(32768)
    private val sumB = IntArray(32768)
    private val lut = IntArray(32768)

    private var started = false

    fun start() {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        out.write(0x70) // no global table, 8 bit colour resolution
        out.write(0)
        out.write(0)
        // NETSCAPE looping extension
        out.write(0x21); out.write(0xFF); out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3); out.write(1)
        writeShort(loopCount)
        out.write(0)
        started = true
    }

    fun addFrame(argb: IntArray, delayMs: Int, dither: Boolean) {
        check(started) { "call start() first" }
        require(argb.size == width * height)

        var hasTransparent = false
        for (p in argb) {
            if ((p ushr 24) < 128) { hasTransparent = true; break }
        }
        val maxColors = if (hasTransparent) 255 else 256
        val palette = buildPalette(argb, maxColors)
        val transparentIndex = if (hasTransparent) palette.size else -1
        val tableEntries = palette.size + if (hasTransparent) 1 else 0

        val indices = mapPixels(argb, palette, transparentIndex, dither)

        var bits = 1
        while ((1 shl bits) < tableEntries) bits++

        // Graphic control extension
        val cs = maxOf(2, (delayMs + 5) / 10)
        out.write(0x21); out.write(0xF9); out.write(4)
        val disposal = if (hasTransparent) 2 else 1
        out.write((disposal shl 2) or (if (hasTransparent) 1 else 0))
        writeShort(cs)
        out.write(if (hasTransparent) transparentIndex else 0)
        out.write(0)

        // Image descriptor + local colour table
        out.write(0x2C)
        writeShort(0); writeShort(0); writeShort(width); writeShort(height)
        out.write(0x80 or (bits - 1))
        val size = 1 shl bits
        for (i in 0 until size) {
            val c = if (i < palette.size) palette[i] else 0
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }

        val minCode = maxOf(2, bits)
        out.write(minCode)
        val packed = lzw(indices, minCode)
        var off = 0
        while (off < packed.size) {
            val n = minOf(255, packed.size - off)
            out.write(n)
            out.write(packed, off, n)
            off += n
        }
        out.write(0)
    }

    fun finish() {
        out.write(0x3B)
        out.flush()
    }

    private fun writeShort(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    // ------------------------------------------------------------------ quantizer

    private fun buildPalette(argb: IntArray, maxColors: Int): IntArray {
        hist.fill(0); sumR.fill(0); sumG.fill(0); sumB.fill(0)
        for (p in argb) {
            if ((p ushr 24) < 128) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val k = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            hist[k]++
            sumR[k] += r; sumG[k] += g; sumB[k] += b
        }
        var n = 0
        for (k in 0 until 32768) if (hist[k] > 0) n++
        if (n == 0) return intArrayOf(0x000000)
        val keys = IntArray(n)
        var i = 0
        for (k in 0 until 32768) if (hist[k] > 0) keys[i++] = k

        // boxes as [start, end) ranges inside keys
        val starts = IntArray(maxColors)
        val ends = IntArray(maxColors)
        var boxes = 1
        starts[0] = 0; ends[0] = n

        while (boxes < maxColors) {
            // pick the box with the most pixels that can still be split
            var best = -1
            var bestScore = -1L
            for (b in 0 until boxes) {
                if (ends[b] - starts[b] < 2) continue
                var pop = 0L
                for (j in starts[b] until ends[b]) pop += hist[keys[j]]
                if (pop > bestScore) { bestScore = pop; best = b }
            }
            if (best < 0) break

            val s = starts[best]
            val e = ends[best]
            // find the channel with the widest range
            var minR = 31; var maxR = 0; var minG = 31; var maxG = 0; var minB = 31; var maxB = 0
            for (j in s until e) {
                val k = keys[j]
                val r = k shr 10; val g = (k shr 5) and 31; val b = k and 31
                if (r < minR) minR = r; if (r > maxR) maxR = r
                if (g < minG) minG = g; if (g > maxG) maxG = g
                if (b < minB) minB = b; if (b > maxB) maxB = b
            }
            val rr = maxR - minR; val gr = maxG - minG; val br = maxB - minB
            val shift = if (gr >= rr && gr >= br) 5 else if (rr >= br) 10 else 0

            // sort by that channel (pack channel value on top of the key, sort ints)
            for (j in s until e) {
                val k = keys[j]
                keys[j] = (((k shr shift) and 31) shl 15) or k
            }
            java.util.Arrays.sort(keys, s, e)
            for (j in s until e) keys[j] = keys[j] and 0x7FFF

            // split at the weighted median
            var total = 0L
            for (j in s until e) total += hist[keys[j]]
            var acc = 0L
            var cut = s + 1
            for (j in s until e - 1) {
                acc += hist[keys[j]]
                cut = j + 1
                if (acc * 2 >= total) break
            }
            starts[boxes] = cut
            ends[boxes] = e
            ends[best] = cut
            boxes++
        }

        val palette = IntArray(boxes)
        for (b in 0 until boxes) {
            var cnt = 0L; var r = 0L; var g = 0L; var bl = 0L
            for (j in starts[b] until ends[b]) {
                val k = keys[j]
                cnt += hist[k]; r += sumR[k]; g += sumG[k]; bl += sumB[k]
            }
            if (cnt == 0L) cnt = 1
            palette[b] = (((r / cnt).toInt()) shl 16) or (((g / cnt).toInt()) shl 8) or (bl / cnt).toInt()
        }
        return palette
    }

    private fun nearest(palette: IntArray, r: Int, g: Int, b: Int): Int {
        var best = 0
        var bestD = Int.MAX_VALUE
        for (i in palette.indices) {
            val c = palette[i]
            val dr = r - ((c shr 16) and 0xFF)
            val dg = g - ((c shr 8) and 0xFF)
            val db = b - (c and 0xFF)
            val d = dr * dr * 3 + dg * dg * 4 + db * db * 2
            if (d < bestD) { bestD = d; best = i; if (d == 0) break }
        }
        return best
    }

    private fun mapPixels(argb: IntArray, palette: IntArray, transparentIndex: Int, dither: Boolean): ByteArray {
        lut.fill(-1)
        val res = ByteArray(argb.size)

        if (!dither) {
            for (i in argb.indices) {
                val p = argb[i]
                if ((p ushr 24) < 128) { res[i] = transparentIndex.toByte(); continue }
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                val k = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                var idx = lut[k]
                if (idx < 0) { idx = nearest(palette, r, g, b); lut[k] = idx }
                res[i] = idx.toByte()
            }
            return res
        }

        // Floyd-Steinberg
        var errR = IntArray(width + 2); var errG = IntArray(width + 2); var errB = IntArray(width + 2)
        var nextR = IntArray(width + 2); var nextG = IntArray(width + 2); var nextB = IntArray(width + 2)
        for (y in 0 until height) {
            nextR.fill(0); nextG.fill(0); nextB.fill(0)
            for (x in 0 until width) {
                val i = y * width + x
                val p = argb[i]
                if ((p ushr 24) < 128) { res[i] = transparentIndex.toByte(); continue }
                val r = (((p shr 16) and 0xFF) + errR[x + 1] / 16).coerceIn(0, 255)
                val g = (((p shr 8) and 0xFF) + errG[x + 1] / 16).coerceIn(0, 255)
                val b = ((p and 0xFF) + errB[x + 1] / 16).coerceIn(0, 255)
                val k = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                var idx = lut[k]
                if (idx < 0) { idx = nearest(palette, r, g, b); lut[k] = idx }
                res[i] = idx.toByte()
                val c = palette[idx]
                val er = r - ((c shr 16) and 0xFF)
                val eg = g - ((c shr 8) and 0xFF)
                val eb = b - (c and 0xFF)
                errR[x + 2] += er * 7; errG[x + 2] += eg * 7; errB[x + 2] += eb * 7
                nextR[x] += er * 3; nextG[x] += eg * 3; nextB[x] += eb * 3
                nextR[x + 1] += er * 5; nextG[x + 1] += eg * 5; nextB[x + 1] += eb * 5
                nextR[x + 2] += er; nextG[x + 2] += eg; nextB[x + 2] += eb
            }
            val tr = errR; errR = nextR; nextR = tr
            val tg = errG; errG = nextG; nextG = tg
            val tb = errB; errB = nextB; nextB = tb
        }
        return res
    }

    // ------------------------------------------------------------------ LZW

    private fun lzw(pixels: ByteArray, minCodeSize: Int): ByteArray {
        val buf = ByteArrayOutputStream(pixels.size / 3 + 64)
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        var codeSize = minCodeSize + 1
        var next = eoi + 1

        val hSize = 5003
        val hKeys = IntArray(hSize) { -1 }
        val hCodes = IntArray(hSize)

        var cur = 0
        var curBits = 0
        fun emit(code: Int) {
            cur = cur or (code shl curBits)
            curBits += codeSize
            while (curBits >= 8) {
                buf.write(cur and 0xFF)
                cur = cur ushr 8
                curBits -= 8
            }
        }

        emit(clear)
        var prefix = pixels[0].toInt() and 0xFF
        for (i in 1 until pixels.size) {
            val c = pixels[i].toInt() and 0xFF
            val key = (prefix shl 8) or c
            var h = ((prefix shl 5) xor c) % hSize
            var found = -1
            while (hKeys[h] != -1) {
                if (hKeys[h] == key) { found = hCodes[h]; break }
                h++
                if (h == hSize) h = 0
            }
            if (found >= 0) {
                prefix = found
                continue
            }
            emit(prefix)
            if (next < 4096) {
                hKeys[h] = key
                hCodes[h] = next
                next++
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                emit(clear)
                java.util.Arrays.fill(hKeys, -1)
                codeSize = minCodeSize + 1
                next = eoi + 1
            }
            prefix = c
        }
        emit(prefix)
        emit(eoi)
        if (curBits > 0) buf.write(cur and 0xFF)
        return buf.toByteArray()
    }
}
