package com.ecogif.app.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

private class Fit(val x: Float, val y: Float, val w: Float, val h: Float)

/** Largest rectangle with the GIF's aspect ratio that fits inside the preview box. */
private fun fitInside(box: IntSize, srcW: Int, srcH: Int): Fit {
    val s = minOf(box.width / srcW.toFloat(), box.height / srcH.toFloat()) * 0.94f
    val w = srcW * s
    val h = srcH * s
    return Fit((box.width - w) / 2f, (box.height - h) / 2f, w, h)
}

private const val LEFT = 1
private const val TOP = 2
private const val RIGHT = 4
private const val BOTTOM = 8
private const val MOVE = 16

/** Which part of the crop box was touched: a side, a corner, the inside (move) or nothing. */
private fun hitTest(p: Offset, l: Float, t: Float, r: Float, b: Float, slop: Float): Int {
    if (p.x !in (l - slop)..(r + slop) || p.y !in (t - slop)..(b + slop)) return 0
    val inX = minOf(slop, (r - l) / 3f)
    val inY = minOf(slop, (b - t) / 3f)
    val nearL = p.x in (l - slop)..(l + inX)
    val nearR = p.x in (r - inX)..(r + slop)
    val nearT = p.y in (t - slop)..(t + inY)
    val nearB = p.y in (b - inY)..(b + slop)
    var m = 0
    if (nearL && (!nearR || abs(p.x - l) <= abs(p.x - r))) m = m or LEFT
    else if (nearR) m = m or RIGHT
    if (nearT && (!nearB || abs(p.y - t) <= abs(p.y - b))) m = m or TOP
    else if (nearB) m = m or BOTTOM
    if (m != 0) return m
    return if (p.x in l..r && p.y in t..b) MOVE else 0
}

/**
 * Live preview of the resize result + crop box editor.
 *
 *  - crop mode OFF: the GIF is drawn exactly as the output will look (cropped, and at the new size
 *    relative to the original), so every slider / field change shows up immediately.
 *  - crop mode ON: the whole GIF is shown with a box; drag any side / corner (or the inside) to crop.
 */
