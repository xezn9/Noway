package com.example.glassvolume

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.concurrent.Executors

/**
 * سبب التأخير القديم: كل ضغطة كانت تسوي ثلاث أشياء ورا بعض:
 *   انتظار 260ms (حتى ما تطلع بلقطة الشاشة)  ->  أخذ لقطة للشاشة  ->  بناء الواجهة من الصفر.
 *
 * الحل الحالي:
 *  1) النافذة + الواجهة تنبني مرة وحدة وقت تشغيل الخدمة وتبقى جاهزة (شفافة تماماً وما تلمس).
 *  2) اللقطة تبدأ بنفس لحظة الضغط وتشتغل «بالتوازي» مع التأخير، مو بعده.
 *  3) زر رفع الصوت: بدون أي تأخير. زر خفض الصوت: تأخير قابل للتعديل من التطبيق
 *     (لأن لقطة الشاشة تنؤخذ بـ خفض الصوت + باور، واللوحة لازم ما تكون ظاهرة وقتها).
 *  4) اللوحة ما تنعرض أبداً وفيه لقطة قيد التنفيذ، حتى ما تنلقط اللوحة نفسها خلف الزجاج.
 */
class VolumeAccessibilityService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var ctrl: VolumeController
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var params: WindowManager.LayoutParams? = null

    // نافذة اللمس الصغيرة: بس بمكان اللوحة. كل لمسة برّاها تروح للتطبيق اللي تحت طبيعي (سحب الصفحات يشتغل)
    private var catcher: TouchCatcher? = null
    private var catcherParams: WindowManager.LayoutParams? = null
    private var panelBounds: Rect? = null
    private var catcherTouchable = false

    private val handler = Handler(Looper.getMainLooper())
    private val shotExecutor = Executors.newSingleThreadExecutor()

    private var shown = false        // النافذة ظاهرة (اللوحة تعرض أو تطلع)
    private var pendingShow = false  // طلب ظهور بانتظار التأخير/اللقطة
    private var capturing = false    // لقطة قيد التنفيذ
    private var showDueAt = 0L

    private val revealCheck = Runnable { tryReveal() }
    private val watchdog = Runnable {
        // حماية: إذا النظام ما رجّع نتيجة اللقطة، نعرض بآخر لقطة موجودة
        if (pendingShow) { capturing = false; showDueAt = 0L; tryReveal() }
    }

    // آخر مرة ظهرت نافذة من تطبيق ثاني (لقطة الشاشة، لوحة النظام...)
    private var lastForeignWindowAt = 0L
    private var screenReceiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { stopRepeat() }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        ctrl = VolumeController(this)
        createOverlay()
        AlwaysOn.startKeepAlive(this)
        registerScreenReceiver()
        // إذا المستخدم فعّلها من زر التطبيق: نرجّعه للتطبيق بدل ما يضل بالإعدادات
        handler.postDelayed({ AlwaysOn.returnToAppIfWaiting(this) }, 400L)
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        try {
            val f = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenReceiver, f)
            screenReceiverRegistered = true
        } catch (_: Throwable) { }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (e.packageName?.toString() == packageName) return
        // ظهرت نافذة من نظام/تطبيق ثاني (مثلاً واجهة لقطة الشاشة): ما نكمل تكرار الضغط المطوّل
        // (احتمال ضاع حدث رفع الإصبع)، ونوقف اللقطات الحية شوية حتى ما نثقّل الجهاز وقت اللقطة
        lastForeignWindowAt = SystemClock.uptimeMillis()
        if (repeatingUp != null) stopRepeat()
    }

    override fun onInterrupt() { stopRepeat() }

    /**
     * نلتقط أزرار الصوت ونغيّر الصوت بنفسنا ونمنع لوحة النظام.
     * الضغط المطوّل: الخدمة تسوي تكرارها الخاص (تكرار النظام ما يوصل لخدمات إمكانية الوصول
     * بشكل موثوق)، وكل تكرار يتعامل معه كضغطة جديدة، وبعد شوية يسرّع.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) {
            // أي زر ثاني ينضغط وقت التكرار = غالباً مو ضغط صوت عادي (مثل لقطة الشاشة): نوقف التكرار
            if (repeatingUp != null) stopRepeat()
            return false
        }
        val up = code == KeyEvent.KEYCODE_VOLUME_UP
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // تكرار النظام (repeatCount > 0) نتجاهله حتى ما يتضاعف مع تكرارنا
                if (event.repeatCount == 0) {
                    stopRepeat()
                    pressVolume(up)
                    startRepeat(up)
                }
            }
            KeyEvent.ACTION_UP -> stopRepeat()   // أي رفع لزر صوت يوقف التكرار (حتى لو الاتجاه ثاني)
        }
        return true
    }

    /** ضغطة وحدة: غيّر الصوت + أظهر اللوحة أو جدّد مؤقتها */
    private fun pressVolume(up: Boolean): Boolean {
        val changed = ctrl.adjustMusic(if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER)
        when {
            shown -> ctrl.bump()          // اللوحة ظاهرة: بس نجدد المؤقت
            pendingShow -> {}             // بانتظار الظهور
            else -> requestShow(if (up) 0L else downDelayMs())
        }
        return changed
    }

    /* ───────────── تكرار الضغط المطوّل ───────────── */

    private var repeatingUp: Boolean? = null
    private var repeatCount = 0
    private var repeatStartedAt = 0L

    private var repeatExpectedAt = 0L

    private val repeatTick = object : Runnable {
        override fun run() {
            val up = repeatingUp ?: return
            val now = SystemClock.uptimeMillis()
            // أمان 1: إذا ضاع حدث الرفع (مثلاً انطفت الشاشة) لا نكمل للأبد
            if (now - repeatStartedAt > REPEAT_MAX_MS) { stopRepeat(); return }
            // أمان 2: إذا الجهاز تعلّق (لاق) والتكرار تأخر كثير عن موعده، غالباً حدث رفع الإصبع ضاع
            // وسط اللاق (مثلاً وقت لقطة الشاشة) فنوقف بدل ما نكمل نخفض الصوت
            if (repeatExpectedAt != 0L && now - repeatExpectedAt > REPEAT_STALL_MS) { stopRepeat(); return }
            repeatCount++
            // أمان 3: وصلنا للحد الأعلى/الأدنى؟ ما فيه فايدة نكمل
            if (!pressVolume(up)) { stopRepeat(); return }
            // يبدأ بطيء وكل تكرار يسرّع لين يوصل أقصى سرعة
            val interval = maxOf(REPEAT_MIN_INTERVAL, REPEAT_START_INTERVAL - repeatCount * 8L)
            repeatExpectedAt = SystemClock.uptimeMillis() + interval
            handler.postDelayed(this, interval)
        }
    }

    private fun startRepeat(up: Boolean) {
        repeatingUp = up
        repeatCount = 0
        repeatStartedAt = SystemClock.uptimeMillis()
        repeatExpectedAt = repeatStartedAt + REPEAT_INITIAL_DELAY
        handler.postDelayed(repeatTick, REPEAT_INITIAL_DELAY)
    }

    private fun stopRepeat() {
        repeatingUp = null
        repeatExpectedAt = 0L
        handler.removeCallbacks(repeatTick)
    }

    private fun downDelayMs(): Long =
        getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getInt(Prefs.KEY_DOWN_DELAY, Prefs.DEFAULT_DOWN_DELAY)
            .toLong()

    /* ───────────── الظهور ───────────── */

    private fun requestShow(delayMs: Long) {
        pendingShow = true
        val now = SystemClock.uptimeMillis()
        showDueAt = now + delayMs

        // لقطة حديثة (أقل من ثانية)؟ نستخدمها وما نحتاج لقطة جديدة
        val cacheOk = ctrl.lastShot != null && now - ctrl.lastShotAt < CACHE_MS
        if (cacheOk) capturing = false else captureBackdrop()

        handler.removeCallbacks(revealCheck)
        handler.removeCallbacks(watchdog)
        if (delayMs <= 0L) tryReveal() else handler.postDelayed(revealCheck, delayMs)
        handler.postDelayed(watchdog, delayMs + 700L)
    }

    private fun tryReveal() {
        if (!pendingShow) return
        if (SystemClock.uptimeMillis() < showDueAt) return  // revealCheck راح يكمّل عند الموعد
        if (capturing) return                               // ننتظر اللقطة (onCaptured راح يكمّل)
        reveal()
    }

    private fun reveal() {
        pendingShow = false
        handler.removeCallbacks(revealCheck)
        handler.removeCallbacks(watchdog)
        ctrl.shot = ctrl.lastShot
        ctrl.reloadStyle()   // آخر إعدادات زجاج/ألوان من التطبيق
        shown = true
        setWindowShown(true)
        ctrl.active = true
        startLive()
    }

    /** الواجهة خلصت أنيميشن الخروج */
    private fun onPanelClosed() {
        shown = false
        ctrl.active = false
        ctrl.dragging = false
        ctrl.shot = null
        stopLive()
        setWindowShown(false)
    }

    private fun captureBackdrop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { capturing = false; return }
        capturing = true
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, shotExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    // هذا يشتغل على thread خلفي: النسخ يكلّف شوية، ما نحمّل الـ main thread
                    var img: ImageBitmap? = null
                    try {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        img = hw?.copy(Bitmap.Config.ARGB_8888, false)?.asImageBitmap()
                    } catch (_: Throwable) { }
                    try { result.hardwareBuffer.close() } catch (_: Throwable) { }
                    handler.post { onCaptured(img) }
                }

                override fun onFailure(errorCode: Int) {
                    handler.post { onCaptured(null) }
                }
            })
        } catch (_: Throwable) {
            capturing = false
        }
    }

    /* ───────────── خلفية حية ─────────────
       نلتقط نافذة التطبيق اللي تحت اللوحة بس (takeScreenshotOfWindow، أندرويد 14+) فما تنلقط اللوحة نفسها،
       ونرسمها فوق اللقطة الثابتة (اللي فيها شريط الحالة وغيره). النظام يحدّد الفاصل بين لقطتين بحوالي 333ms. */

    private var liveRunning = false
    private var liveBusy = false

    private val liveTick = object : Runnable {
        override fun run() {
            if (!liveRunning) return
            // نوقف اللقطات الحية أثناء الضغط المطوّل وبعد ظهور نافذة نظام (لقطة الشاشة) حتى ما يتعلّق الجهاز
            val calm = repeatingUp == null && SystemClock.uptimeMillis() - lastForeignWindowAt > FOREIGN_PAUSE_MS
            if (!liveBusy && calm) captureLiveFrame()
            handler.postDelayed(this, LIVE_INTERVAL_MS)
        }
    }

    private fun startLive() {
        if (Build.VERSION.SDK_INT < 34) return
        val on = getSharedPreferences(Prefs.NAME, MODE_PRIVATE).getBoolean(Prefs.KEY_LIVE_BG, true)
        if (!on || liveRunning) return
        liveRunning = true
        handler.postDelayed(liveTick, 30L)
    }

    private fun stopLive() {
        liveRunning = false
        liveBusy = false
        handler.removeCallbacks(liveTick)
        ctrl.live = null
        ctrl.liveRect = null
        ctrl.livePrev = null
        ctrl.livePrevRect = null
    }

    private fun captureLiveFrame() {
        if (Build.VERSION.SDK_INT < 34) return
        try {
            // أكبر نافذة تطبيق ظاهرة (نتجاهل نافذة اللوحة والنوافذ الصغيرة العائمة)
            var best: AccessibilityWindowInfo? = null
            var bestArea = 0L
            val r = Rect()
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                w.getBoundsInScreen(r)
                val a = r.width().toLong() * r.height().toLong()
                if (a > bestArea) { bestArea = a; best = w }
            }
            val win = best ?: return
            val bounds = Rect().also { win.getBoundsInScreen(it) }
            liveBusy = true
            takeScreenshotOfWindow(win.id, shotExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    var img: ImageBitmap? = null
                    try {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        img = hw?.copy(Bitmap.Config.ARGB_8888, false)?.asImageBitmap()
                    } catch (_: Throwable) { }
                    try { result.hardwareBuffer.close() } catch (_: Throwable) { }
                    handler.post {
                        liveBusy = false
                        if (liveRunning && img != null) {
                            ctrl.livePrev = ctrl.live
                            ctrl.livePrevRect = ctrl.liveRect
                            ctrl.liveRect = bounds
                            ctrl.live = img
                            ctrl.liveSeq++
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    handler.post { liveBusy = false }
                }
            })
        } catch (_: Throwable) {
            liveBusy = false
        }
    }

    private fun onCaptured(img: ImageBitmap?) {
        capturing = false
        if (img != null) {
            ctrl.lastShot = img
            ctrl.lastShotAt = SystemClock.uptimeMillis()
        }
        tryReveal()
    }

    /* ───────────── النافذة الجاهزة مسبقاً ───────────── */

    private fun createOverlay() {
        if (view != null) return
        val o = OverlayOwner()
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setViewTreeViewModelStoreOwner(o)
            setContent {
                VolumeOverlay(
                    ctrl,
                    onTouchable = { setTouchable(it) },
                    onPanelBounds = { onPanelBounds(it) },
                    onClosed = { onPanelClosed() },
                )
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            HIDDEN_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            alpha = 0f
            title = "GlassVolume"
        }
        owner = o
        view = v
        params = lp
        wm.addView(v, lp)

        // نافذة اللمس: حجمها يتبع اللوحة، وأي لمسة برّاها توصلها كـ ACTION_OUTSIDE (نسكّر اللوحة) وتكمل للتطبيق اللي تحت
        val c = TouchCatcher(
            this,
            onForward = { c, ev -> forwardTouch(c, ev) },
            onOutside = { if (shown) ctrl.requestClose() },
        )
        val clp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            CATCHER_OFF_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "GlassVolumeTouch"
        }
        catcher = c
        catcherParams = clp
        wm.addView(c, clp)
    }

    /**
     * النافذة الكبيرة (الرسم) ما تلمس أبداً: بس alpha 0 ↔ 1. اللمس كله عن طريق النافذة الصغيرة.
     * التبديل بدون إعادة بناء ولا تغيير حجم = شبه فوري.
     */
    private fun setWindowShown(on: Boolean) {
        val v = view ?: return
        val lp = params ?: return
        lp.alpha = if (on) 1f else 0f
        try { wm.updateViewLayout(v, lp) } catch (_: Exception) { }
        if (!on) setTouchable(false)
    }

    /** تشغيل/إيقاف نافذة اللمس الصغيرة (أثناء الإغلاق نطفيها حتى كل اللمس يروح للتطبيق اللي تحت) */
    private fun setTouchable(on: Boolean) {
        val c = catcher ?: return
        val lp = catcherParams ?: return
        if (on == catcherTouchable) return
        catcherTouchable = on
        lp.flags = if (on) CATCHER_ON_FLAGS else CATCHER_OFF_FLAGS
        if (on) panelBounds?.let { b ->
            lp.x = b.left; lp.y = b.top
            lp.width = b.width().coerceAtLeast(1); lp.height = b.height().coerceAtLeast(1)
        }
        try { wm.updateViewLayout(c, lp) } catch (_: Exception) { }
    }

    /** الواجهة تبلّغنا بمكان اللوحة (بإحداثيات نافذتها) → نحوّله لإحداثيات الشاشة ونحط عليه نافذة اللمس */
    private fun onPanelBounds(r: ComposeRect) {
        val v = view ?: return
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val pad = (16 * resources.displayMetrics.density).toInt()   // هامش للتكبير وقت اللمس
        val b = Rect(
            r.left.toInt() + loc[0] - pad,
            r.top.toInt() + loc[1] - pad,
            r.right.toInt() + loc[0] + pad,
            r.bottom.toInt() + loc[1] + pad,
        )
        panelBounds = b
        if (!catcherTouchable) return
        val c = catcher ?: return
        val lp = catcherParams ?: return
        val w = b.width().coerceAtLeast(1)
        val h = b.height().coerceAtLeast(1)
        if (lp.x == b.left && lp.y == b.top && lp.width == w && lp.height == h) return
        lp.x = b.left; lp.y = b.top; lp.width = w; lp.height = h
        try { wm.updateViewLayout(c, lp) } catch (_: Exception) { }
    }

    /** لمسة داخل اللوحة: ننقلها لواجهة الرسم (بنفس الإحداثيات) حتى السلايدرات والأزرار تشتغل */
    private fun forwardTouch(c: View, ev: MotionEvent) {
        val a = view ?: return
        val la = IntArray(2); val lc = IntArray(2)
        a.getLocationOnScreen(la); c.getLocationOnScreen(lc)
        val copy = MotionEvent.obtain(ev)
        copy.offsetLocation((lc[0] - la[0]).toFloat(), (lc[1] - la[1]).toFloat())
        try { a.dispatchTouchEvent(copy) } finally { copy.recycle() }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (screenReceiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (_: Throwable) { }
            screenReceiverRegistered = false
        }
        AlwaysOn.stopKeepAlive(this)
        view?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        catcher?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        owner?.destroy()
        view = null; owner = null; params = null
        catcher = null; catcherParams = null
        shotExecutor.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val CACHE_MS = 1000L
        const val LIVE_INTERVAL_MS = 120L        // نعيد المحاولة بسرعة: النظام يرفض اللقطة إذا مر أقل من ~333ms، فنلقط بأول لحظة مسموحة بدل ما ننتظر 336 كاملة
        const val REPEAT_INITIAL_DELAY = 380L   // قبل ما يبدأ التكرار (قريب من مهلة الضغط المطوّل)
        const val REPEAT_START_INTERVAL = 130L  // أول فاصل بين التكرارات
        const val REPEAT_MIN_INTERVAL = 45L     // أسرع فاصل
        const val REPEAT_MAX_MS = 6_000L
        const val REPEAT_STALL_MS = 250L        // تأخر التكرار أكثر من هذا = الجهاز معلّق، نوقف
        const val FOREIGN_PAUSE_MS = 1_800L     // مدة إيقاف اللقطات الحية بعد ظهور نافذة نظام
        const val CATCHER_ON_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or      // اللمس برّا النافذة يروح للتطبيق اللي تحت
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or  // ونوصلنا إشعار بيه (ACTION_OUTSIDE)
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        const val CATCHER_OFF_FLAGS = CATCHER_ON_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        const val HIDDEN_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    }
}

/** ComposeView خارج Activity يحتاج Lifecycle + SavedState + ViewModelStore */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/** نافذة شفافة بحجم اللوحة بس: تنقل لمسها للواجهة، وتبلّغ لما تجي لمسة من برّاها */
private class TouchCatcher(
    ctx: Context,
    private val onForward: (View, MotionEvent) -> Unit,
    private val onOutside: () -> Unit,
) : View(ctx) {
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside()
            return false
        }
        onForward(this, event)
        return true
    }
}
