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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import com.google.android.material.appbar.MaterialToolbar
import com.mfp.filemanager.ui.compose.GlassTitleState
import com.mfp.filemanager.ui.util.Immersive
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
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

        header.background = GlassHeaderDrawable(bg, dp(ctx, 30f))
        ViewCompat.setTranslationZ(header, dp(ctx, 6f)) // draw above the scrolling content

        // titles of toolbars are centred like the Home / Settings capsules
        (header as? MaterialToolbar)?.isTitleCentered = true
        keepClearOfCutout(header)

        styleTitle(header)
        header.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> styleTitle(header) }

        val syncScroll = slideContentUnderHeader(root, header)

        // header drops in softly
        header.alpha = 0f
        header.translationY = -dp(ctx, 14f)
        header.animate().alpha(1f).translationY(0f).setDuration(380)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .withEndAction { syncScroll?.invoke() } // the animation ends at 0: put it back where the scroll wants it
            .start()
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

    /**
     * The title TextView stays where it is (layout, pager swipes, selection-mode titles ...) but
     * becomes invisible; GlassTitleOverlay draws a real Backdrop liquid-glass capsule + the text on top
     * of it. Re-applied because Toolbar recreates its title view.
     */
    private fun styleTitle(header: View) {
        if (header !is ViewGroup) return
        val bar = header as? Toolbar
        var tv: TextView? = null
        if (bar != null) {
            // a Toolbar also holds a subtitle TextView: pick the real title one
            val t = bar.title?.toString().orEmpty()
            for (i in 0 until header.childCount) {
                val c = header.getChildAt(i)
                if (c is TextView && c.text.isNotBlank() && c.text.toString() == t) { tv = c; break }
            }
        }
        if (tv == null) {
            for (i in 0 until header.childCount) {
                val c = header.getChildAt(i)
                if (c is TextView && c.text.isNotBlank()) { tv = c; break }
            }
        }
        tv ?: return
        val ctx = header.context

        // ZArchiver-style second line (folder path): the Toolbar's subtitle. Like the title it
        // stays in the layout but is invisible, the glass capsule draws it.
        val sub = if (bar != null) GlassTitleState.findSubtitleView(bar, tv) else null
        if (sub != null) {
            sub.background = null
            sub.alpha = 0f
            val subPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, ctx.resources.displayMetrics)
            if (kotlin.math.abs(sub.textSize - subPx) > 0.5f) sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            if (sub.paddingLeft != 0 || sub.paddingTop != 0 || sub.paddingRight != 0 || sub.paddingBottom != 0) {
                sub.setPadding(0, 0, 0, 0)
            }
        }

        // with a path line the title gets a bit smaller and loses its vertical padding (the capsule
        // adds its own around both lines); without it everything is exactly as before
        if (!originalTitleSize.containsKey(tv)) originalTitleSize[tv] = tv.textSize
        val wantPx = if (sub != null)
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17f, ctx.resources.displayMetrics)
        else originalTitleSize[tv] ?: tv.textSize
        if (kotlin.math.abs(tv.textSize - wantPx) > 0.5f) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, wantPx)
        val h = dp(ctx, 18f).toInt()
        val v = if (sub != null) 0 else dp(ctx, 7f).toInt()
        if (tv.paddingTop != v || tv.paddingBottom != v || tv.paddingLeft != h || tv.paddingRight != h) {
            tv.setPadding(h, v, h, v)
        }

        if (GlassTitleState.isAttached(tv)) return
        tv.background = null
        tv.alpha = 0f // the overlay draws it
        GlassTitleState.attach(tv)
    }

    private val originalTitleSize = java.util.WeakHashMap<TextView, Float>()

    // ------------------------------------------------------------------ camera cut-out

    /** Full screen: nothing pushes the header below the camera hole any more, so do it ourselves. */
    private fun keepClearOfCutout(header: View) {
        val baseTop = header.paddingTop
        val baseHeight = header.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
        var applied = -1

        fun setTop(top: Int) {
            if (top == applied) return
            applied = top
            header.setPadding(header.paddingLeft, baseTop + top, header.paddingRight, header.paddingBottom)
            val lp = header.layoutParams
            if (lp != null && baseHeight > 0) { // fixed height (Toolbar): grow by the same amount
                lp.height = baseHeight + top
                header.layoutParams = lp
            }
        }

        setTop(Immersive.topInset)
        ViewCompat.setOnApplyWindowInsetsListener(header) { _, insets ->
            setTop(insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top)
            insets
        }
        header.doOnAttach {
            ViewCompat.getRootWindowInsets(it)
                ?.getInsets(WindowInsetsCompat.Type.displayCutout())?.top?.let { top -> setTop(top) }
        }
    }

    // ------------------------------------------------------------------ content under the header

    private fun slideContentUnderHeader(root: View, header: View): (() -> Unit)? {
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
        if (!under) return null

        val scroll = findFirst(root) { (it is RecyclerView || it is NestedScrollView) } as? ViewGroup ?: return null
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

        return attachScrollAway(scroll, header)
    }

    /**
     * The title belongs to the page: it moves up together with the content when the page is
     * scrolled (and comes back when scrolled back to the top). A toolbar that currently shows
     * actions (selection mode, paste ...) stays pinned so the actions never scroll out of reach.
     */
    private fun attachScrollAway(scroll: ViewGroup, header: View): () -> Unit {
        fun scrolledBy(): Int = when (scroll) {
            is RecyclerView -> {
                val lm = scroll.layoutManager as? LinearLayoutManager
                val first = lm?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                when {
                    lm == null || scroll.childCount == 0 -> 0
                    first == 0 -> lm.findViewByPosition(0)
                        ?.let { (scroll.paddingTop - it.top).coerceAtLeast(0) } ?: 0
                    first > 0 -> Int.MAX_VALUE
                    else -> 0
                }
            }
            is NestedScrollView -> scroll.scrollY
            else -> 0
        }

        fun sync() {
            val bar = header as? Toolbar
            val pinned = bar != null && bar.menu.hasVisibleItems()
            val y = if (pinned) 0 else minOf(scrolledBy(), header.height)
            header.translationY = -y.toFloat()
        }

        when (scroll) {
            is RecyclerView -> scroll.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    sync()
                }
            })
            is NestedScrollView -> scroll.setOnScrollChangeListener(
                NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> sync() }
            )
        }
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> sync() }
        header.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> sync() }
        header.post { sync() }

        val handle: () -> Unit = { sync() }
        return handle
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

