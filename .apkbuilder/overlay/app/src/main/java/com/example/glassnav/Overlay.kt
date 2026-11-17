package com.example.glassnav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch

/**
 * iOS 26 style home bar.
 *  - swipe up            -> Home
 *  - swipe up and hold   -> Recents
 */
@Composable
fun PillBar(
    cover: Boolean,
    coverWhite: Boolean,
    onHome: () -> Unit,
    onRecents: () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }   // 0..1 while finger is down
    val lift = remember { Animatable(0f) }    // px, pill follows the finger upwards

    val coverColor = if (coverWhite) Color.White else Color.Black
    val pillColor = if (cover && coverWhite) Color(0xFF1C1C1E) else Color.White
    val bounce = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val threshold = 36.dp.toPx()
                val maxLift = 40.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    scope.launch { press.animateTo(1f, bounce) }
                    var crossedAt = 0L
                    var lastDy = 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull() ?: break
                        lastDy = c.position.y - down.position.y
                        if (lastDy < -threshold) {
                            if (crossedAt == 0L) crossedAt = System.currentTimeMillis()
                        } else crossedAt = 0L
                        val l = (lastDy * 0.45f).coerceIn(-maxLift, 0f)
                        scope.launch { lift.snapTo(l) }
                        c.consume()
                        if (!c.pressed) break
                    }
                    if (lastDy < -threshold) {
                        val held = crossedAt != 0L && System.currentTimeMillis() - crossedAt > 350
                        if (held) onRecents() else onHome()
                    }
                    scope.launch { press.animateTo(0f, bounce) }
                    scope.launch { lift.animateTo(0f, bounce) }
                }
            }
    ) {
        // Content that the glass pill "sees" through the backdrop
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .then(if (cover) Modifier.background(coverColor) else Modifier)
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 9.dp)
                .width(138.dp)
                .height(5.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(2.dp.toPx())
                        lens(2.dp.toPx(), 4.dp.toPx())
                    },
                    onDrawSurface = { drawRect(pillColor.copy(alpha = 0.95f)) },
                    layerBlock = {
                        val p = press.value
                        scaleX = 1f + 0.12f * p
                        scaleY = 1f + 0.8f * p
                        translationY = lift.value
                    }
                )
        )
    }
}

class EdgeState {
    var progress by mutableFloatStateOf(0f) // 0..1.2
}

/** The grey bulge with a chevron that appears at the screen edge for "Back". */
@Composable
fun EdgeBulge(state: EdgeState, left: Boolean) {
    val p by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "bulge"
    )
    Canvas(Modifier.fillMaxSize()) {
        val prog = p.coerceAtLeast(0f)
        val h = size.height
        val bw = prog * 62.dp.toPx()
        val top = h * 0.2f
        val bottom = h * 0.8f
        val mid = h / 2f

        withTransform({
            if (!left) scale(-1f, 1f, Offset(size.width / 2f, 0f))
        }) {
            val path = Path().apply {
                moveTo(0f, top)
                cubicTo(0f, top + (mid - top) * 0.55f, bw, top + (mid - top) * 0.45f, bw, mid)
                cubicTo(bw, mid + (bottom - mid) * 0.55f, 0f, mid + (bottom - mid) * 0.45f, 0f, bottom)
                close()
            }
            drawPath(
                path,
                Brush.verticalGradient(listOf(Color(0xE63A3A3C), Color(0xE61C1C1E)))
            )
            drawPath(path, Color.White.copy(alpha = 0.18f), style = Stroke(1.dp.toPx()))

            val a = prog.coerceIn(0f, 1f)
            val s = 7.dp.toPx() * a
            val cx = bw * 0.5f
            val w = 2.5.dp.toPx()
            drawLine(Color.White.copy(alpha = a), Offset(cx - s / 2, mid - s), Offset(cx + s / 2, mid), w, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = a), Offset(cx + s / 2, mid), Offset(cx - s / 2, mid + s), w, StrokeCap.Round)
        }
    }
}
