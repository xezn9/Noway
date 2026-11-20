package com.mfp.filemanager.ui.glass

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * A "liquid glass" file badge drawn with plain Canvas (cheap enough for RecyclerView rows):
 * tinted translucent body, inner light, curved specular gloss, rim light and a soft shadow,
 * with a glyph + extension label on top. It has no intrinsic size, so it always fills its view.
 */
class GlassFileIconDrawable(
    private val kind: FileKind,
    extension: String? = null
) : Drawable() {

    private val label: String = (kind.label ?: extension ?: "").uppercase().take(4)

    private val tint = kind.tint
    private val light = ColorUtils.blendARGB(tint, Color.WHITE, 0.5f)
    private val deep = ColorUtils.blendARGB(tint, Color.BLACK, 0.28f)

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0x38, 0, 0, 0) }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0x66, 255, 255, 255) }
    private val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0x3A, 255, 255, 255) }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val frontRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(0xF4, 255, 255, 255)
    }
    private val glyphFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0xF4, 255, 255, 255) }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = deep }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.argb(0xF8, 255, 255, 255)
    }
    private val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.argb(0xF8, 255, 255, 255)
    }

    private val body = Path()
    private val gloss = Path()
    private val fold = Path()
    private val front = Path()
    private val tmp = Path()
    private val r = RectF()          // the badge rect (inset)
    private val glyphRect = RectF()  // where the glyph lives
    private var labelY = 0f
    private var shadowDy = 0f
    private var hasFold = false
    private var hasFront = false

    override fun onBoundsChange(b: android.graphics.Rect) {
        super.onBoundsChange(b)
        if (b.isEmpty) return
        build(b.left.toFloat(), b.top.toFloat(), b.width().toFloat(), b.height().toFloat())
    }

    // ------------------------------------------------------------------ build

    private fun build(x: Float, y: Float, w: Float, h: Float) {
        val pad = w * 0.07f
        r.set(x + pad, y + pad, x + w - pad, y + h - pad)
        val rw = r.width()
        val rh = r.height()
        shadowDy = w * 0.03f
        hasFold = false
        hasFront = false
        body.reset()

        when (kind.shape) {
            GlassShape.FOLDER -> {
                val pts = listOf(
                    PointF(r.left, r.top + rh * 0.10f),
                    PointF(r.left + rw * 0.40f, r.top + rh * 0.10f),
                    PointF(r.left + rw * 0.50f, r.top + rh * 0.24f),
                    PointF(r.right, r.top + rh * 0.24f),
                    PointF(r.right, r.bottom),
                    PointF(r.left, r.bottom)
                )
                body.set(roundedPolygon(pts, rw * 0.13f))
                val fr = RectF(r.left, r.top + rh * 0.38f, r.right, r.bottom)
                front.reset()
                front.addRoundRect(fr, rw * 0.13f, rw * 0.13f, Path.Direction.CW)
                hasFront = true
            }
            GlassShape.PAGE -> {
                val l = r.left + rw * 0.12f
                val rt = r.right - rw * 0.12f
                val f = (rt - l) * 0.34f
                val pts = listOf(
                    PointF(l, r.top), PointF(rt - f, r.top), PointF(rt, r.top + f),
                    PointF(rt, r.bottom), PointF(l, r.bottom)
                )
                body.set(roundedPolygon(pts, rw * 0.1f))
                fold.reset()
                fold.moveTo(rt - f, r.top)
                fold.lineTo(rt - f, r.top + f)
                fold.lineTo(rt, r.top + f)
                fold.close()
                hasFold = true
            }
            GlassShape.SQUIRCLE -> body.addRoundRect(r, rw * 0.30f, rw * 0.30f, Path.Direction.CW)
            GlassShape.CIRCLE -> body.addOval(r, Path.Direction.CW)
            GlassShape.HEXAGON -> {
                val cx = r.centerX()
                val cy = r.centerY()
                val rad = min(rw, rh) / 2f
                val pts = (0 until 6).map {
                    val a = Math.toRadians(-90.0 + 60.0 * it)
                    PointF(cx + (rad * cos(a)).toFloat(), cy + (rad * sin(a)).toFloat())
                }
                body.set(roundedPolygon(pts, rad * 0.30f))
            }
            GlassShape.CHIP -> {
                val i = rw * 0.13f
                val c = RectF(r.left + i, r.top + i, r.right - i, r.bottom - i)
                body.addRoundRect(c, rw * 0.16f, rw * 0.16f, Path.Direction.CW)
            }
        }

        // ---- shaders (all relative to the badge rect) ----
        val cx = r.centerX()
        fillPaint.shader = LinearGradient(
            cx, r.top, cx, r.bottom,
            intArrayOf(withA(light, 0xE6), withA(tint, 0xBE), withA(deep, 0xD2)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        lightPaint.shader = RadialGradient(
            r.left + rw * 0.28f, r.top + rh * 0.20f, rw * 0.85f,
            intArrayOf(Color.argb(0x70, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            null, Shader.TileMode.CLAMP
        )
        bottomPaint.shader = LinearGradient(
            cx, r.bottom - rh * 0.5f, cx, r.bottom,
            Color.argb(0, 255, 255, 255), withA(light, 0x80), Shader.TileMode.CLAMP
        )
        gloss.reset()
        gloss.moveTo(r.left, r.top)
        gloss.lineTo(r.right, r.top)
        gloss.lineTo(r.right, r.top + rh * 0.36f)
        gloss.quadTo(cx, r.top + rh * 0.62f, r.left, r.top + rh * 0.36f)
        gloss.close()
        glossPaint.shader = LinearGradient(
            cx, r.top, cx, r.top + rh * 0.55f,
            Color.argb(0x78, 255, 255, 255), Color.argb(0x0A, 255, 255, 255), Shader.TileMode.CLAMP
        )
        val rimW = maxOf(1f, w * 0.032f)
        rimPaint.strokeWidth = rimW
        rimPaint.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(Color.argb(0xF4, 255, 255, 255), Color.argb(0x2A, 255, 255, 255), withA(light, 0xA0)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        frontRimPaint.strokeWidth = rimW * 0.8f
        frontRimPaint.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(Color.argb(0xB0, 255, 255, 255), Color.argb(0x10, 255, 255, 255), Color.argb(0x60, 255, 255, 255)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )

        // ---- glyph area / label ----
        val hasLabel = label.isNotEmpty()
        glyphRect.set(
            cx - rw * 0.23f,
            r.top + rh * (if (hasLabel) 0.20f else 0.28f),
            cx + rw * 0.23f,
            r.top + rh * (if (hasLabel) 0.56f else 0.72f)
        )
        glyphStroke.strokeWidth = maxOf(1.2f, w * 0.058f)
        textPaint.textSize = rh * (if (label.length > 3) 0.17f else 0.20f)
        val fm = textPaint.fontMetrics
        labelY = r.top + rh * 0.80f - (fm.ascent + fm.descent) / 2f
        monoPaint.textSize = rh * 0.34f
    }

    // ------------------------------------------------------------------- draw

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty || r.isEmpty) return

        // soft drop shadow
        canvas.save()
        canvas.translate(0f, shadowDy)
        canvas.drawPath(body, shadowPaint)
        canvas.restore()

        // glass body
        canvas.drawPath(body, fillPaint)

        canvas.save()
        canvas.clipPath(body)
        canvas.drawRect(r, lightPaint)
        canvas.drawRect(r, bottomPaint)
        canvas.drawPath(gloss, glossPaint)
        if (hasFold) canvas.drawPath(fold, foldPaint)
        canvas.restore()

        if (hasFront) {
            canvas.drawPath(front, frontPaint)
            canvas.drawPath(front, frontRimPaint)
        }

        drawGlyph(canvas)

        canvas.drawPath(body, rimPaint)
    }

    private fun drawGlyph(canvas: Canvas) {
        val g = glyphRect
        val gw = g.width()
        val gh = g.height()
        val cx = g.centerX()

        when (kind.glyph) {
            GlassGlyph.NONE -> Unit

            GlassGlyph.LINES -> for (i in 0..2) {
                val yy = g.top + gh * (0.12f + i * 0.38f)
                val x2 = if (i == 1) g.right - gw * 0.28f else g.right
                canvas.drawLine(g.left, yy, x2, yy, glyphStroke)
            }

            GlassGlyph.BRACES -> canvas.drawText("{ }", cx, g.centerY() - (monoPaint.ascent() + monoPaint.descent()) / 2f, monoPaint)
            GlassGlyph.TAGS -> canvas.drawText("</>", cx, g.centerY() - (monoPaint.ascent() + monoPaint.descent()) / 2f, monoPaint)

            GlassGlyph.ZIPPER -> {
                val tooth = gw * 0.20f
                for (i in 0..4) {
                    val yy = g.top + gh * (0.04f + i * 0.2f)
                    val off = if (i % 2 == 0) -tooth else 0f
                    canvas.drawLine(cx + off, yy, cx + off + tooth, yy, glyphStroke)
                }
                // pull tab
                val tab = RectF(cx - gw * 0.15f, g.bottom - gh * 0.02f, cx + gw * 0.15f, g.bottom + gh * 0.26f)
                canvas.drawRoundRect(tab, gw * 0.08f, gw * 0.08f, glyphStroke)
            }

            GlassGlyph.BOOKS -> {
                val bh = gh * 0.24f
                val widths = floatArrayOf(1f, 0.82f, 0.94f)
                for (i in 0..2) {
                    val top = g.top + i * (bh + gh * 0.10f)
                    val ww = gw * widths[i]
                    canvas.drawRoundRect(cx - ww / 2f, top, cx + ww / 2f, top + bh, bh / 2f, bh / 2f, glyphFill)
                }
            }

            GlassGlyph.ANDROID -> {
                val rad = gw * 0.42f
                val headBottom = g.top + gh * 0.62f
                val head = RectF(cx - rad, headBottom - rad, cx + rad, headBottom + rad)
                canvas.drawArc(head, 180f, 180f, true, glyphFill)
                // antennae
                val a = glyphStroke.strokeWidth * 0.8f
                val old = glyphStroke.strokeWidth
                glyphStroke.strokeWidth = a
                canvas.drawLine(cx - rad * 0.55f, headBottom - rad * 0.78f, cx - rad * 0.85f, headBottom - rad * 1.2f, glyphStroke)
                canvas.drawLine(cx + rad * 0.55f, headBottom - rad * 0.78f, cx + rad * 0.85f, headBottom - rad * 1.2f, glyphStroke)
                glyphStroke.strokeWidth = old
                // eyes
                val er = rad * 0.11f
                canvas.drawCircle(cx - rad * 0.42f, headBottom - rad * 0.42f, er, eyePaint)
                canvas.drawCircle(cx + rad * 0.42f, headBottom - rad * 0.42f, er, eyePaint)
                // body
                canvas.drawRoundRect(cx - rad, headBottom + rad * 0.12f, cx + rad, headBottom + rad * 0.62f, rad * 0.2f, rad * 0.2f, glyphFill)
            }

            GlassGlyph.CHIP -> {
                // inner die
                val d = RectF(cx - gw * 0.30f, g.top + gh * 0.02f, cx + gw * 0.30f, g.top + gh * 0.02f + gw * 0.60f)
                canvas.drawRoundRect(d, gw * 0.10f, gw * 0.10f, glyphStroke)
                // pins on the 4 sides (outside the body)
                val bodyL = r.left + r.width() * 0.13f
                val bodyR = r.right - r.width() * 0.13f
                val bodyT = r.top + r.height() * 0.13f
                val bodyB = r.bottom - r.height() * 0.13f
                val pin = r.width() * 0.085f
                val old = glyphStroke.strokeWidth
                glyphStroke.strokeWidth = old * 0.8f
                for (i in 1..3) {
                    val fx = bodyL + (bodyR - bodyL) * i / 4f
                    val fy = bodyT + (bodyB - bodyT) * i / 4f
                    canvas.drawLine(fx, bodyT, fx, bodyT - pin, glyphStroke)
                    canvas.drawLine(fx, bodyB, fx, bodyB + pin, glyphStroke)
                    canvas.drawLine(bodyL, fy, bodyL - pin, fy, glyphStroke)
                    canvas.drawLine(bodyR, fy, bodyR + pin, fy, glyphStroke)
                }
                glyphStroke.strokeWidth = old
            }

            GlassGlyph.PLAY -> {
                tmp.reset()
                tmp.moveTo(cx - gw * 0.18f, g.top + gh * 0.05f)
                tmp.lineTo(cx + gw * 0.28f, g.centerY())
                tmp.lineTo(cx - gw * 0.18f, g.bottom - gh * 0.05f)
                tmp.close()
                canvas.drawPath(tmp, glyphFill)
            }

            GlassGlyph.MOUNTAIN -> {
                tmp.reset()
                tmp.moveTo(g.left, g.bottom)
                tmp.lineTo(cx - gw * 0.08f, g.top + gh * 0.25f)
                tmp.lineTo(cx + gw * 0.12f, g.top + gh * 0.58f)
                tmp.lineTo(cx + gw * 0.26f, g.top + gh * 0.40f)
                tmp.lineTo(g.right, g.bottom)
                tmp.close()
                canvas.drawPath(tmp, glyphFill)
                canvas.drawCircle(g.right - gw * 0.12f, g.top + gh * 0.08f, gw * 0.09f, glyphFill)
            }

            GlassGlyph.NOTE -> {
                val hr = gw * 0.17f
                val hx = cx - gw * 0.08f
                val hy = g.bottom - hr
                canvas.drawCircle(hx, hy, hr, glyphFill)
                val sx = hx + hr * 0.85f
                canvas.drawLine(sx, hy, sx, g.top, glyphStroke)
                canvas.drawLine(sx, g.top, sx + gw * 0.30f, g.top + gh * 0.22f, glyphStroke)
            }
        }

        if (label.isNotEmpty()) canvas.drawText(label, r.centerX(), labelY, textPaint)
    }

    // ------------------------------------------------------------- Drawable api

    override fun setAlpha(alpha: Int) {
        // keep the glass look; fading is done on the view when needed
    }

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // ---------------------------------------------------------------- helpers

    private fun withA(color: Int, alpha: Int) = ColorUtils.setAlphaComponent(color, alpha)

    private fun roundedPolygon(pts: List<PointF>, radius: Float): Path {
        val path = Path()
        val n = pts.size
        for (i in 0 until n) {
            val prev = pts[(i + n - 1) % n]
            val cur = pts[i]
            val next = pts[(i + 1) % n]
            val d1 = hypot(prev.x - cur.x, prev.y - cur.y)
            val d2 = hypot(next.x - cur.x, next.y - cur.y)
            val r1 = min(radius, d1 / 2f)
            val r2 = min(radius, d2 / 2f)
            val ax = cur.x + (prev.x - cur.x) * r1 / d1
            val ay = cur.y + (prev.y - cur.y) * r1 / d1
            val bx = cur.x + (next.x - cur.x) * r2 / d2
            val by = cur.y + (next.y - cur.y) * r2 / d2
            if (i == 0) path.moveTo(ax, ay) else path.lineTo(ax, ay)
            path.quadTo(cur.x, cur.y, bx, by)
        }
        path.close()
        return path
    }
}
