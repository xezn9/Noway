package com.mfp.filemanager.ui.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mfp.filemanager.R

/**
 * Turns the title bar of ANY screen into a liquid-glass header that fades out towards the bottom,
 * and lets the screen content scroll underneath it (so the header never looks like a separate block).
 * Called once per fragment view from MainActivity.
 */
object GlassHeader {

    fun install(root: View) {
        val header = findHeader(root) ?: return
        val ctx = root.context
        val bg = themeColor(ctx, android.R.attr.colorBackground)

        header.background = GlassHeaderDrawable(bg)
        ViewCompat.setTranslationZ(header, dp(ctx, 6f)) // draw above the scrolling content

        styleTitle(header)
        header.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> styleTitle(header) }

        slideContentUnderHeader(root, header)

        // header drops in softly
        header.alpha = 0f
        header.translationY = -dp(ctx, 14f)
        header.animate().alpha(1f).translationY(0f).setDuration(380)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f)).start()
    }

    // ------------------------------------------------------------------ finding things

    private fun findHeader(root: View): View? {
        root.findViewById<View>(R.id.home_header)?.let { return it }
        root.findViewById<View>(R.id.settings_header)?.let { return it }
        return findFirst(root) { it is Toolbar }
    }

    private fun findFirst(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findFirst(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    // ------------------------------------------------------------------ title pill

    /** Wraps the title text in a small glass capsule (re-applied because Toolbar recreates its title view). */
    private fun styleTitle(header: View) {
        if (header !is ViewGroup) return
        var tv: TextView? = null
        for (i in 0 until header.childCount) {
            val c = header.getChildAt(i)
            if (c is TextView && c.text.isNotBlank()) { tv = c; break }
        }
        tv ?: return
        if (tv.background is GlassButtonDrawable) return
        val ctx = header.context
        tv.background = GlassButtonDrawable(0xFF4DA3FF.toInt(), capsule = true, strength = 0.55f)
        val h = dp(ctx, 16f).toInt()
        val v = dp(ctx, 6f).toInt()
        tv.setPadding(h, v, h, v)
    }

    // ------------------------------------------------------------------ content under the header

    private fun slideContentUnderHeader(root: View, header: View) {
        val swipe = root.findViewById<View>(R.id.swipe_refresh)
        val extra = root.findViewById<View>(R.id.card_search) // Home's fallback search card
        val parent = header.parent

        var under = false
        if (parent is ConstraintLayout && swipe != null) {
            val lp = swipe.layoutParams as? ConstraintLayout.LayoutParams
            if (lp != null && (lp.topToBottom == header.id || (extra != null && lp.topToBottom == extra.id))) {
                lp.topToBottom = ConstraintLayout.LayoutParams.UNSET
                lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                lp.topMargin = 0
                swipe.layoutParams = lp
                under = true
            }
        } else if (parent is FrameLayout) {
            under = true // Settings: scroll view and header share one FrameLayout
        }
        if (!under) return

        val scroll = findFirst(root) { (it is RecyclerView || it is NestedScrollView) } as? ViewGroup ?: return
        val baseTop = scroll.paddingTop
        scroll.clipToPadding = false

        fun update() {
            var top = header.bottom
            if (extra != null && extra.visibility == View.VISIBLE) top = maxOf(top, extra.bottom)
            val wanted = top + baseTop
            if (scroll.paddingTop != wanted) {
                scroll.setPadding(scroll.paddingLeft, wanted, scroll.paddingRight, scroll.paddingBottom)
                (swipe as? SwipeRefreshLayout)?.setProgressViewOffset(false, top, top + dp(root.context, 64f).toInt())
            }
        }
        header.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
        extra?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
        header.post { update() }
    }

    // ------------------------------------------------------------------ helpers

    private fun themeColor(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ctx.getColor(tv.resourceId) else tv.data
    }

    private fun dp(ctx: Context, v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)
}

/** Frosted tint on top, melting into the page towards the bottom (no hard edge). */
class GlassHeaderDrawable(private val bg: Int) : Drawable() {
    private val fill = Paint()
    private val sheen = Paint()

    override fun onBoundsChange(b: android.graphics.Rect) {
        super.onBoundsChange(b)
        if (b.isEmpty) return
        val glass = ColorUtils.blendARGB(ColorUtils.blendARGB(bg, Color.WHITE, 0.05f), 0xFF4DA3FF.toInt(), 0.06f)
        val t = b.top.toFloat()
        val bt = b.bottom.toFloat()
        fill.shader = LinearGradient(
            0f, t, 0f, bt,
            intArrayOf(
                ColorUtils.setAlphaComponent(glass, 0xF4),
                ColorUtils.setAlphaComponent(glass, 0xDC),
                ColorUtils.setAlphaComponent(bg, 0x66),
                ColorUtils.setAlphaComponent(bg, 0x00)
            ),
            floatArrayOf(0f, 0.5f, 0.82f, 1f), Shader.TileMode.CLAMP
        )
        sheen.shader = LinearGradient(
            0f, t, 0f, t + b.height() * 0.5f,
            Color.argb(0x1C, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, fill)
        canvas.drawRect(bounds, sheen)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
