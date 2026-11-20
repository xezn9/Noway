package com.mfp.filemanager.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * Folder icon made of real Backdrop liquid glass (same drawBackdrop / lens / vibrancy / highlight
 * as the bottom tabs): a glass tab, a glass back panel and a lighter glass front flap.
 *
 * Every icon owns a tiny hidden "scene" (soft colours) that the glass refracts, so it keeps its
 * liquid look even on a flat list background and never needs the screen backdrop
 * (a glass element must not sit inside the layer it samples).
 *
 * Only rounded-rect shapes are used because the lens effect needs rounded-corner shapes.
 */
@Composable
fun LiquidFolderIcon(
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    tint: Color = Color(0xFF4DA3FF)
) {
    val scene = rememberLayerBackdrop()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val s: Dp = if (maxWidth < maxHeight) maxWidth else maxHeight

        // --- hidden scene the glass looks at ---
        Box(
            Modifier
                .size(s)
                .alpha(0f)
                .layerBackdrop(scene)
                .drawBehind {
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color(0xFFB8F0FF), tint, Color(0xFF2347C9)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    )
                    drawCircle(
                        Color.White.copy(0.65f),
                        radius = size.minDimension * 0.34f,
                        center = Offset(size.width * 0.22f, size.height * 0.20f)
                    )
                    drawCircle(
                        Color(0xFFB27BFF).copy(0.55f),
                        radius = size.minDimension * 0.30f,
                        center = Offset(size.width * 0.86f, size.height * 0.92f)
                    )
                }
        )

        val backColor = tint.copy(if (dark) 0.40f else 0.50f)
        val frontColor = Color.White.copy(if (dark) 0.16f else 0.30f)
            .compositeOver(tint.copy(0.20f))

        // tab (top-left)
        GlassPiece(scene, s, 0.09f, 0.13f, 0.40f, 0.26f, 0.10f, backColor)
        // back panel
        GlassPiece(scene, s, 0.09f, 0.24f, 0.82f, 0.62f, 0.15f, backColor)
        // front flap (lighter, makes the folder look folded)
        GlassPiece(scene, s, 0.09f, 0.38f, 0.82f, 0.48f, 0.15f, frontColor)
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha + background.alpha * (1f - alpha)
    if (a <= 0f) return Color.Transparent
    fun mix(f: Float, b: Float) = (f * alpha + b * background.alpha * (1f - alpha)) / a
    return Color(mix(red, background.red), mix(green, background.green), mix(blue, background.blue), a)
}

@Composable
private fun GlassPiece(
    backdrop: Backdrop,
    s: Dp,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    corner: Float,
    surface: Color
) {
    val radius = s * corner
    Box(
        Modifier
            .offset(s * left, s * top)
            .size(s * width, s * height)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(radius) },
                effects = {
                    vibrancy()
                    blur(1f.dp.toPx())
                    lens(4f.dp.toPx(), 9f.dp.toPx())
                },
                onDrawSurface = { drawRect(surface) }
            )
    )
}