@Composable
fun ResizePreview(state: ResizeState, uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info = state.info ?: return
    val srcW = info.width
    val srcH = info.height

    var drawable by remember(uri) { mutableStateOf<Drawable?>(null) }
    val tick = remember { mutableIntStateOf(0) }

    LaunchedEffect(uri) {
        drawable = withContext(Dispatchers.IO) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeDrawable(source) { decoder, i, _ ->
                    val big = maxOf(i.size.width, i.size.height)
                    decoder.setTargetSampleSize(maxOf(1, big / 900))
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // redraw whenever the animated drawable moves to its next frame
    DisposableEffect(drawable) {
        val d = drawable
        val handler = Handler(Looper.getMainLooper())
        if (d != null) {
            d.setBounds(0, 0, max(1, d.intrinsicWidth), max(1, d.intrinsicHeight))
            d.callback = object : Drawable.Callback {
                override fun invalidateDrawable(who: Drawable) {
                    tick.intValue++
                }

                override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                    handler.postAtTime(what, who, `when`)
                }

                override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                    handler.removeCallbacks(what, who)
                }
            }
            (d as? AnimatedImageDrawable)?.apply {
                repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                start()
            }
            tick.intValue++
        }
        onDispose {
            d?.callback = null
            (d as? AnimatedImageDrawable)?.stop()
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(0.18f))
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state.cropping) {
                    if (!state.cropping) return@pointerInput
                    val slop = 30.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val fit = fitInside(size, srcW, srcH)
                        val handle = hitTest(
                            down.position,
                            fit.x + state.cropL * fit.w, fit.y + state.cropT * fit.h,
                            fit.x + state.cropR * fit.w, fit.y + state.cropB * fit.h,
                            slop
                        )
                        // touched outside the box: let the page scroll as usual
                        if (handle == 0) return@awaitEachGesture
                        down.consume()
                        val minW = (16f / srcW).coerceIn(0.03f, 0.5f)
                        val minH = (16f / srcH).coerceIn(0.03f, 0.5f)
                        drag(down.id) { change ->
                            val d = change.positionChange()
                            val dx = d.x / fit.w
                            val dy = d.y / fit.h
                            var l = state.cropL
                            var t = state.cropT
                            var r = state.cropR
                            var b = state.cropB
                            if (handle and MOVE != 0) {
                                val w = r - l
                                val h = b - t
                                l = (l + dx).coerceIn(0f, 1f - w)
                                t = (t + dy).coerceIn(0f, 1f - h)
                                r = l + w
                                b = t + h
                            } else {
                                if (handle and LEFT != 0) l = (l + dx).coerceIn(0f, r - minW)
                                if (handle and RIGHT != 0) r = (r + dx).coerceIn(l + minW, 1f)
                                if (handle and TOP != 0) t = (t + dy).coerceIn(0f, b - minH)
                                if (handle and BOTTOM != 0) b = (b + dy).coerceIn(t + minH, 1f)
                            }
                            state.setCrop(l, t, r, b)
                            change.consume()
                        }
                    }
                }
        ) {
            tick.intValue // read: redraw on every animation frame
            val d = drawable
            val fit = fitInside(IntSize(size.width.toInt(), size.height.toInt()), srcW, srcH)
            val iw = max(1, d?.intrinsicWidth ?: 1)
            val ih = max(1, d?.intrinsicHeight ?: 1)

            if (state.cropping) {
                // ---------- crop editor: whole GIF + box
                if (d != null) {
                    withTransform({
                        translate(fit.x, fit.y)
                        scale(fit.w / iw, fit.h / ih, pivot = Offset.Zero)
                    }) {
                        drawIntoCanvas { d.draw(it.nativeCanvas) }
                    }
                }
                val l = fit.x + state.cropL * fit.w
                val t = fit.y + state.cropT * fit.h
                val r = fit.x + state.cropR * fit.w
                val b = fit.y + state.cropB * fit.h
                val dim = Color.Black.copy(0.58f)
                drawRect(dim, Offset(fit.x, fit.y), Size(fit.w, t - fit.y))
                drawRect(dim, Offset(fit.x, b), Size(fit.w, fit.y + fit.h - b))
                drawRect(dim, Offset(fit.x, t), Size(l - fit.x, b - t))
                drawRect(dim, Offset(r, t), Size(fit.x + fit.w - r, b - t))

                // rule-of-thirds grid
                val grid = Color.White.copy(0.30f)
                val g = 1.dp.toPx()
                for (k in 1..2) {
                    val gx = l + (r - l) * k / 3f
                    val gy = t + (b - t) * k / 3f
                    drawLine(grid, Offset(gx, t), Offset(gx, b), g)
                    drawLine(grid, Offset(l, gy), Offset(r, gy), g)
                }
                drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))

                // corner handles
                val len = 22.dp.toPx()
                val th = 4.dp.toPx()
                fun corner(cx: Float, cy: Float, sx: Float, sy: Float) {
                    drawLine(Color.White, Offset(cx - th / 2 * sx, cy), Offset(cx + len * sx, cy), th, StrokeCap.Round)
                    drawLine(Color.White, Offset(cx, cy - th / 2 * sy), Offset(cx, cy + len * sy), th, StrokeCap.Round)
                }
                corner(l, t, 1f, 1f)
                corner(r, t, -1f, 1f)
                corner(l, b, 1f, -1f)
                corner(r, b, -1f, -1f)

                // side handles (middle of every side)
                val half = 14.dp.toPx()
                val mx = (l + r) / 2f
                val my = (t + b) / 2f
                val bar = 5.dp.toPx()
                val side = (r - l).coerceAtLeast(0f)
                val tall = (b - t).coerceAtLeast(0f)
                val hx = minOf(half, side / 4f)
                val hy = minOf(half, tall / 4f)
                drawRoundRect(Color.White, Offset(mx - hx, t - bar / 2), Size(hx * 2, bar), CornerRadius(bar / 2))
                drawRoundRect(Color.White, Offset(mx - hx, b - bar / 2), Size(hx * 2, bar), CornerRadius(bar / 2))
                drawRoundRect(Color.White, Offset(l - bar / 2, my - hy), Size(bar, hy * 2), CornerRadius(bar / 2))
                drawRoundRect(Color.White, Offset(r - bar / 2, my - hy), Size(bar, hy * 2), CornerRadius(bar / 2))
            } else {
                // ---------- live result: cropped + resized, relative to the original size
                val cw = state.cropW
                val ch = state.cropH
                val ow = (state.outW ?: cw).toFloat()
                val oh = (state.outH ?: ch).toFloat()
                // the original is drawn at `fit` size; bigger results shrink the whole stage
                val stage = maxOf(1f, ow / srcW, oh / srcH)
                val k = fit.w / (srcW * stage)           // screen px per output px
                val rw = ow * k
                val rh = oh * k
                val rx = (size.width - rw) / 2f
                val ry = (size.height - rh) / 2f
                val sxFull = ow / cw                      // output px per source px
                val syFull = oh / ch
                val fullW = srcW * sxFull * k
                val fullH = srcH * syFull * k
                val imgX = rx - state.cropX * sxFull * k
                val imgY = ry - state.cropY * syFull * k
                if (d != null) {
                    clipRect(rx, ry, rx + rw, ry + rh) {
                        withTransform({
                            translate(imgX, imgY)
                            scale(fullW / iw, fullH / ih, pivot = Offset.Zero)
                        }) {
                            drawIntoCanvas { d.draw(it.nativeCanvas) }
                        }
                    }
                }
                drawRect(
                    Color.White.copy(0.35f), Offset(rx, ry), Size(rw, rh),
                    style = Stroke(1.dp.toPx())
                )
            }
        }
    }
}
