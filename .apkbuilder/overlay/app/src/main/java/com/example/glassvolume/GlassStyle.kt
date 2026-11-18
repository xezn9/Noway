package com.example.glassvolume

import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * كل خيارات مظهر لوحة الصوت (الزجاج + الألوان).
 * القيم الافتراضية = نفس المظهر القديم بالضبط.
 *
 * الشدّات كلها كسور 0..1:
 *  - Blur          → نصف قطر التغبيش (0..MAX_BLUR dp)
 *  - Refraction    → قوة العدسة (انكسار الضوء)
 *  - Transparency  → 1 = شفاف تماماً، 0 = زجاج مصنفر كثيف
 *  - Rim           → لمعة الحافة (النص = الافتراضي ×0.667، الحد الأقصى ×1.5)
 */
data class GlassStyle(
    // ───── الشريط (السلايدر + البطاقة الموسّعة) ─────
    val barBlur: Float = 0.05f,
    val barRefraction: Float = 0.5f,
    val barTransparency: Float = 0.92f,
    val barRim: Float = 1f / RIM_MAX,

    // ───── الأزرار ─────
    val btnBlur: Float = 0.05f,
    val btnRefraction: Float = 0.5f,
    val btnTransparency: Float = 0.90f,
    val btnRim: Float = 1f / RIM_MAX,

    // ───── الشريط الرفيع (قبل اللمس) كنسبة من الحجم الطبيعي ─────
    val thinWidth: Float = 0.08f,
    val thinHeight: Float = 0.50f,

    // ───── الألوان ─────
    val barFill: Color = Color.White,                         // الشريط الأبيض (التعبئة)
    val barTint: Color = Color.White,                         // لون زجاج الشريط
    val iconOnGlass: Color = Color.White,                     // الأيقونات فوق الزجاج
    val iconOnFill: Color = Color.Black.copy(alpha = 0.52f),  // الأيقونات فوق التعبئة
    val btnTint: Color = Color.White,                         // لون زجاج الأزرار
    val btnIcon: Color = Color.White,                         // أيقونات الأزرار
    val silentFill: Color = Color.White,                      // زر الصامت (الخلفية)
    val silentIcon: Color = Color.Black.copy(alpha = 0.6f),   // زر الصامت (الأيقونة)
    val rimColor: Color = Color.White,                        // الحافة اللامعة
    val labelColor: Color = Color.White.copy(alpha = 0.9f),   // نص «الصوت»

    // ───── XOS: ضبابية النظام الحية (بدل backdrop). أندرويد 12+ ─────
    val xos: Boolean = false,
) {
    /** نصف قطر ضبابية النظام (dp): نستعمل سلايدر ضبابية الشريط، بحد أدنى حتى تبين */
    val xosBlurDp: Float get() = 14f + barBlur * 40f

    // الشريط
    val barBlurDp: Dp get() = (barBlur * MAX_BLUR).dp
    val barLensHeight: Dp get() = (barRefraction * BAR_LENS_H).dp
    val barLensAmount: Dp get() = (barRefraction * BAR_LENS_A).dp
    val barTintAlpha: Float get() = (1f - barTransparency) * MAX_TINT
    val barRimMul: Float get() = barRim * RIM_MAX

    // الأزرار
    val btnBlurDp: Dp get() = (btnBlur * MAX_BLUR).dp
    val btnLensHeight: Dp get() = (btnRefraction * BTN_LENS_H).dp
    val btnLensAmount: Dp get() = (btnRefraction * BTN_LENS_A).dp
    val btnTintAlpha: Float get() = (1f - btnTransparency) * MAX_TINT
    val btnRimMul: Float get() = btnRim * RIM_MAX

    fun save(p: SharedPreferences) {
        p.edit()
            .putBoolean(KEY_XOS, xos)
            .putFloat(K_BAR_BLUR, barBlur)
            .putFloat(K_BAR_REFR, barRefraction)
            .putFloat(K_BAR_TRANS, barTransparency)
            .putFloat(K_BAR_RIM, barRim)
            .putFloat(K_BTN_BLUR, btnBlur)
            .putFloat(K_BTN_REFR, btnRefraction)
            .putFloat(K_BTN_TRANS, btnTransparency)
            .putFloat(K_BTN_RIM, btnRim)
            .putFloat(K_THIN_W, thinWidth)
            .putFloat(K_THIN_H, thinHeight)
            .putInt(C_BAR_FILL, barFill.toArgb())
            .putInt(C_BAR_TINT, barTint.toArgb())
            .putInt(C_ICON_GLASS, iconOnGlass.toArgb())
            .putInt(C_ICON_FILL, iconOnFill.toArgb())
            .putInt(C_BTN_TINT, btnTint.toArgb())
            .putInt(C_BTN_ICON, btnIcon.toArgb())
            .putInt(C_SILENT_FILL, silentFill.toArgb())
            .putInt(C_SILENT_ICON, silentIcon.toArgb())
            .putInt(C_RIM, rimColor.toArgb())
            .putInt(C_LABEL, labelColor.toArgb())
            .apply()
    }

    companion object {
        const val MAX_BLUR = 20f        // dp
        const val MAX_TINT = 0.6f       // أقصى كثافة للون الزجاج
        const val BAR_LENS_H = 28f      // dp عند الانكسار 100%
        const val BAR_LENS_A = 56f
        const val BTN_LENS_H = 16f
        const val BTN_LENS_A = 36f
        const val RIM_MAX = 1.5f

        const val KEY_XOS = "g_xos"
        private const val K_BAR_BLUR = "g_bar_blur"
        private const val K_BAR_REFR = "g_bar_refraction"
        private const val K_BAR_TRANS = "g_bar_transparency"
        private const val K_BAR_RIM = "g_bar_rim"
        private const val K_BTN_BLUR = "g_btn_blur"
        private const val K_BTN_REFR = "g_btn_refraction"
        private const val K_BTN_TRANS = "g_btn_transparency"
        private const val K_BTN_RIM = "g_btn_rim"
        private const val K_THIN_W = "g_thin_w2"
        private const val K_THIN_H = "g_thin_h2"
        const val THIN_W_MIN = 0.03f
        const val THIN_W_MAX = 0.60f
        const val THIN_H_MIN = 0.20f
        const val THIN_H_MAX = 1.00f
        private const val C_BAR_FILL = "c_bar_fill"
        private const val C_BAR_TINT = "c_bar_tint"
        private const val C_ICON_GLASS = "c_icon_glass"
        private const val C_ICON_FILL = "c_icon_fill"
        private const val C_BTN_TINT = "c_btn_tint"
        private const val C_BTN_ICON = "c_btn_icon"
        private const val C_SILENT_FILL = "c_silent_fill"
        private const val C_SILENT_ICON = "c_silent_icon"
        private const val C_RIM = "c_rim"
        private const val C_LABEL = "c_label"

        val Default = GlassStyle()

        fun load(p: SharedPreferences): GlassStyle {
            val d = Default
            return GlassStyle(
                barBlur = p.getFloat(K_BAR_BLUR, d.barBlur),
                barRefraction = p.getFloat(K_BAR_REFR, d.barRefraction),
                barTransparency = p.getFloat(K_BAR_TRANS, d.barTransparency),
                barRim = p.getFloat(K_BAR_RIM, d.barRim),
                btnBlur = p.getFloat(K_BTN_BLUR, d.btnBlur),
                btnRefraction = p.getFloat(K_BTN_REFR, d.btnRefraction),
                btnTransparency = p.getFloat(K_BTN_TRANS, d.btnTransparency),
                btnRim = p.getFloat(K_BTN_RIM, d.btnRim),
                thinWidth = p.getFloat(K_THIN_W, d.thinWidth).coerceIn(THIN_W_MIN, THIN_W_MAX),
                thinHeight = p.getFloat(K_THIN_H, d.thinHeight).coerceIn(THIN_H_MIN, THIN_H_MAX),
                barFill = Color(p.getInt(C_BAR_FILL, d.barFill.toArgb())),
                barTint = Color(p.getInt(C_BAR_TINT, d.barTint.toArgb())),
                iconOnGlass = Color(p.getInt(C_ICON_GLASS, d.iconOnGlass.toArgb())),
                iconOnFill = Color(p.getInt(C_ICON_FILL, d.iconOnFill.toArgb())),
                btnTint = Color(p.getInt(C_BTN_TINT, d.btnTint.toArgb())),
                btnIcon = Color(p.getInt(C_BTN_ICON, d.btnIcon.toArgb())),
                silentFill = Color(p.getInt(C_SILENT_FILL, d.silentFill.toArgb())),
                silentIcon = Color(p.getInt(C_SILENT_ICON, d.silentIcon.toArgb())),
                rimColor = Color(p.getInt(C_RIM, d.rimColor.toArgb())),
                labelColor = Color(p.getInt(C_LABEL, d.labelColor.toArgb())),
                xos = p.getBoolean(KEY_XOS, false) && Build.VERSION.SDK_INT >= 31,
            )
        }
    }
}

