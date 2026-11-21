package com.kyant.backdrop.catalog.utils

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Live "liquid glass" controls (all in percent, 50 = the original look).
 * The values are snapshot state, so every glass surface redraws while a slider moves.
 */
@Stable
class GlassLevels(blur: Float = 50f, refraction: Float = 50f, transparency: Float = 50f) {
    var blurPct by mutableFloatStateOf(blur)
    var refractionPct by mutableFloatStateOf(refraction)
    var transparencyPct by mutableFloatStateOf(transparency)

    /** Multiplier for every blur radius. */
    val blurK: Float get() = (blurPct / 50f).coerceAtLeast(0.05f)

    /** Multiplier for every lens (refraction) height / amount. */
    val lensK: Float get() = (refractionPct / 50f).coerceAtLeast(0.05f)

    /** Multiplier for the opacity of every glass surface (0 = clear glass). */
    val opacityK: Float get() = ((100f - transparencyPct) / 50f).coerceIn(0f, 2f)
}

val LocalGlassLevels = compositionLocalOf { GlassLevels() }

fun Color.scaleAlpha(k: Float): Color = copy(alpha = (alpha * k).coerceIn(0f, 1f))
