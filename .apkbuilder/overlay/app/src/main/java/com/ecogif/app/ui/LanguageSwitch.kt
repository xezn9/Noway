package com.ecogif.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Language-switch animation: a liquid wave grows from the tapped button until it covers the screen,
 * the language flips behind it (a big letter spins in), then a circular hole opens from the same
 * point and reveals the page in the new language.
 */
@Stable
class LangSwitchState {
    val cover = Animatable(0f)
    val label = Animatable(0f)

    /** 0 = idle, 1 = wave growing, 2 = revealing the new language. */
    var mode by mutableIntStateOf(0)
    var origin by mutableStateOf(Offset.Zero)
    var toArabic by mutableStateOf(false)

    suspend fun run(arabic: Boolean, from: Offset, apply: () -> Unit) {
        if (mode != 0) return
        origin = from
        toArabic = arabic
        try {
            mode = 1
            cover.snapTo(0f)
            label.snapTo(0f)
            cover.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            apply() // the texts change here, hidden behind the wave
            coroutineScope {
                launch { label.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 130f)) }
                delay(850)
            }
            mode = 2
            cover.snapTo(0f)
            cover.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        } finally {
            mode = 0
            cover.snapTo(0f)
        }
    }
}

@Composable
fun LanguageSwitchOverlay(s: LangSwitchState, accent: Color, accent2: Color) {
    if (s.mode == 0) return
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val origin = s.origin
            val corners = listOf(
                Offset(0f, 0f), Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)
            )
            val maxR = corners.maxOf { (it - origin).getDistance() } * 1.02f
            val p = s.cover.value
            val r = maxR * p
            val brush = Brush.linearGradient(
                listOf(accent, accent2, accent),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            if (s.mode == 1) {
                drawCircle(brush, r, origin)
                // soft ripples trailing behind the wave front
                for (i in 1..3) {
                    val rp = (p - i * 0.13f).coerceIn(0f, 1f)
                    if (rp > 0f) {
                        drawCircle(Color.White.copy(alpha = 0.32f * (1f - rp)), maxR * rp, origin, style = Stroke(3.dp.toPx()))
                    }
                }
                drawCircle(Color.White.copy(alpha = 0.6f), r, origin, style = Stroke(5.dp.toPx()))
            } else {
                val hole = Path().apply { addOval(Rect(center = origin, radius = r)) }
                clipPath(hole, clipOp = ClipOp.Difference) { drawRect(brush) }
                drawCircle(Color.White.copy(alpha = 0.6f * (1f - p)), r, origin, style = Stroke(5.dp.toPx()))
            }
        }

        // big letter of the new language spins in, then fades while the page is revealed
        val fade = if (s.mode == 2) (1f - s.cover.value * 1.7f).coerceIn(0f, 1f) else 1f
        val l = s.label.value
        if (s.mode == 2 || l > 0f) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = fade * l.coerceIn(0f, 1f)
                        val sc = 0.45f + 0.55f * l
                        scaleX = sc
                        scaleY = sc
                        rotationY = (1f - l) * -100f
                        cameraDistance = 14f * density
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                androidx.compose.material3.Text(
                    if (s.toArabic) "ع" else "A",
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold
                )
                androidx.compose.material3.Text(
                    if (s.toArabic) "العربية" else "English",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