/** المظهر الحالي: اللوحة والمعاينة يقرؤون منه (بدل ما نمرّره بكل دالة) */
val LocalGlassStyle = compositionLocalOf { GlassStyle.Default }

/** كل لون قابل للتعديل: عنوانه، مجموعته، وهل يدعم الشفافية */
enum class ColorSlot(
    val label: String,
    val group: Int,               // 0 = الشريط، 1 = الأزرار، 2 = عام
    val allowAlpha: Boolean,
    val get: (GlassStyle) -> Color,
    val set: (GlassStyle, Color) -> GlassStyle,
) {
    BarFill("لون الشريط الأبيض (التعبئة)", 0, true, { it.barFill }, { s, c -> s.copy(barFill = c) }),
    BarTint("لون زجاج الشريط", 0, false, { it.barTint }, { s, c -> s.copy(barTint = c) }),
    IconOnGlass("لون الأيقونات", 0, true, { it.iconOnGlass }, { s, c -> s.copy(iconOnGlass = c) }),
    IconOnFill("لون الأيقونات فوق التعبئة", 0, true, { it.iconOnFill }, { s, c -> s.copy(iconOnFill = c) }),

    BtnTint("لون زجاج الأزرار", 1, false, { it.btnTint }, { s, c -> s.copy(btnTint = c) }),
    BtnIcon("لون أيقونات الأزرار", 1, true, { it.btnIcon }, { s, c -> s.copy(btnIcon = c) }),
    SilentFill("لون زر الصامت", 1, false, { it.silentFill }, { s, c -> s.copy(silentFill = c) }),
    SilentIcon("لون أيقونة الصامت", 1, true, { it.silentIcon }, { s, c -> s.copy(silentIcon = c) }),

    Rim("لون الحافة اللامعة", 2, true, { it.rimColor }, { s, c -> s.copy(rimColor = c) }),
    Label("لون النص (الصوت)", 2, true, { it.labelColor }, { s, c -> s.copy(labelColor = c) }),
}
