package com.ecogif.app.engine

/**
 * Small pure-Kotlin GIF decoder.
 * Frames come out fully composed (disposal methods + transparency applied), one at a time,
 * so even long GIFs never need all their frames in memory.
 */
class GifFrame(val pixels: IntArray, val delayMs: Int)

class GifInfo(val width: Int, val height: Int, val frameCount: Int, val durationMs: Long)

class GifDecoder(private val data: ByteArray) {

    var width = 0
        private set
    var height = 0
        private set

    private var pos = 0
    private var globalTable: IntArray? = null
    private var canvas = IntArray(0)
    private var backup: IntArray? = null

    // state of the previously drawn frame (needed for disposal)
    private var prevDisposal = 0
    private var prevX = 0
    private var prevY = 0
    private var prevW = 0
    private var prevH = 0

    // graphic control extension waiting for the next image
    private var gceDelay = 100
    private var gceDisposal = 0
    private var gceTransparent = -1

    private var headerRead = false
    private var finished = false

    init {
        readHeader()
    }

    private fun u8(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else 0
    private fun u16(): Int = u8() or (u8() shl 8)

    private fun readHeader() {
        if (data.size < 13) throw IllegalArgumentException("Not a GIF file")
        val sig = String(data, 0, 6, Charsets.US_ASCII)
        if (sig != "GIF87a" && sig != "GIF89a") throw IllegalArgumentException("Not a GIF file")
        pos = 6
        width = u16()
        height = u16()
        val packed = u8()
        u8() // background colour index
        u8() // aspect ratio
        if (width <= 0 || height <= 0) throw IllegalArgumentException("Bad GIF size")
        if (packed and 0x80 != 0) {
            globalTable = readTable(2 shl (packed and 7))
        }
        canvas = IntArray(width * height)
        headerRead = true
    }

    private fun readTable(size: Int): IntArray {
        val t = IntArray(size)
        for (i in 0 until size) {
            val r = u8()
            val g = u8()
            val b = u8()
            t[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return t
    }

    private fun skipSubBlocks() {
        while (pos < data.size) {
            val n = u8()
            if (n == 0) return
            pos += n
        }
    }

    /** Returns the next composed frame, or null when the GIF is finished. */
    fun readFrame(): GifFrame? = nextImage(decodePixels = true)

    private fun nextImage(decodePixels: Boolean): GifFrame? {
        if (finished) return null
        while (pos < data.size) {
            when (u8()) {
                0x21 -> { // extension
                    val label = u8()
                    if (label == 0xF9) { // graphic control
                        u8() // block size (4)
                        val packed = u8()
                        val d = u16()
                        val t = u8()
                        u8() // terminator
                        gceDisposal = (packed shr 2) and 7
                        gceDelay = if (d <= 1) 100 else d * 10
                        gceTransparent = if (packed and 1 != 0) t else -1
                    } else {
                        skipSubBlocks()
                    }
                }
                0x2C -> { // image descriptor
                    return readImage(decodePixels)
                }
                0x3B -> { // trailer
                    finished = true
                    return null
                }
                else -> { /* ignore garbage byte */ }
            }
        }
        finished = true
        return null
    }

    private fun readImage(decodePixels: Boolean): GifFrame {
        val x = u16()
        val y = u16()
        val w = u16()
        val h = u16()
        val packed = u8()
        val interlaced = packed and 0x40 != 0
        val table = if (packed and 0x80 != 0) readTable(2 shl (packed and 7)) else globalTable
        val minCode = u8()

        val delay = gceDelay
        val disposal = gceDisposal
        val transparent = gceTransparent
        gceDelay = 100
        gceDisposal = 0
        gceTransparent = -1

        // gather the LZW sub-blocks
        var total = 0
        val start = pos
        var p = pos
        while (p < data.size) {
            val n = data[p++].toInt() and 0xFF
            if (n == 0) break
            total += n
            p += n
        }
        if (!decodePixels) {
            pos = p
            return GifFrame(IntArray(0), delay)
        }
        val lzw = ByteArray(total)
        var q = start
        var o = 0
        while (q < data.size) {
            val n = data[q++].toInt() and 0xFF
            if (n == 0) break
            val len = minOf(n, data.size - q)
            System.arraycopy(data, q, lzw, o, len)
            o += len
            q += n
        }
        pos = p

        // --- dispose the previous frame ---
        when (prevDisposal) {
            2 -> clearRect(prevX, prevY, prevW, prevH)
            3 -> backup?.let { System.arraycopy(it, 0, canvas, 0, canvas.size) }
        }
        if (disposal == 3) {
            val b = backup ?: IntArray(canvas.size).also { backup = it }
            System.arraycopy(canvas, 0, b, 0, canvas.size)
        }

        // --- draw this frame ---
        val indices = decodeLzw(minCode.coerceIn(2, 11), lzw, w * h)
        val colors = table
        if (colors != null) {
            var row = 0
            val rowMap = if (interlaced) interlaceRows(h) else null
            for (r in 0 until h) {
                val destRow = y + (rowMap?.get(r) ?: r)
                if (destRow !in 0 until height) { row++; continue }
                val srcBase = r * w
                for (c in 0 until w) {
                    val dx = x + c
                    if (dx !in 0 until width) continue
                    val idx = indices[srcBase + c].toInt() and 0xFF
                    if (idx == transparent) continue
                    if (idx < colors.size) canvas[destRow * width + dx] = colors[idx]
                }
                row++
            }
        }

        prevDisposal = disposal
        prevX = x
        prevY = y
        prevW = w
        prevH = h
        return GifFrame(canvas.copyOf(), delay)
    }

    private fun clearRect(x: Int, y: Int, w: Int, h: Int) {
        for (r in y until minOf(y + h, height)) {
            if (r < 0) continue
            for (c in x until minOf(x + w, width)) {
                if (c < 0) continue
                canvas[r * width + c] = 0
            }
        }
    }

    private fun interlaceRows(h: Int): IntArray {
        val map = IntArray(h)
        var i = 0
        for ((start, step) in arrayOf(0 to 8, 4 to 8, 2 to 4, 1 to 2)) {
            var r = start
            while (r < h) {
                map[i++] = r
                r += step
            }
        }
        return map
    }

    private fun decodeLzw(minCodeSize: Int, input: ByteArray, pixelCount: Int): ByteArray {
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        var codeSize = minCodeSize + 1
        var nextCode = eoi + 1
        val prefix = ShortArray(4096)
        val suffix = ByteArray(4096)
        val stack = ByteArray(4097)
        val out = ByteArray(pixelCount)
        for (i in 0 until clear) suffix[i] = i.toByte()

        var outPos = 0
        var bits = 0
        var datum = 0
        var inPos = 0
        var oldCode = -1
        var first = 0

        while (outPos < pixelCount) {
            while (bits < codeSize) {
                if (inPos >= input.size) return out
                datum = datum or ((input[inPos++].toInt() and 0xFF) shl bits)
                bits += 8
            }
            var code = datum and ((1 shl codeSize) - 1)
            datum = datum ushr codeSize
            bits -= codeSize

            if (code == clear) {
                codeSize = minCodeSize + 1
                nextCode = eoi + 1
                oldCode = -1
                continue
            }
            if (code == eoi) break
            if (oldCode == -1) {
                if (code < 4096) out[outPos++] = suffix[code]
                oldCode = code
                first = code and 0xFF
                continue
            }
            val inCode = code
            var top = 0
            if (code >= nextCode) {
                stack[top++] = first.toByte()
                code = oldCode
            }
            while (code >= clear) {
                if (top >= stack.size - 1) return out // corrupt data
                stack[top++] = suffix[code]
                code = prefix[code].toInt() and 0xFFFF
            }
            first = suffix[code].toInt() and 0xFF
            stack[top++] = first.toByte()
            if (nextCode < 4096) {
                prefix[nextCode] = oldCode.toShort()
                suffix[nextCode] = first.toByte()
                nextCode++
                if (nextCode == (1 shl codeSize) && codeSize < 12) codeSize++
            }
            oldCode = inCode
            while (top > 0 && outPos < pixelCount) out[outPos++] = stack[--top]
        }
        return out
    }

    companion object {
        /** Quick scan (no pixel decoding) for size / frame count / duration. */
        fun probe(data: ByteArray): GifInfo {
            val d = GifDecoder(data)
            var frames = 0
            var duration = 0L
            while (true) {
                val f = d.nextImage(decodePixels = false) ?: break
                frames++
                duration += f.delayMs
            }
            return GifInfo(d.width, d.height, frames, duration)
        }
    }
}
