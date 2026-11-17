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
            val light = p.getBoolean(Prefs.EDGE_LIGHT, false)
            val reserve = if (p.getBoolean(Prefs.BAR, true)) dp(if (p.getBoolean(Prefs.COVER, false)) 56f else 36f) else 0
            addEdge(left = true, light = light, bottomReserve = reserve)
            addEdge(left = false, light = light, bottomReserve = reserve)
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
    private fun addEdge(left: Boolean, light: Boolean, bottomReserve: Int) {
        val state = EdgeState()
        val side = if (left) Gravity.START else Gravity.END

        // 1) visual window: full height, so the bulge can appear at any Y (not touchable)
        val visual = composeView { EdgeBulge(state, left, light) }
        val vlp = params(dp(140f), WindowManager.LayoutParams.MATCH_PARENT, false)
            .apply { gravity = Gravity.TOP or side }
        wm.addView(visual, vlp)

        // 2) thin touch strip along the whole edge (leaves the home bar area free)
        val touch = View(this)
        excludeFromSystemGestures(touch)
        val screenH = wm.currentWindowMetrics.bounds.height()
        val tlp = params(dp(18f), (screenH - bottomReserve).coerceAtLeast(dp(200f)), true)
            .apply { gravity = Gravity.TOP or side }

        var startX = 0f
        val threshold = dp(60f)

        touch.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.rawX
                    state.dx = 0f
                    state.y = ev.rawY
                    state.armed = false
                    state.downCount++
                    state.active = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = if (left) ev.rawX - startX else startX - ev.rawX
                    state.dx = dx
                    state.y = ev.rawY
                    val nowArmed = dx > threshold
                    if (nowArmed && !state.armed) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    state.armed = nowArmed
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = abs(ev.rawX - startX)
                    if (ev.actionMasked == MotionEvent.ACTION_UP && dx > threshold) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                    state.armed = false
                    state.active = false   // the bulge slides back with a bounce
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
