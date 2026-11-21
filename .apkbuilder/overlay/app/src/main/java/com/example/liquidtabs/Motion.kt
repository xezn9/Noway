package com.example.liquidtabs

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Constants copied from HyperSpring's Motion.java / PushMorph.java / TabReveal.java

/** Accent color (Motion.ACCENT). */
val ACCENT = Color(0xFFFF5F45)

/** PathInterpolator(0.2, 0, 0, 1): "SETTLE" in PushMorph, "EASE" in Motion. */
val SETTLE = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Motion.EXPO_OUT */
val EXPO_OUT = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Motion.POWER4_OUT */
val POWER4_OUT = CubicBezierEasing(0.165f, 0.84f, 0.44f, 1f)

/** TabReveal.WAVE */
val WAVE = CubicBezierEasing(0.4f, 0.1f, 0.15f, 1f)

fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Blur of a layer (Android 12+). Below half a pixel it is removed (same rule as PushMorph.blur). */
fun blurEffect(r: Float, mode: TileMode = TileMode.Decal): RenderEffect? =
    if (Build.VERSION.SDK_INT < 31 || r < 0.5f) null else BlurEffect(r, r, mode)

/**
 * Motion.cascade: each child appears a little after the previous one.
 * Cards with [rows] rows reveal their rows right behind them.
 */
class Cascade(private val base: Int) {
    private var d = base

    /** Delay (ms) at which the next element should start. */
    fun take(rows: Int = 0): Int {
        val mine = d
        d += minOf(rows, 8) * 30
        d += 55
        if (d - base > 700) d = base + 700   // on long pages it does not drag on
        return mine
    }
}

/** Motion.reveal: fade in while rising a little (760 ms, power4.out). */
@Composable
fun Reveal(
    delayMs: Int,
    modifier: Modifier = Modifier,
    dy: Dp = 22.dp,
    content: @Composable () -> Unit
) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(760, easing = POWER4_OUT))
    }
    Box(
        modifier.graphicsLayer {
            alpha = a.value
            translationY = (1f - a.value) * dy.toPx()
        }
    ) { content() }
}
