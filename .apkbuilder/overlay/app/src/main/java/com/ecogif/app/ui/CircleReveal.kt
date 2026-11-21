package com.ecogif.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A circle centred on a tab of the bottom bar; [progress] 0..1 grows it until it covers the screen. */
private class CircleRevealShape(
    val originX: Float,
    val bottomPx: Float,
    val progress: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = Offset(originX * size.width, size.height - bottomPx)
        val r = maxRadius(c, size) * progress
        return Outline.Generic(Path().apply { addOval(Rect(center = c, radius = r)) })
    }
}

/** The opposite of [CircleRevealShape]: the whole screen EXCEPT the circle (where the old page must be hidden). */
private class OutsideCircleShape(
    val originX: Float,
    val bottomPx: Float,
    val progress: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = Offset(originX * size.width, size.height - bottomPx)
        val r = maxRadius(c, size) * progress
        val screen = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        val hole = Path().apply { addOval(Rect(center = c, radius = r)) }
        return Outline.Generic(Path.combine(PathOperation.Difference, screen, hole))
    }
}

private fun maxRadius(c: Offset, size: Size): Float =
    listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
        .maxOf { (it - c).getDistance() } * 1.03f

/**
 * Page transition: the new page is revealed inside a circle that is born at ITS tab in the bottom bar
 * and grows to fill the screen, with a glowing rim, trailing ripples and sparkles on the edge.
 * The old page recedes underneath. Nothing recomposes per frame: everything reads [reveal] while drawing.
 */
@Composable
fun RevealPages(
    shown: Int,
    prev: Int,
    reveal: Animatable<Float, AnimationVector1D>,
    originX: Float,
    bottomPx: Float,
    accent: Color,
    accent2: Color,
    content: @Composable (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (prev >= 0) {
            // the old page only stays visible OUTSIDE the growing circle, so it never shows through the new page
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = reveal.value
                        clip = true
                        shape = OutsideCircleShape(originX, bottomPx, p)
                        alpha = 1f - 0.5f * p
                    }
            ) { content(prev) }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (prev >= 0) {
                        val p = reveal.value
                        clip = true
                        shape = CircleRevealShape(originX, bottomPx, p)
                    }
                }
        ) { content(shown) }

        if (prev >= 0) {
            Canvas(Modifier.fillMaxSize()) {
                val p = reveal.value
                if (p <= 0f || p >= 1f) return@Canvas
                val center = Offset(originX * size.width, size.height - bottomPx)
                val maxR = maxRadius(center, size)
                val r = maxR * p
                val fade = (1f - p * p).coerceIn(0f, 1f)

                // soft coloured halo just outside the front
                val halo = 80.dp.toPx()
                val big = r + halo
                val s2 = (r / big).coerceIn(0f, 0.999f)
                val s1 = ((r - halo * 0.7f) / big).coerceIn(0f, s2)
                drawRect(
                    Brush.radialGradient(
                        0f to Color.Transparent,
                        s1 to Color.Transparent,
                        s2 to accent.copy(alpha = 0.55f * fade),
                        1f to Color.Transparent,
                        center = center,
                        radius = big
                    )
                )

                // glowing rim: wide aura + bright thin core
                drawCircle(accent2.copy(alpha = 0.28f * fade), r, center, style = Stroke(24.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.34f * fade), r, center, style = Stroke(10.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.95f * fade), r, center, style = Stroke(3.dp.toPx()))

                // ripples trailing behind the front, over the new page
                for (i in 1..3) {
                    val rp = (p - i * 0.09f).coerceIn(0f, 1f)
                    if (rp > 0f) {
                        drawCircle(
                            Color.White.copy(alpha = 0.30f * (1f - rp) * fade),
                            maxR * rp, center, style = Stroke(2.dp.toPx())
                        )
                    }
                }

                // sparkles riding the edge
                val n = 34
                for (i in 0 until n) {
                    val a = i * (2f * PI.toFloat() / n) + p * 3.2f
                    val rr = r + sin(i * 7.3f + p * 14f) * 18.dp.toPx()
                    val pos = Offset(center.x + cos(a) * rr, center.y + sin(a) * rr)
                    val twinkle = abs(sin(i * 3.1f + p * 30f))
                    val dot = (1.5f + (i % 3)) * density
                    drawCircle(Color.White.copy(alpha = 0.9f * fade * twinkle), dot, pos)
                    drawCircle(accent2.copy(alpha = 0.35f * fade * twinkle), dot * 2.6f, pos)
                }
            }
        }
    }
}
