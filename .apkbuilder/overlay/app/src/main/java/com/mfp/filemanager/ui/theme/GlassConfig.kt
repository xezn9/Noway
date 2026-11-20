package com.mfp.filemanager.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.mfp.filemanager.data.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * One place that drives the look of EVERY liquid-glass part of the app (tabs, search bar,
 * page titles, dialogs, folder icons, buttons, switches, settings preview).
 *
 * All values are Compose snapshot state, and the glass code reads them inside `drawBackdrop`
 * (effects / onDrawSurface). So moving a slider in Settings redraws the glass live, with no
 * recomposition and no restart.
 *
 * Sliders are stored as 0f..1f where 0.5f is exactly the original look of the app:
 *  - blur         0 = no blur ........ 0.5 = original ........ 1 = 4x blur
 *  - transparency 0 = almost solid ... 0.5 = original ........ 1 = fully clear glass
 *  - refraction   0 = flat glass ..... 0.5 = original ........ 1 = 3x bending of the background
 */
object GlassConfig {

    var accentArgb: Int by mutableIntStateOf(AccentPalette.default.argb)

    var blur: Float by mutableFloatStateOf(0.5f)
    var transparency: Float by mutableFloatStateOf(0.5f)
    var refraction: Float by mutableFloatStateOf(0.5f)

    val accent: Color get() = Color(accentArgb)

    /** Multiplier for every blur radius (reads state, so call it inside a draw/effects block). */
    fun blurScale(): Float {
        val v = blur
        return if (v <= 0.5f) v * 2f else 1f + (v - 0.5f) * 6f
    }

    /** Multiplier for every lens refraction (height and amount). */
    fun refractionScale(): Float {
        val v = refraction
        return if (v <= 0.5f) v * 2f else 1f + (v - 0.5f) * 4f
    }

    /** Multiplier for the alpha of the glass tint / surface colour. */
    fun surfaceScale(): Float {
        val v = transparency
        return if (v <= 0.5f) 1f + (0.5f - v) * 2f else (1f - v) * 2f
    }

    /** Blur radius in dp for a glass part whose original radius is [baseDp]. 0 => skip blur(). */
    fun blurDp(baseDp: Float): Float = baseDp * blurScale()

    /** Refraction height / amount in dp. 0 => skip lens() (a zero lens is not valid). */
    fun lensDp(baseDp: Float, maxScale: Float = Float.MAX_VALUE): Float =
        baseDp * refractionScale().coerceAtMost(maxScale)

    /** Applies the transparency slider to a glass tint colour. */
    fun surface(color: Color, minAlpha: Float = 0f): Color =
        color.copy(alpha = (color.alpha * surfaceScale()).coerceIn(minOf(minAlpha, color.alpha), maxOf(0.92f, color.alpha)))

    /** Reads the saved values (call once, early, before any UI is created). */
    suspend fun load(repo: SettingsRepository) {
        accentArgb = AccentPalette.find(repo.accentColor.first()).argb
        blur = repo.glassBlur.first().coerceIn(0f, 1f)
        transparency = repo.glassTransparency.first().coerceIn(0f, 1f)
        refraction = repo.glassRefraction.first().coerceIn(0f, 1f)
    }

    fun resetGlass() {
        blur = 0.5f
        transparency = 0.5f
        refraction = 0.5f
    }
}
