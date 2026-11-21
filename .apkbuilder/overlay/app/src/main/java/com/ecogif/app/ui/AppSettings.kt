package com.ecogif.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.catalog.utils.GlassLevels

/** User settings (saved on the phone): colour of every page + the liquid-glass levels. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ecogif_settings", Context.MODE_PRIVATE)

    val glass = GlassLevels(
        blur = prefs.getFloat("glass_blur", 50f),
        refraction = prefs.getFloat("glass_refraction", 50f),
        transparency = prefs.getFloat("glass_transparency", 50f)
    )

    // index into [Swatches] for the three pages (Resize, To GIF, To Video)
    private val colorIdx = mutableStateListOf(
        prefs.getInt("color_0", 0).coerceIn(0, Swatches.lastIndex),
        prefs.getInt("color_1", 1).coerceIn(0, Swatches.lastIndex),
        prefs.getInt("color_2", 2).coerceIn(0, Swatches.lastIndex)
    )

    /** App language: true = Arabic (RTL), false = English. Defaults to the phone's language. */
    var arabic by mutableStateOf(
        prefs.getBoolean("lang_ar", java.util.Locale.getDefault().language == "ar")
    )
        private set

    /** Show the animated splash when the app opens. */
    var showSplash by mutableStateOf(prefs.getBoolean("show_splash", true))
        private set

    init {
        I18n.arabic = arabic
    }

    fun changeLanguage(v: Boolean) {
        arabic = v
        I18n.arabic = v
        prefs.edit().putBoolean("lang_ar", v).apply()
    }

    fun changeSplash(v: Boolean) {
        showSplash = v
        prefs.edit().putBoolean("show_splash", v).apply()
    }

    /** Which page the colour picker is editing (the Settings page previews that page's colour). */
    var editTab by mutableIntStateOf(0)

    fun colorIndex(page: Int): Int = colorIdx[page.coerceIn(0, 2)]

    /** Colour of a page; the Settings page (3) shows the page that is being edited. */
    fun pageColor(tab: Int): Color =
        Swatches[colorIdx[if (tab in 0..2) tab else editTab]].color

    fun setColor(page: Int, index: Int) {
        colorIdx[page] = index
        prefs.edit().putInt("color_$page", index).apply()
    }

    fun setBlur(v: Float) {
        glass.blurPct = v
        prefs.edit().putFloat("glass_blur", v).apply()
    }

    fun setRefraction(v: Float) {
        glass.refractionPct = v
        prefs.edit().putFloat("glass_refraction", v).apply()
    }

    fun setTransparency(v: Float) {
        glass.transparencyPct = v
        prefs.edit().putFloat("glass_transparency", v).apply()
    }

    fun resetGlass() {
        setBlur(50f)
        setRefraction(50f)
        setTransparency(50f)
    }
}
