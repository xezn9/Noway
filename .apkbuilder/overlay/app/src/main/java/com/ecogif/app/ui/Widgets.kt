package com.ecogif.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.utils.LocalGlassLevels
import com.kyant.backdrop.catalog.utils.scaleAlpha
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.delay

/** Cards float in one after the other (delay grows with [index]) every time a screen appears. */
@Composable
fun Staggered(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 170f))
    }
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 70.dp.toPx()
            val sc = 0.93f + 0.07f * p
            scaleX = sc
            scaleY = sc
        }
    ) { content() }
}

/** Small glass tile (used in rows of 2-3 numbers). */
@Composable
fun GlassTile(
    backdrop: Backdrop,
    colors: EcoColors,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlassLevels.current
    Column(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(22.dp) },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx() * glass.blurK)
                    lens(8f.dp.toPx() * glass.lensK, 16f.dp.toPx() * glass.lensK)
                },
                onDrawSurface = { drawRect(colors.card.scaleAlpha(glass.opacityK)) }
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
fun StatTile(
    value: String,
    label: String,
    backdrop: Backdrop,
    colors: EcoColors,
    modifier: Modifier = Modifier,
) {
    GlassTile(backdrop, colors, modifier) {
        Text(value, color = LocalAccent.current, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = colors.secondary, fontSize = 12.sp, maxLines = 1)
    }
}

/** Shows which part of the video becomes the GIF. */
@Composable
fun TimelineBar(
    startSec: Float,
    lengthSec: Float,
    durationSec: Float,
    colors: EcoColors,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.fillMaxWidth().height(34.dp)) {
        val w = size.width
        val h = size.height
        val total = durationSec.coerceAtLeast(0.1f)
        var a = (startSec / total).coerceIn(0f, 1f)
        var b = ((startSec + lengthSec) / total).coerceIn(a, 1f)
        if (rtl) {
            val na = 1f - b
            val nb = 1f - a
            a = na
            b = nb
        }
        val radius = CornerRadius(h / 2f)
        drawRoundRect(colors.field, size = size, cornerRadius = radius)
        val left = w * a
        val right = maxOf(w * b, left + h).coerceAtMost(w)
        val l2 = (right - h).coerceAtLeast(0f).coerceAtMost(left)
        drawRoundRect(
            Brush.horizontalGradient(listOf(accent, accent.copy(0.65f)), startX = l2, endX = right),
            topLeft = Offset(l2, 0f),
            size = Size(right - l2, h),
            cornerRadius = radius
        )
        val n = 28
        for (i in 1 until n) {
            val x = w * i / n
            drawLine(colors.content.copy(0.22f), Offset(x, h * 0.32f), Offset(x, h * 0.68f), 1.dp.toPx())
        }
    }
}

/** - 3x +  counter made of two glass buttons. */
@Composable
fun Stepper(
    text: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    backdrop: Backdrop,
    colors: EcoColors,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LiquidButton(
            onClick = onMinus,
            backdrop = backdrop,
            modifier = Modifier.width(84.dp),
            surfaceColor = if (colors.dark) Color.White.copy(0.10f) else Color.White.copy(0.35f)
        ) {
            Text("−", color = colors.content, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
        Text(text, color = colors.content, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        LiquidButton(
            onClick = onPlus,
            backdrop = backdrop,
            modifier = Modifier.width(84.dp),
            tint = LocalAccent.current
        ) {
            Text("+", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
    }
}
