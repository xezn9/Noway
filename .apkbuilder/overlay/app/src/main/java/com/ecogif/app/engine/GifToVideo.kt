package com.ecogif.app.engine

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.ensureActive
import java.io.FileDescriptor
import kotlin.coroutines.coroutineContext

/** GIF -> H.264 MP4, using MediaCodec with exact per-frame timestamps taken from the GIF delays. */
object GifToVideo {

    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

    /** Returns the encoder-friendly output size for the requested scale. */
    fun outputSize(srcW: Int, srcH: Int, scalePercent: Int): Pair<Int, Int> {
        var w = srcW * scalePercent / 100
        var h = srcH * scalePercent / 100
        // phones can't encode huge frames reliably
        val maxSide = 1920
        val big = maxOf(w, h)
        if (big > maxSide) {
            w = w * maxSide / big
            h = h * maxSide / big
        }
        val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .findEncoderForFormat(MediaFormat.createVideoFormat(MIME, 640, 360))
        var alignW = 2
        var alignH = 2
        if (codecName != null) {
            val info = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { it.name == codecName }
            val caps = info?.getCapabilitiesForType(MIME)?.videoCapabilities
            if (caps != null) {
                alignW = maxOf(2, caps.widthAlignment)
                alignH = maxOf(2, caps.heightAlignment)
                var tries = 0
                while (tries < 8) {
                    val aw = align(w, alignW)
                    val ah = align(h, alignH)
                    if (caps.isSizeSupported(aw, ah)) return aw to ah
                    w = w * 3 / 4
                    h = h * 3 / 4
                    tries++
                }
            }
        }
        return align(w, alignW) to align(h, alignH)
    }

    private fun align(v: Int, a: Int): Int = maxOf(a, ((v + a / 2) / a) * a)

    suspend fun convert(
        data: ByteArray,
        outFd: FileDescriptor,
        outW: Int,
        outH: Int,
        loops: Int,
        onProgress: (Float) -> Unit,
    ) {
        val info = GifDecoder.probe(data)
        val totalFrames = maxOf(1, info.frameCount) * loops

        val format = MediaFormat.createVideoFormat(MIME, outW, outH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            val bitrate = (outW.toLong() * outH * 25 * 0.2).toLong().coerceIn(600_000L, 12_000_000L)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate.toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, 25)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
            ?: throw IllegalStateException("No H.264 encoder on this device")
        val codec = MediaCodec.createByCodecName(codecName)
        val muxer = MediaMuxer(outFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        fun drain(untilEos: Boolean) {
            while (true) {
                val idx = codec.dequeueOutputBuffer(bufferInfo, if (untilEos) 10_000 else 0)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEos) return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    idx >= 0 -> {
                        val buf = codec.getOutputBuffer(idx)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bufferInfo.size = 0
                        if (buf != null && bufferInfo.size > 0 && muxerStarted) {
                            buf.position(bufferInfo.offset)
                            buf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(track, buf, bufferInfo)
                        }
                        codec.releaseOutputBuffer(idx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        suspend fun queueFrame(argb: IntArray, ptsUs: Long, eos: Boolean) {
            while (true) {
                coroutineContext.ensureActive()
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    if (eos) {
                        codec.queueInputBuffer(i, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val image = codec.getInputImage(i) ?: throw IllegalStateException("Encoder has no input image")
                        fillImage(image, argb, outW, outH)
                        val size = codec.getInputBuffer(i)?.capacity() ?: (outW * outH * 3 / 2)
                        codec.queueInputBuffer(i, 0, size, ptsUs, 0)
                    }
                    return
                }
                drain(false)
            }
        }

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            var ptsUs = 0L
            var done = 0
            var lastFrame: IntArray? = null
            repeat(loops) {
                val decoder = GifDecoder(data)
                while (true) {
                    coroutineContext.ensureActive()
                    val frame = decoder.readFrame() ?: break
                    val pixels = BitmapOps.scalePixels(frame.pixels, decoder.width, decoder.height, outW, outH)
                    // GIF may be transparent: flatten on white for video
                    val flat = IntArray(pixels.size)
                    for (p in pixels.indices) flat[p] = flatten(pixels[p])
                    queueFrame(flat, ptsUs, false)
                    drain(false)
                    ptsUs += frame.delayMs * 1000L
                    lastFrame = flat
                    done++
                    onProgress(done.toFloat() / totalFrames * 0.98f)
                }
            }
            // hold the last frame for its full duration, then close the stream
            lastFrame?.let { queueFrame(it, ptsUs, false) }
            queueFrame(IntArray(0), ptsUs + 1000L, true)
            drain(true)
            onProgress(1f)
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { if (muxerStarted) muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }

    private fun flatten(p: Int): Int {
        val a = p ushr 24
        if (a == 255) return p
        val r = (((p shr 16) and 0xFF) * a + 255 * (255 - a)) / 255
        val g = (((p shr 8) and 0xFF) * a + 255 * (255 - a)) / 255
        val b = ((p and 0xFF) * a + 255 * (255 - a)) / 255
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** ARGB -> the codec's YUV420 flexible Image (BT.601, limited range). */
    private fun fillImage(image: Image, argb: IntArray, w: Int, h: Int) {
        val planes = image.planes
        val yBuf = planes[0].buffer
        val yRow = planes[0].rowStride
        val yPix = planes[0].pixelStride
        val uBuf = planes[1].buffer
        val uRow = planes[1].rowStride
        val uPix = planes[1].pixelStride
        val vBuf = planes[2].buffer
        val vRow = planes[2].rowStride
        val vPix = planes[2].pixelStride

        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until w) {
                val p = argb[base + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val yy = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(y * yRow + x * yPix, yy.toByte())
            }
        }
        for (cy in 0 until h / 2) {
            for (cx in 0 until w / 2) {
                var r = 0; var g = 0; var b = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val p = argb[(cy * 2 + dy) * w + cx * 2 + dx]
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                }
                r /= 4; g /= 4; b /= 4
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                uBuf.put(cy * uRow + cx * uPix, u.coerceIn(0, 255).toByte())
                vBuf.put(cy * vRow + cx * vPix, v.coerceIn(0, 255).toByte())
            }
        }
    }
}
