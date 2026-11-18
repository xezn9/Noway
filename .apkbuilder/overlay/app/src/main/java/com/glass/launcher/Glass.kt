package com.glass.launcher

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** White tint strength of glass surfaces (set from Settings). */
val LocalGlassTint = compositionLocalOf { 0.16f }

/** Liquid glass surface (Backdrop 2.0.0). All glass styling lives here: if the API differs, fix this one file. */
@Composable
fun Modifier.glass(backdrop: Backdrop, radius: Dp = 28.dp): Modifier {
    val tint = LocalGlassTint.current
    return drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedCornerShape(radius) },
        effects = {
            vibrancy()
            blur(4.dp.toPx())
            lens(16.dp.toPx(), 32.dp.toPx())
        },
        onDrawSurface = { drawRect(Color.White.copy(alpha = tint)) }
    )
}
