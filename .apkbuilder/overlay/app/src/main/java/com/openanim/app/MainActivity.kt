package com.openanim.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
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

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var statusCard: LinearLayout

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
        statusText = text("", 15f, Color.WHITE, false)
        statusCard.addView(statusText)
        root.addView(statusCard)

        // 1) إمكانية الوصول
        root.addView(button("1) تفعيل خدمة إمكانية الوصول", true) {
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

        // 2) البطارية
        root.addView(button("2) استثناء التطبيق من توفير البطارية", false) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                toast("مفعّل مسبقاً")
            } else {
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
        })

        // الإعدادات
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
        durLabel.text = "مدة الانيميشن: ${Prefs.duration(this)} ms"
        root.addView(durLabel)

        val seek = SeekBar(this).apply {
            max = 500
            progress = (Prefs.duration(this@MainActivity) - 200).coerceIn(0, 500)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    Prefs.setDuration(this@MainActivity, 200 + p)
                    durLabel.text = "مدة الانيميشن: ${200 + p} ms"
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(seek)

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
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val on = isServiceEnabled()
        statusText.text = if (on) {
            "✅ الخدمة شغّالة — افتح أي تطبيق من الشاشة الرئيسية"
        } else {
            "⚠️ الخدمة غير مفعّلة. فعّلها من الزر بالأسفل"
        }
        statusCard.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(if (on) 0xFF16301F.toInt() else 0xFF33261A.toInt())
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