/**
 * The page colour stays solid behind the title capsule and fades out ONLY at the bottom edge
 * (the last [fadePx] pixels), so the list melts away under the header instead of being cut off.
 * The glass itself (title capsule) is the real Backdrop one drawn by GlassTitleOverlay.
 */
class GlassHeaderDrawable(private val bg: Int, private val fadePx: Float) : Drawable() {
    private val fill = Paint()

    override fun onBoundsChange(b: android.graphics.Rect) {
        super.onBoundsChange(b)
        if (b.isEmpty) return
        val h = b.height().toFloat()
        val fade = fadePx.coerceIn(1f, h)
        val start = (h - fade) / h // fraction where the fade begins; everything above it is solid
        // eased (smoothstep) fade so there is no visible edge where it starts
        val steps = 8
        val colors = IntArray(steps + 2)
        val pos = FloatArray(steps + 2)
        val top = 0xEE
        colors[0] = ColorUtils.setAlphaComponent(bg, top)
        pos[0] = 0f
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val eased = t * t * (3f - 2f * t)
            colors[i + 1] = ColorUtils.setAlphaComponent(bg, (top * (1f - eased)).toInt())
            pos[i + 1] = start + (1f - start) * t
        }
        pos[1] = maxOf(pos[1], 0.0001f)
        fill.shader = LinearGradient(
            0f, b.top.toFloat(), 0f, b.bottom.toFloat(), colors, pos, Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, fill)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
