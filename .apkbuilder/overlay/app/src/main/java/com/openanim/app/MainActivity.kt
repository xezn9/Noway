package com.openanim.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var statusCard: LinearLayout
    private lateinit var scaleLabel: TextView
    private lateinit var scaleSeek: SeekBar

    private val reqCode = 1001
    private val secure = "android.permission.WRITE_SECURE_SETTINGS"

    private var autoAsked = false
    private var autoSetupTried = false
    @Volatile private var setupRunning = false

    private val binderListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            refreshStatus()
            autoConnect()
        }
    }
    private val deadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { refreshStatus() }
    }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            setupViaShizuku()
        } else {
            runOnUiThread { toast("تم رفض إذن Shizuku") }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(44), dp(20), dp(28))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(text("انيميشن الفتح", 28f, Color.WHITE, true))
        root.addView(
            text(
                "يشغّل انيميشن بأسلوب iOS 26 يتوسع من أيقونة التطبيق عند فتحه من الشاشة الرئيسية، بدون روت.",
                14f, 0xFF9A9AA8.toInt(), false
            ).apply { setPadding(0, dp(6), 0, dp(18)) }
        )

        // بطاقة الحالة
        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        statusText = text("", 15f, Color.WHITE, false).apply { setLineSpacing(0f, 1.25f) }
        statusCard.addView(statusText)
        root.addView(statusCard)

        // ---- الطريقة السريعة: Shizuku ----
        root.addView(text("الطريقة السريعة (Shizuku)", 18f, Color.WHITE, true).apply {
            setPadding(0, dp(24), 0, dp(2))
        })
        root.addView(
            text(
                "يمنح التطبيق الصلاحيات ويفعّل الخدمة تلقائياً، بدون «الإعدادات المقيّدة» ولا خطوات يدوية.",
                12f, 0xFF8A8A98.toInt(), false
            ).apply { setPadding(dp(2), 0, dp(2), 0) }
        )
        root.addView(button("ربط Shizuku ومنح الصلاحيات تلقائياً", true) { connectShizuku() })

        // ---- الطريقة اليدوية ----
        root.addView(text("أو يدوياً (بدون Shizuku)", 18f, Color.WHITE, true).apply {
            setPadding(0, dp(28), 0, dp(2))
        })
        root.addView(button("تفعيل خدمة إمكانية الوصول", false) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                toast("تعذر فتح الإعدادات")
            }
        })
        root.addView(
            text(
                "إذا كان الخيار رمادي (أندرويد 13 وما فوق): افتح معلومات التطبيق ← ⋮ ← «السماح بالإعدادات المقيّدة» ثم ارجع وفعّل الخدمة.",
                12f, 0xFF8A8A98.toInt(), false
            ).apply { setPadding(dp(4), dp(8), dp(4), 0) }
        )
        root.addView(button("فتح معلومات التطبيق", false) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                toast("تعذر فتح معلومات التطبيق")
            }
        })
        root.addView(button("استثناء التطبيق من توفير البطارية", false) { batteryExemption() })

        // ---- الإعدادات ----
        root.addView(text("الإعدادات", 18f, Color.WHITE, true).apply { setPadding(0, dp(28), 0, dp(6)) })

        val sw = Switch(this).apply {
            text = "تفعيل الانيميشن"
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = Prefs.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> Prefs.setEnabled(this@MainActivity, checked) }
            setPadding(0, dp(10), 0, dp(10))
        }
        root.addView(sw)

        val durLabel = text("", 14f, 0xFFB5B5C3.toInt(), false).apply { setPadding(0, dp(14), 0, dp(4)) }
        durLabel.text = "مدة انيميشننا: ${Prefs.duration(this)} ms"
        root.addView(durLabel)
        root.addView(SeekBar(this).apply {
            max = 500
            progress = (Prefs.duration(this@MainActivity) - 200).coerceIn(0, 500)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    Prefs.setDuration(this@MainActivity, 200 + p)
                    durLabel.text = "مدة انيميشننا: ${200 + p} ms"
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        // سرعة انيميشن النظام (تحتاج WRITE_SECURE_SETTINGS)
        scaleLabel = text("", 14f, 0xFFB5B5C3.toInt(), false).apply { setPadding(0, dp(22), 0, dp(4)) }
        scaleLabel.text = scaleText(1f)
        root.addView(scaleLabel)
        scaleSeek = SeekBar(this).apply {
            max = 8 // 0.0x .. 2.0x بخطوة 0.25
            progress = 4
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p * 0.25f
                    scaleLabel.text = scaleText(v)
                    if (fromUser && !setSystemScale(v)) toast("تحتاج منح صلاحية الإعدادات الآمنة أولاً")
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(scaleSeek)
        root.addView(
            text(
                "هذا يغيّر سرعة انيميشن النظام الأصلي (يشتغل بعد ربط Shizuku). 1.0x = الافتراضي، 0 = بدون انيميشن.",
                12f, 0xFF8A8A98.toInt(), false
            ).apply { setPadding(dp(4), dp(6), dp(4), 0) }
        )

        root.addView(
            text(
                "ملاحظة: الانيميشن يشتغل عند الضغط على أيقونة التطبيق في اللانچر (الشاشة الرئيسية والدوك). إذا لم يطابق مكان الأيقونة جرّب لانچر ثاني.",
                12f, 0xFF8A8A98.toInt(), false
            ).apply { setPadding(dp(4), dp(24), dp(4), 0) }
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF0E0E12.toInt())
            isFillViewport = true
            addView(root)
        })

        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permListener)
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        autoConnect()
    }

    // ---------- Shizuku ----------

    private fun connectShizuku() {
        if (!ShizukuHelper.installed(this)) {
            toast("Shizuku غير مثبت — نفتح صفحة التحميل")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            } catch (e: Exception) {
                // ignore
            }
            return
        }
        if (!ShizukuHelper.running()) {
            toast("شغّل Shizuku أولاً (تصحيح لاسلكي أو ADB)")
            openShizukuApp()
            return
        }
        if (!ShizukuHelper.permitted()) {
            try {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    toast("امنح الإذن من داخل تطبيق Shizuku")
                    openShizukuApp()
                } else {
                    Shizuku.requestPermission(reqCode)
                }
            } catch (e: Throwable) {
                toast("تعذر طلب الإذن من Shizuku")
            }
            return
        }
        setupViaShizuku()
    }

    private fun openShizukuApp() {
        packageManager.getLaunchIntentForPackage(ShizukuHelper.PKG)?.let {
            try {
                startActivity(it)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /** يتعرف على Shizuku تلقائياً ويطلب الاتصال بدون ما تضغط شي. */
    private fun autoConnect() {
        if (!ShizukuHelper.running()) return
        if (ShizukuHelper.permitted()) {
            if (!autoSetupTried && (!hasSecure() || !isServiceEnabled())) {
                autoSetupTried = true
                setupViaShizuku()
            }
            return
        }
        if (autoAsked) return
        autoAsked = true
        try {
            if (!Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(reqCode)
            }
        } catch (e: Throwable) {
            // ignore
        }
    }

    private fun setupViaShizuku() {
        if (setupRunning) return
        setupRunning = true
        Thread {
            var msg = ""
            try {
                if (!hasSecure()) {
                    val (code, out) = ShizukuHelper.shell("pm grant $packageName $secure")
                    if (code != 0) {
                        throw RuntimeException(out.trim().ifEmpty { "pm grant ($code)" })
                    }
                }
                // استثناء البطارية تلقائياً (اختياري، نتجاهل الخطأ)
                try {
                    ShizukuHelper.shell("dumpsys deviceidle whitelist +$packageName")
                } catch (e: Exception) {
                    // ignore
                }
                msg = if (hasSecure() && enableAccessibility()) {
                    "تم تفعيل كل شي ✅"
                } else {
                    "انمنحت الصلاحية لكن فشل تفعيل الخدمة، فعّلها يدوياً"
                }
            } catch (e: Exception) {
                msg = "فشل: ${e.message}"
            } finally {
                setupRunning = false
            }
            runOnUiThread {
                toast(msg)
                refreshStatus()
            }
        }.start()
    }

    private fun hasSecure(): Boolean =
        checkSelfPermission(secure) == PackageManager.PERMISSION_GRANTED

    private fun enableAccessibility(): Boolean {
        return try {
            val cn = ComponentName(this, AnimService::class.java)
            val full = cn.flattenToString()
            val short = cn.flattenToShortString()
            val cur = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val list = cur.split(':').filter { it.isNotBlank() }
            if (list.none { it.equals(full, true) || it.equals(short, true) }) {
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    (list + full).joinToString(":")
                )
            }
            Settings.Secure.putString(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- انيميشن النظام ----------

    private fun setSystemScale(v: Float): Boolean {
        if (!hasSecure()) return false
        return try {
            Settings.Global.putFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, v)
            Settings.Global.putFloat(contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, v)
            Settings.Global.putFloat(contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, v)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun scaleText(v: Float): String =
        "سرعة انيميشن النظام الأصلي: " + if (v == 0f) "معطّل" else "${v}x"

    // ---------- البطارية ----------

    private fun batteryExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("مفعّل مسبقاً")
            return
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                toast("تعذر فتح إعدادات البطارية")
            }
        }
    }

    // ---------- الحالة ----------

    private fun refreshStatus() {
        val on = isServiceEnabled()
        val sec = hasSecure()
        val sh = when {
            !ShizukuHelper.installed(this) -> "❌ غير مثبت"
            !ShizukuHelper.running() -> "⚠️ مثبت بس مو شغّال"
            !ShizukuHelper.permitted() -> "🔑 شغّال — يحتاج إذن"
            else -> "✅ جاهز"
        }
        val svc = if (on) "✅ شغّالة" else "⚠️ غير مفعّلة"
        val secTxt = if (sec) "✅ ممنوحة" else "—"
        statusText.text = "الخدمة: $svc\nShizuku: $sh\nالإعدادات الآمنة: $secTxt"
        statusCard.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(if (on) 0xFF16301F.toInt() else 0xFF33261A.toInt())
        }

        scaleSeek.isEnabled = sec
        if (sec) {
            val cur = try {
                Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            } catch (e: Exception) {
                1f
            }
            val p = (cur / 0.25f).roundToInt().coerceIn(0, 8)
            scaleSeek.progress = p
            scaleLabel.text = scaleText(p * 0.25f)
        }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, AnimService::class.java)
        val full = cn.flattenToString()
        val short = cn.flattenToShortString()
        return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }

    // ---------- أدوات واجهة ----------

    private fun text(t: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            stateListAnimator = null
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (primary) 0xFF5B6CFF.toInt() else 0xFF22222B.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(12) }
            setOnClickListener { onClick() }
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
