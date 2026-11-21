package com.example.liquidtabs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port of HyperSpring's PushMorph.java: opening a sub-page like the HyperOS setup wizard.
 *
 *  1. What was there dissolves in place: it blurs, shrinks a little and fades out.
 *  2. The tapped row grows until it fills the screen, as if it became the new page; if it is
 *     not known what was tapped, a circle of light is born instead.
 *  3. The elements of the new page come out of that point, small and blurry, and slide to their
 *     place while they sharpen, one after another.
 */

/** Card color (the row that grows). */
private val SURFACE = Color(0xFF22222B)

class PushRequest(val title: String, val from: Rect?)

class PushController(private val scope: CoroutineScope) {
    var request by mutableStateOf<PushRequest?>(null)
        private set

    /** 0..1: the page below dissolves (blur 22dp, scale 0.96, alpha 0). 380 ms SETTLE. */
    val under = Animatable(0f)

    /** 0..1: the growing row / circle of light. 640 ms EXPO_OUT (700 ms SETTLE for the circle). */
    val bloom = Animatable(0f)

    /** 0..1: the page slides out to the right when going back (pop). */
    val pop = Animatable(0f)
    private var closing = false

    fun open(title: String, from: Rect?) {
        if (request != null) return
        request = PushRequest(title, from)
        scope.launch {
            pop.snapTo(0f)
            under.snapTo(0f)
            under.animateTo(1f, tween(380, easing = SETTLE))
        }
        scope.launch {
            bloom.snapTo(0f)
            bloom.animateTo(
                1f,
                tween(if (from != null) 640 else 700, easing = if (from != null) EXPO_OUT else SETTLE)
            )
        }
    }

    /** Back: the page slides out (320 ms) while the one below comes back. */
    fun close() {
        if (request == null || closing) return
        closing = true
        scope.launch {
            launch { bloom.snapTo(1f) }                   // an unfinished opening is completed
            coroutineScope {
                launch { under.animateTo(0f, tween(320, easing = SETTLE)) }
                launch { pop.animateTo(1f, tween(320, easing = SETTLE)) }
            }
            request = null
            pop.snapTo(0f)
            closing = false
        }
    }
}

val LocalPush = staticCompositionLocalOf<PushController?> { null }

/** Where everything comes out of (the center of what was tapped), in root coordinates. */
val LocalPushOrigin = compositionLocalOf { Offset.Zero }

@Composable
fun PushStack(
    controller: PushController,
    modifier: Modifier = Modifier,
    detail: @Composable (title: String) -> Unit,
    under: @Composable () -> Unit
) {
    BackHandler(enabled = controller.request != null) { controller.close() }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = controller.under.value
                    val k = 1f - 0.04f * t
                    scaleX = k
                    scaleY = k
                    alpha = 1f - t
                    renderEffect = blurEffect(22.dp.toPx() * t)
                }
        ) {
            CompositionLocalProvider(LocalPush provides controller) { under() }
        }
        controller.request?.let { r -> DetailLayer(r, controller) { detail(r.title) } }
    }
}

@Composable
private fun DetailLayer(r: PushRequest, c: PushController, content: @Composable () -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val origin = r.from?.center ?: Offset(size.width / 2f, size.height * 0.45f)
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .graphicsLayer {
                alpha = if (c.pop.value > 0f) 1f else minOf(1f, c.under.value * 1.4f)
                translationX = c.pop.value * size.width
            }
            .background(Brush.verticalGradient(listOf(Color(0xFF14141B), Color(0xFF0B0B10))))
            .pointerInput(Unit) { detectTapGestures { } }     // covers what is below
    ) {
        CompositionLocalProvider(LocalPushOrigin provides origin) { content() }

        // The row that grows (Grow) or the circle of light (Bloom), over the new page.
        Canvas(Modifier.fillMaxSize()) {
            val t = c.bloom.value
            if (t >= 1f) return@Canvas
            val dp = density
            val from = r.from
            if (from != null) {
                val a = 1f - seg(t, 0.3f, 0.85f)
                if (a <= 0f) return@Canvas
                val left = lerpF(from.left, 0f, t)
                val top = lerpF(from.top, 0f, t)
                val right = lerpF(from.right, this.size.width, t)
                val bottom = lerpF(from.bottom, this.size.height, t)
                val rad = CornerRadius(lerpF(18f, 34f, t) * dp)   // like the screen corners
                drawRoundRect(
                    SURFACE.copy(alpha = 235f / 255f * a),
                    topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = rad
                )
                drawRoundRect(
                    ACCENT.copy(alpha = 140f / 255f * a),
                    topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = rad,
                    style = Stroke(maxOf(1f, dp))
                )
            } else {
                val ox = this.size.width / 2f
                val oy = this.size.height * 0.45f
                val rr = (24f + 190f * t) * dp
                val fade = 1f - t
                val core = ACCENT.copy(alpha = 70f / 255f * fade)
                drawCircle(
                    Brush.radialGradient(
                        0.2f to core, 1f to core.copy(alpha = 0f),
                        center = Offset(ox, oy), radius = rr
                    ),
                    radius = rr, center = Offset(ox, oy)
                )
                drawCircle(
                    Color.White.copy(alpha = 90f / 255f * fade * fade),
                    radius = rr * 0.82f, center = Offset(ox, oy), style = Stroke(1.5f * dp)
                )
            }
        }
    }
}

/**
 * An element comes out of the circle: from small, blurry and shifted towards it, to sharp in
 * its place (PushMorph.emerge: 680 ms SETTLE, scale 0.84, blur 16dp).
 */
@Composable
fun Emerge(
    delayMs: Int,
    modifier: Modifier = Modifier,
    sharpen: Boolean = true,
    content: @Composable () -> Unit
) {
    val origin = LocalPushOrigin.current
    val a = remember { Animatable(0f) }
    var center by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(680, easing = SETTLE))
    }
    Box(
        modifier
            .onGloballyPositioned { if (center == Offset.Unspecified) center = it.boundsInRoot().center }
            .graphicsLayer {
                val t = a.value
                val c = if (center == Offset.Unspecified) origin else center
                val maxDy = 180.dp.toPx()
                val dx = (origin.x - c.x) * 0.25f
                val dy = ((origin.y - c.y) * 0.45f).coerceIn(-maxDy, maxDy)
                alpha = minOf(1f, t * 1.8f)
                translationX = dx * (1f - t)
                translationY = dy * (1f - t)
                val s = 0.84f + 0.16f * t
                scaleX = s
                scaleY = s
                renderEffect = if (sharpen) blurEffect(16.dp.toPx() * (1f - t)) else null
            }
    ) { content() }
}

/** A row inside a card: it appears sharpening, without moving (the card already moves). */
@Composable
fun FadeIn(delayMs: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(520, easing = SETTLE))
    }
    Box(
        modifier.graphicsLayer {
            alpha = a.value
            renderEffect = blurEffect(10.dp.toPx() * (1f - a.value))
        }
    ) { content() }
}
