package com.example.glassnav

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlin.math.abs

class GlassNavService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var wm: WindowManager
    private val owner = OverlayOwner()
    private val views = mutableListOf<View>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        owner.start()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        rebuild()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) = rebuild()
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        clear()
        owner.stop()
        super.onDestroy()
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun clear() {
        handler.removeCallbacksAndMessages(null)
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear()
    }

    private fun rebuild() {
        clear()
        val p = Prefs.sp(this)
        if (p.getBoolean(Prefs.BAR, true)) {
            addBar(p.getBoolean(Prefs.COVER, false), p.getBoolean(Prefs.COVER_WHITE, false))
        }
        if (p.getBoolean(Prefs.BACK, true)) {
            addEdge(left = true)
            addEdge(left = false)
        }
    }

    private fun params(w: Int, h: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    )

    private fun composeView(content: @Composable () -> Unit) =
        ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            owner.attachTo(this)
            setContent(content)
        }

    /** Ask the system not to steal gestures that start inside this view. */
    private fun excludeFromSystemGestures(v: View) {
        v.addOnLayoutChangeListener { view, l, t, r, b, _, _, _, _ ->
            view.systemGestureExclusionRects = listOf(Rect(0, 0, r - l, b - t))
        }
    }

    // ---------------------------------------------------------------- Home bar
    private fun addBar(cover: Boolean, white: Boolean) {
        val view = composeView {
            PillBar(
                cover = cover,
                coverWhite = white,
                onHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
                onRecents = { performGlobalAction(GLOBAL_ACTION_RECENTS) }
            )
        }
        excludeFromSystemGestures(view)
        val lp = params(WindowManager.LayoutParams.MATCH_PARENT, dp(if (cover) 56f else 36f), true)
            .apply { gravity = Gravity.BOTTOM }
        wm.addView(view, lp)
        views += view
    }

    // ---------------------------------------------------------- Back gesture
    private fun addEdge(left: Boolean) {
        val state = EdgeState()
        val side = if (left) Gravity.START else Gravity.END

        // 1) visual window (not touchable, shown only while dragging)
        val visual = composeView { EdgeBulge(state, left) }.apply { visibility = View.GONE }
        val vh = dp(260f)
        val vlp = params(dp(140f), vh, false).apply { gravity = Gravity.TOP or side }
        wm.addView(visual, vlp)

        // 2) thin touch strip on the edge
        val touch = View(this)
        excludeFromSystemGestures(touch)
        val tlp = params(dp(18f), dp(200f), true).apply { gravity = side or Gravity.CENTER_VERTICAL }

        var startX = 0f
        var armed = false
        val threshold = dp(70f)
        val full = dp(90f).toFloat()

        touch.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.rawX
                    armed = false
                    vlp.y = (ev.rawY - vh / 2f).toInt().coerceAtLeast(0)
                    wm.updateViewLayout(visual, vlp)
                    handler.removeCallbacksAndMessages(visual)
                    visual.visibility = View.VISIBLE
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = if (left) ev.rawX - startX else startX - ev.rawX
                    state.progress = (dx / full).coerceIn(0f, 1.2f)
                    val nowArmed = dx > threshold
                    if (nowArmed && !armed) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    armed = nowArmed
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = abs(ev.rawX - startX)
                    if (ev.actionMasked == MotionEvent.ACTION_UP && dx > threshold) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                    state.progress = 0f
                    handler.postDelayed({ visual.visibility = View.GONE }, visual, 600)
                    true
                }
                else -> false
            }
        }
        wm.addView(touch, tlp)
        views += visual
        views += touch
    }
}
