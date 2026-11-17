package com.example.glassnav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch
import kotlin.math.exp

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

/** Live state of one edge gesture, written by the touch strip, read by the glass bulge. */
class EdgeState {
    var active by mutableStateOf(false)   // finger is down
    var dx by mutableFloatStateOf(0f)     // px dragged toward screen centre
    var y by mutableFloatStateOf(0f)      // finger Y in screen px
    var armed by mutableStateOf(false)    // dragged far enough -> Back will fire
    var downCount by mutableIntStateOf(0) // increments on every new touch
}

/** Max distance the bulge may reach into the screen (it never goes to the middle). */
val BulgeMaxW = 56.dp
/** Half of the bulge height. */
val BulgeHalfH = 78.dp

/**
 * Glass bulge drawn at the left edge (x = 0). Mirror the DrawScope for the right edge.
 * [light] = white glass, otherwise dark glass.
 */
fun DrawScope.drawGlassBulge(light: Boolean, bw: Float, cy: Float, halfH: Float, chevron: Float, chevronScale: Float) {
    if (bw < 0.5f) return
    val top = cy - halfH
    val bottom = cy + halfH
    val path = Path().apply {
        moveTo(0f, top)
        cubicTo(0f, top + halfH * 0.55f, bw, top + halfH * 0.45f, bw, cy)
        cubicTo(bw, cy + halfH * 0.55f, 0f, cy + halfH * 0.45f, 0f, bottom)
        close()
    }
    val white = Color.White

    // soft shadow
    drawPath(path, Color.Black.copy(alpha = if (light) 0.10f else 0.28f), style = Stroke(4.dp.toPx()))

    // glass body
    drawPath(
        path,
        Brush.horizontalGradient(
            colors = if (light) listOf(Color(0xD9FFFFFF), Color(0x99ECECF2))
                     else listOf(Color(0x995A5A5E), Color(0xCC161618)),
            startX = 0f, endX = bw.coerceAtLeast(1f)
        )
    )
    // sheen
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(white.copy(alpha = 0.28f), Color.Transparent, white.copy(alpha = 0.12f)),
            startY = top, endY = bottom
        )
    )
    // rim light
    val rimA = if (light) 0.95f else 0.55f
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(white.copy(alpha = rimA), white.copy(alpha = rimA * 0.25f), white.copy(alpha = rimA * 0.8f)),
            startY = top, endY = bottom
        ),
        style = Stroke(1.3.dp.toPx())
    )

    // chevron, pointing toward the screen centre
    val a = chevron.coerceIn(0f, 1f)
    if (a > 0.01f) {
        val col = (if (light) Color(0xFF1C1C1E) else white).copy(alpha = a)
        val s = 7.dp.toPx() * chevronScale
        val cx = bw * 0.5f
        val w = 2.5.dp.toPx()
        drawLine(col, Offset(cx - s / 2, cy - s), Offset(cx + s / 2, cy), w, StrokeCap.Round)
        drawLine(col, Offset(cx + s / 2, cy), Offset(cx - s / 2, cy + s), w, StrokeCap.Round)
    }
}

/**
 * Back-gesture bulge. Follows the finger anywhere along the edge, is capped at [BulgeMaxW],
 * slides in with a spring, and slides back with a bouncy spring on release.
 */
@Composable
fun EdgeBulge(state: EdgeState, left: Boolean, light: Boolean) {
    val density = LocalDensity.current
    val maxW = with(density) { BulgeMaxW.toPx() }
    val halfH = with(density) { BulgeHalfH.toPx() }
    val reach = with(density) { 64.dp.toPx() }

    val width = remember { Animatable(0f) }
    val yPos = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }

    // Rubber-band: grows fast at first, asymptotically stops at maxW
    val target = if (state.active) maxW * (1f - exp(-state.dx.coerceAtLeast(0f) / reach)) else 0f

    LaunchedEffect(target, state.active) {
        if (state.active) width.animateTo(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
        else width.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessLow)) // slide back + bounce
    }
    LaunchedEffect(state.downCount) { yPos.snapTo(state.y) }
    LaunchedEffect(state.y) {
        yPos.animateTo(state.y, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(state.armed) {
        if (state.armed) {
            pop.animateTo(1.35f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessHigh))
        } else {
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val bw = width.value.coerceAtLeast(0f)
        val cy = yPos.value.coerceIn(halfH, (size.height - halfH).coerceAtLeast(halfH))
        withTransform({
            if (!left) scale(-1f, 1f, Offset(size.width / 2f, 0f))
        }) {
            drawGlassBulge(light, bw, cy, halfH, chevron = (bw / maxW) * 1.4f, chevronScale = pop.value)
        }
    }
}
