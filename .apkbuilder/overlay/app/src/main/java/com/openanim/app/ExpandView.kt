package com.openanim.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils

/**
 * يرسم مستطيل بزوايا دائرية يتوسع من مكان الأيقونة إلى كامل الشاشة (مثل iOS 26).
 */
class ExpandView(
    context: Context,
    private val icon: Bitmap,
    private val iconColor: Int,
    private val endColor: Int,
    private val startRect: RectF
) : View(context) {

    var onExpanded: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val dst = RectF()

    private var progress = 0f
    private var alphaAll = 1f
    private var expander: ValueAnimator? = null
    private var fader: ValueAnimator? = null

    private val startRadius = startRect.width() * 0.225f
    private val endRadius = 42f * context.resources.displayMetrics.density

    fun expand(duration: Long) {
        expander = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = PathInterpolator(0.2f, 0.9f, 0.25f, 1f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onExpanded?.invoke()
                }
            })
            start()
        }
    }

    fun fadeOut(duration: Long, end: () -> Unit) {
        fader = ValueAnimator.ofFloat(1f, 0f).apply {
            this.duration = duration
            addUpdateListener {
                alphaAll = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    end()
                }
            })
            start()
        }
    }

    fun cancel() {
        onExpanded = null
        expander?.cancel()
        fader?.removeAllListeners()
        fader?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = progress

        rect.set(
            lerp(startRect.left, 0f, p),
            lerp(startRect.top, 0f, p),
            lerp(startRect.right, w, p),
            lerp(startRect.bottom, h, p)
        )

        // الزوايا: من زاوية الأيقونة إلى زاوية الشاشة ثم تختفي قرب النهاية
        val corner = lerp(startRadius, endRadius, p) * (1f - smooth(0.8f, 1f, p))

        paint.color = ColorUtils.blendARGB(iconColor, endColor, (p * 1.8f).coerceAtMost(1f))
        paint.alpha = (255 * alphaAll).toInt()
        canvas.drawRoundRect(rect, corner, corner, paint)

        val iconAlpha = (1f - p * 3.2f).coerceIn(0f, 1f)
        if (iconAlpha > 0f) {
            val s = startRect.width() * lerp(1f, 3.4f, p)
            dst.set(
                rect.centerX() - s / 2f,
                rect.centerY() - s / 2f,
                rect.centerX() + s / 2f,
                rect.centerY() + s / 2f
            )
            iconPaint.alpha = (255 * iconAlpha * alphaAll).toInt()
            canvas.drawBitmap(icon, null, dst, iconPaint)
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
