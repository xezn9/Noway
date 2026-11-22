package com.openanim.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.graphics.drawable.toBitmap

class AnimService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager

    // اسم التطبيق (بحروف صغيرة) -> اسم الحزمة
    @Volatile private var labels: Map<String, String> = emptyMap()
    @Volatile private var launchers: Set<String> = emptySet()
    private var lastReload = 0L

    private var overlay: ExpandView? = null
    private var expandDone = false
    private var appShown = false
    private var hiding = false

    private val hideRunnable = Runnable { hideOverlay() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        reload()
    }

    private fun reload() {
        lastReload = System.currentTimeMillis()
        Thread {
            val pm = packageManager
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            launchers = pm.queryIntentActivities(homeIntent, 0)
                .map { it.activityInfo.packageName }
                .toSet()

            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val map = HashMap<String, String>()
            for (ri in pm.queryIntentActivities(mainIntent, 0)) {
                val l = ri.loadLabel(pm).toString().trim().lowercase()
                if (l.isNotEmpty()) map[l] = ri.activityInfo.packageName
            }
            labels = map
        }.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::wm.isInitialized) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> onClick(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindow(event)
        }
    }

    // ---------- الضغط على أيقونة داخل اللانچر ----------

    private fun onClick(e: AccessibilityEvent) {
        if (!Prefs.enabled(this)) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg !in launchers) return
        val src = e.source ?: return

        val hit = resolve(src)
        if (hit == null) {
            if (System.currentTimeMillis() - lastReload > 15000) reload()
            return
        }
        startAnimation(hit.first, hit.second)
    }

    private fun resolve(src: AccessibilityNodeInfo): Pair<String, Rect>? {
        var n: AccessibilityNodeInfo? = src
        var depth = 0
        while (n != null && depth < 4) {
            val b = boundsOf(n)
            if (plausible(b)) {
                val own = textOf(n)?.let { lookup(it) }
                if (own != null) return own to b
                for (i in 0 until minOf(n.childCount, 4)) {
                    val c = n.getChild(i) ?: continue
                    val p = textOf(c)?.let { lookup(it) }
                    if (p != null) return p to b
                }
            }
            n = n.parent
            depth++
        }
        return null
    }

    private fun textOf(n: AccessibilityNodeInfo): String? =
        (n.text ?: n.contentDescription)?.toString()?.takeIf { it.isNotBlank() }

    private fun boundsOf(n: AccessibilityNodeInfo): Rect =
        Rect().also { n.getBoundsInScreen(it) }

    private fun plausible(b: Rect): Boolean {
        val d = resources.displayMetrics.density
        val w = b.width() / d
        val h = b.height() / d
        return w in 28f..240f && h in 28f..260f
    }

    private fun lookup(raw: String): String? {
        val t = raw.trim().lowercase()
        labels[t]?.let { return it }
        // بعض اللانچرات تضيف "، 3 إشعارات" بعد الاسم
        var best: String? = null
        var bestLen = 0
        for ((l, p) in labels) {
            if (l.length > bestLen && (t.startsWith("$l,") || t.startsWith("$l،"))) {
                best = p
                bestLen = l.length
            }
        }
        return best
    }

    // ---------- الانيميشن ----------

    private fun startAnimation(pkg: String, bounds: Rect) {
        val drawable = try {
            packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            return
        }
        val bmp = drawable.toBitmap(160, 160)
        val avg = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt()

        // مستطيل الأيقونة نفسها (إذا كان العنصر يحتوي الأيقونة + الاسم نأخذ الجزء العلوي)
        val r = RectF(bounds)
        val side: Float
        val left: Float
        val top: Float
        if (r.height() > r.width() * 1.15f) {
            side = r.width() * 0.74f
            left = r.centerX() - side / 2f
            top = r.top + r.height() * 0.08f
        } else {
            side = minOf(r.width(), r.height())
            left = r.centerX() - side / 2f
            top = r.centerY() - side / 2f
        }
        val start = RectF(left, top, left + side, top + side)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val endColor = if (night) 0xFF111114.toInt() else 0xFFF4F4F8.toInt()

        removeOverlayNow()
        expandDone = false
        appShown = false
        hiding = false

        val dur = Prefs.duration(this).toLong()
        val v = ExpandView(this, bmp, avg, endColor, start)
        v.onExpanded = {
            expandDone = true
            if (appShown) scheduleHide(60) else scheduleHide(500)
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            return
        }
        overlay = v
        v.post { v.expand(dur) }

        // أمان: إذا ما صار شي نخفي بعد فترة
        scheduleHide(dur + 1800)
    }

    // ---------- ظهور نافذة التطبيق ----------

    private fun onWindow(e: AccessibilityEvent) {
        if (overlay == null) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg in launchers || pkg == packageName || pkg == "com.android.systemui") return
        appShown = true
        if (expandDone) scheduleHide(80)
    }

    private fun scheduleHide(delay: Long) {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, delay)
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        if (hiding) return
        hiding = true
        v.fadeOut(180) { removeOverlayNow() }
    }

    private fun removeOverlayNow() {
        handler.removeCallbacks(hideRunnable)
        val v = overlay
        if (v != null) {
            v.cancel()
            try {
                wm.removeViewImmediate(v)
            } catch (e: Exception) {
                // ignore
            }
        }
        overlay = null
        hiding = false
    }

    override fun onInterrupt() {
        if (::wm.isInitialized) removeOverlayNow()
    }

    override fun onDestroy() {
        if (::wm.isInitialized) removeOverlayNow()
        super.onDestroy()
    }
}
