package com.example.liquidtabs

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

/**
 * Port of HyperSpring's TabReveal.java: tab change with a liquid-glass wave.
 *
 * The new page is born as a circle from the tapped tab button and grows; its edge is like the
 * rim of thick glass (it bends the image and shines). Ahead of the wave the old page is pushed
 * outwards, like displaced water, and at the same time it sinks (shrinks, fades, blurs).
 *
 * The shaders below are the original AGSL sources (Android 13+); before that only the circle
 * and the ring of light are drawn.
 */

/** Glass rim of the new page (TabReveal.RIM_SHADER). */
private const val RIM_SHADER = """
uniform shader content;
uniform float2 center;
uniform float radius;
uniform float width;
uniform float amount;
half4 main(float2 p) {
  float2 d = p - center;
  float dist = length(d);
  float e = radius - dist;
  if (e < 0.0 || e > width) return content.eval(p);
  float2 n = d / max(dist, 1.0);
  float k = 1.0 - e / width;
  half4 c = content.eval(p - n * amount * k * k);
  float shine = 0.22 * k * k * k;
  c.rgb = min(c.rgb + half3(shine) * c.a, c.aaa);
  return c;
}
"""

/** Push of the old page just ahead of the wave (TabReveal.PUSH_SHADER). */
private const val PUSH_SHADER = """
uniform shader content;
uniform float2 center;
uniform float radius;
uniform float width;
uniform float amount;
half4 main(float2 p) {
  float2 d = p - center;
  float dist = length(d);
  float x = (dist - radius) / width;
  if (x < -1.0 || x > 5.0) return content.eval(p);
  float2 n = d / max(dist, 1.0);
  float push = amount * exp(-0.9 * max(x, 0.0));
  return content.eval(p - n * push);
}
"""

private class CircleAt(val c: Offset, val r: Float) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(c, r)) })
}

private fun shaderOrNull(src: String): RuntimeShader? =
    if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(src) }.getOrNull() else null

/**
 * Shows page [selectedTab]. When it changes, the wave plays from [tabCenter] (the center of the
 * tapped tab button, in this host's coordinates).
 *
 * @param page content of a tab; [revealBase] is the delay (ms) at which its cascade should start
 */
@Composable
fun TabRevealHost(
    selectedTab: Int,
    tabCenter: (Int) -> Offset?,
    modifier: Modifier = Modifier,
    page: @Composable (index: Int, revealBase: Int) -> Unit
) {
    var current by remember { mutableIntStateOf(selectedTab) }
    var old by remember { mutableStateOf<Int?>(null) }
    var switched by remember { mutableStateOf(false) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var center by remember { mutableStateOf(Offset.Zero) }
    val progress = remember { Animatable(0f) }
    val rim = remember { shaderOrNull(RIM_SHADER) }
    val push = remember { shaderOrNull(PUSH_SHADER) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == current) return@LaunchedEffect
        val c = tabCenter(selectedTab)
        progress.snapTo(0f)
        switched = true
        if (c == null || hostSize == IntSize.Zero) {          // no wave: plain switch
            old = null
            current = selectedTab
            return@LaunchedEffect
        }
        center = c
        old = current                                      // an unfinished change ends right here
        current = selectedTab
        progress.animateTo(1f, tween(900, easing = WAVE))  // WAVE, 900 ms
        old = null
    }

    Box(modifier.onSizeChanged { hostSize = it }) {
        val animating = old != null
        // New page last so it is drawn on top; keys keep each page's state when the order changes.
        for (idx in listOfNotNull(old, current)) {
            key(idx) {
                val isNew = idx == current
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (!animating) return@graphicsLayer
                            val t = progress.value
                            val cx = center.x
                            val cy = center.y
                            val w = hostSize.width.toFloat().coerceAtLeast(1f)
                            val h = hostSize.height.toFloat().coerceAtLeast(1f)
                            val maxR = hypot(max(cx, w - cx), max(cy, h - cy)) + 40.dp.toPx()
                            val startR = 26.dp.toPx()
                            val r = startR + (maxR - startR) * t
                            val force = 1f - t * t * t   // full for most of the way, calms at the end
                            transformOrigin = TransformOrigin(cx / w, cy / h)

                            if (isNew) {
                                // The new page only shows inside the circle.
                                val s = 1.04f - 0.04f * t           // it arrives a bit close
                                scaleX = s
                                scaleY = s
                                clip = true
                                shape = CircleAt(Offset(cx, cy), r)
                                if (Build.VERSION.SDK_INT >= 33 && rim != null) {
                                    rim.setFloatUniform("center", cx, cy)
                                    rim.setFloatUniform("width", 34.dp.toPx())
                                    rim.setFloatUniform("radius", r)
                                    rim.setFloatUniform("amount", 26.dp.toPx() * force)
                                    renderEffect = android.graphics.RenderEffect
                                        .createRuntimeShaderEffect(rim, "content")
                                        .asComposeRenderEffect()
                                }
                            } else {
                                val k = 1f - 0.08f * t
                                scaleX = k
                                scaleY = k
                                alpha = 1f - 0.55f * t
                                if (Build.VERSION.SDK_INT >= 31) {
                                    var e: android.graphics.RenderEffect? = null
                                    val b = 10.dp.toPx() * t
                                    if (b >= 0.5f) {
                                        e = android.graphics.RenderEffect.createBlurEffect(
                                            b, b, android.graphics.Shader.TileMode.CLAMP
                                        )
                                    }
                                    if (Build.VERSION.SDK_INT >= 33 && push != null) {
                                        // Wave radius in the old page's coordinates (which shrinks).
                                        push.setFloatUniform("center", cx, cy)
                                        push.setFloatUniform("width", 40.dp.toPx())
                                        push.setFloatUniform("radius", r / k)
                                        push.setFloatUniform("amount", 48.dp.toPx() * force)
                                        val pe = android.graphics.RenderEffect
                                            .createRuntimeShaderEffect(push, "content")
                                        // push first, then blur
                                        e = if (e == null) pe
                                        else android.graphics.RenderEffect.createChainEffect(e, pe)
                                    }
                                    renderEffect = e?.asComposeRenderEffect()
                                }
                            }
                        }
                ) {
                    page(idx, if (!switched) 200 else 90)
                }
            }
        }

        // The ring of light on the wave front: a wide soft halo and a thin line on top.
        if (animating) {
            Canvas(Modifier.fillMaxSize()) {
                val t = progress.value
                if (t >= 1f) return@Canvas
                val w = size.width
                val h = size.height
                val maxR = hypot(max(center.x, w - center.x), max(center.y, h - center.y)) + 40.dp.toPx()
                val startR = 26.dp.toPx()
                val r = startR + (maxR - startR) * t
                val fade = 1f - t
                rotate(180f * t, pivot = center) {
                    drawCircle(
                        ACCENT.copy(alpha = 55f / 255f * fade), radius = r, center = center,
                        style = Stroke(16.dp.toPx())
                    )
                    drawCircle(
                        ACCENT.copy(alpha = 200f / 255f * fade), radius = r, center = center,
                        style = Stroke(2.dp.toPx())
                    )
                }
            }
        }
    }
}
