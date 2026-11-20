package com.mfp.filemanager.ui.glass

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

/** Round liquid-glass button background (tinted body, inner light, gloss, rim). */
class GlassButtonDrawable(private val tint: Int) : Drawable() {
    private val light = ColorUtils.blendARGB(tint, Color.WHITE, 0.45f)
    private val deep = ColorUtils.blendARGB(tint, Color.BLACK, 0.25f)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0x35, 0, 0, 0) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val body = Path()
    private val glossPath = Path()
    private val r = RectF()
    private var dy = 0f

    override fun onBoundsChange(b: android.graphics.Rect) {
        super.onBoundsChange(b)
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val pad = w * 0.06f
        r.set(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad)
        dy = w * 0.03f
        body.reset()
        body.addOval(r, Path.Direction.CW)
        val cx = r.centerX()
        fill.shader = LinearGradient(
            cx, r.top, cx, r.bottom,
            intArrayOf(
                ColorUtils.setAlphaComponent(light, 0xE0),
                ColorUtils.setAlphaComponent(tint, 0xB4),
                ColorUtils.setAlphaComponent(deep, 0xC8)
            ),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        inner.shader = RadialGradient(
            r.left + r.width() * 0.3f, r.top + r.height() * 0.22f, r.width() * 0.8f,
            Color.argb(0x70, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )
        glossPath.reset()
        glossPath.moveTo(r.left, r.centerY() - r.height() * 0.05f)
        glossPath.quadTo(cx, r.top + r.height() * 0.62f, r.right, r.centerY() - r.height() * 0.05f)
        glossPath.lineTo(r.right, r.top)
        glossPath.lineTo(r.left, r.top)
        glossPath.close()
        gloss.shader = LinearGradient(
            cx, r.top, cx, r.centerY(),
            Color.argb(0x70, 255, 255, 255), Color.argb(0x08, 255, 255, 255), Shader.TileMode.CLAMP
        )
        rim.strokeWidth = maxOf(1f, w * 0.035f)
        rim.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(Color.argb(0xF0, 255, 255, 255), Color.argb(0x20, 255, 255, 255),
                ColorUtils.setAlphaComponent(light, 0xA0)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (r.isEmpty) return
        canvas.save(); canvas.translate(0f, dy); canvas.drawPath(body, shadow); canvas.restore()
        canvas.drawPath(body, fill)
        canvas.save()
        canvas.clipPath(body)
        canvas.drawRect(r, inner)
        canvas.drawPath(glossPath, gloss)
        canvas.restore()
        canvas.drawPath(body, rim)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
