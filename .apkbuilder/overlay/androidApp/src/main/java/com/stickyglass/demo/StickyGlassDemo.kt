package com.stickyglass.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.StickyRoundRect
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.stickyLens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.abs
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Tuning (all in dp)
// ---------------------------------------------------------------------------------------------
private const val BarHalfHeightDp = 32f
private const val BarHalfWidthDp = 170f
private const val CircleRadiusDp = 32f
private const val RegionHeightDp = 300f

private const val StickinessDp = 48f          // metaball blend distance: bigger = stickier
private const val RefractionHeightDp = 26f
private const val RefractionAmountDp = 52f
private const val DispersionAmount = 0.25f    // chromatic aberration
private const val SpringStiffness = 140f
private const val SpringDamping = 8f

private const val RestCx = -110f              // bar rest pose when merged with the circle

// ---------------------------------------------------------------------------------------------
// Choreography copied from the reference video (one loop = 7.8 s)
// pose = bar centre relative to the circle centre (dp) + bar half width
// ---------------------------------------------------------------------------------------------
private class Pose(val cx: Float, val cy: Float, val hw: Float)

private class Key(val t: Float, val cx: Float, val cy: Float, val hw: Float)

private const val LoopSeconds = 7.8f

private val Keys = listOf(
    Key(0.0f, -520f, 0f, BarHalfWidthDp),   // off-screen left
    Key(0.9f, -170f, 0f, BarHalfWidthDp),   // slides in, melts with the circle
    Key(1.7f, -150f, -100f, BarHalfWidthDp), // lifts up, neck stretches then snaps
    Key(2.3f, -150f, -100f, BarHalfWidthDp), // hold apart
    Key(3.3f, -110f, 0f, BarHalfWidthDp),    // comes down and sticks again
    Key(4.5f, -110f, 0f, BarHalfWidthDp),    // hold merged
    Key(5.3f, -100f, 8f, BarHalfWidthDp),    // little bulge
    Key(6.2f, -215f, 0f, BarHalfWidthDp),    // pulls away (sticky stretch)
    Key(7.0f, 60f, -100f, BarHalfWidthDp),   // flies up and right
    Key(7.8f, 330f, -100f, BarHalfWidthDp)   // gone
)

private fun poseAt(t: Float): Pose {
    var i = 0
    while (i < Keys.size - 2 && t >= Keys[i + 1].t) i++
    val a = Keys[i]
    val b = Keys[i + 1]
    val u = ((t - a.t) / (b.t - a.t)).coerceIn(0f, 1f)
    val e = u * u * (3f - 2f * u)
    return Pose(lerp(a.cx, b.cx, e), lerp(a.cy, b.cy, e), lerp(a.hw, b.hw, e))
}

private class Geo(val circle: StickyRoundRect, val bar: StickyRoundRect)

private fun geometry(size: Size, dp: Float, pose: Pose): Geo {
    val cX = size.width - 52f * dp
    val cY = size.height - 80f * dp
    val circle = StickyRoundRect(
        centerX = cX,
        centerY = cY,
        halfWidth = CircleRadiusDp * dp,
        halfHeight = CircleRadiusDp * dp,
        cornerRadius = CircleRadiusDp * dp
    )
    val bar = StickyRoundRect(
        centerX = cX + pose.cx * dp,
        centerY = cY + pose.cy * dp,
        halfWidth = pose.hw * dp,
        halfHeight = BarHalfHeightDp * dp,
        cornerRadius = BarHalfHeightDp * dp
    )
    return Geo(circle, bar)
}

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------
@Composable
fun StickyGlassDemo() {
    val backdrop = rememberLayerBackdrop()

    var auto by remember { mutableStateOf(true) }
    val time = remember { mutableFloatStateOf(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    // Auto play: loops the video choreography
    LaunchedEffect(auto) {
        if (auto) {
            val start = withFrameNanos { it } - (time.floatValue * 1_000_000_000f).toLong()
            while (true) {
                withFrameNanos { now ->
                    time.floatValue = ((now - start) / 1_000_000_000f) % LoopSeconds
                }
            }
        }
    }

    // Manual mode: after release the bar springs back and sticks to the circle again
    LaunchedEffect(dragging, auto) {
        if (!auto && !dragging) {
            var vx = 0f
            var vy = 0f
            var last = withFrameNanos { it }
            while (abs(dragX) > 0.3f || abs(dragY) > 0.3f || abs(vx) > 2f || abs(vy) > 2f) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.032f)
                last = now
                val ax = -SpringStiffness * dragX - SpringDamping * vx
                val ay = -SpringStiffness * dragY - SpringDamping * vy
                vx += ax * dt
                vy += ay * dt
                dragX += vx * dt
                dragY += vy * dt
            }
            dragX = 0f
            dragY = 0f
        }
    }

    val currentPose: (Float) -> Pose = { dp ->
        if (auto) poseAt(time.floatValue)
        else Pose(RestCx + dragX / dp, dragY / dp, BarHalfWidthDp)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 1) wallpaper = the backdrop that the glass refracts
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .drawBehind { drawWallpaper() }
        )

        // 2) the sticky glass (bar + circle melt into one liquid surface)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(RegionHeightDp.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    highlight = null,
                    shadow = null,
                    effects = {
                        vibrancy()
                        blur(2f.dp.toPx())
                        val geo = geometry(size, density, currentPose(density))
                        stickyLens(
                            shapes = listOf(geo.bar, geo.circle),
                            smoothness = StickinessDp * density,
                            refractionHeight = RefractionHeightDp * density,
                            refractionAmount = RefractionAmountDp * density,
                            chromaticAberration = DispersionAmount,
                            tint = Color.White.copy(alpha = 0.22f),
                            rimWidth = 1.6f * density,
                            rimAlpha = 0.95f,
                            lightAngle = 45f
                        )
                    }
                )
                .then(
                    if (!auto) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false }
                            ) { change, amount ->
                                change.consume()
                                dragX += amount.x
                                dragY += amount.y
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            // icons live on top of the glass and follow the bar
            Canvas(Modifier.fillMaxSize()) {
                val dp = density
                val geo = geometry(size, dp, currentPose(dp))
                val ink = Color(0xFF1B1B20).copy(alpha = 0.9f)
                val s = 11f * dp
                val sw = 2f * dp
                val y = geo.bar.centerY
                drawHome(Offset(geo.bar.centerX - 110f * dp, y), s, ink, sw)
                drawGrid(Offset(geo.bar.centerX - 40f * dp, y), s, ink, sw)
                drawRadio(Offset(geo.bar.centerX + 30f * dp, y), s, ink, sw)
                drawSearch(Offset(geo.circle.centerX, geo.circle.centerY), s, ink, sw)
            }
        }

        // 3) mode chips
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Chip("تشغيل تلقائي", auto) { auto = true }
            Chip("اسحب بإصبعك", !auto) { auto = false }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = if (selected) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Icons (home / grid / radio / search) drawn with strokes
// ---------------------------------------------------------------------------------------------
private fun DrawScope.drawHome(c: Offset, s: Float, color: Color, sw: Float) {
    val path = Path().apply {
        moveTo(c.x - 0.85f * s, c.y + 0.9f * s)
        lineTo(c.x - 0.85f * s, c.y - 0.1f * s)
        lineTo(c.x, c.y - 0.95f * s)
        lineTo(c.x + 0.85f * s, c.y - 0.1f * s)
        lineTo(c.x + 0.85f * s, c.y + 0.9f * s)
        close()
    }
    drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawGrid(c: Offset, s: Float, color: Color, sw: Float) {
    val box = 0.8f * s
    val gap = 0.15f * s
    for (ix in 0..1) {
        for (iy in 0..1) {
            val x = c.x + (if (ix == 0) -box - gap else gap)
            val y = c.y + (if (iy == 0) -box - gap else gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(box, box),
                cornerRadius = CornerRadius(0.28f * box),
                style = Stroke(width = sw, join = StrokeJoin.Round)
            )
        }
    }
}

private fun DrawScope.drawRadio(c: Offset, s: Float, color: Color, sw: Float) {
    drawCircle(color, radius = 0.2f * s, center = c)
    val stroke = Stroke(width = sw, cap = StrokeCap.Round)
    for (r in floatArrayOf(0.6f * s, 1.05f * s)) {
        drawArc(
            color = color,
            startAngle = -40f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(2f * r, 2f * r),
            style = stroke
        )
        drawArc(
            color = color,
            startAngle = 140f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(2f * r, 2f * r),
            style = stroke
        )
    }
}

private fun DrawScope.drawSearch(c: Offset, s: Float, color: Color, sw: Float) {
    val ring = Offset(c.x - 0.15f * s, c.y - 0.15f * s)
    drawCircle(color, radius = 0.62f * s, center = ring, style = Stroke(width = sw))
    drawLine(
        color = color,
        start = Offset(c.x + 0.3f * s, c.y + 0.3f * s),
        end = Offset(c.x + 0.9f * s, c.y + 0.9f * s),
        strokeWidth = sw,
        cap = StrokeCap.Round
    )
}

// ---------------------------------------------------------------------------------------------
// Wallpaper: dark garden + pink lily (lots of contrast so the refraction / dispersion is visible)
// ---------------------------------------------------------------------------------------------
private fun DrawScope.drawWallpaper() {
    val w = size.width
    val h = size.height
    val dp = density

    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF0A150D), Color(0xFF1F3B24), Color(0xFF0B160E))
        )
    )

    val rnd = Random(7)
    repeat(16) {
        drawCircle(
            color = Color(0xFF3A6B3E).copy(alpha = 0.18f + rnd.nextFloat() * 0.15f),
            radius = (30f + rnd.nextFloat() * 90f) * dp,
            center = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        )
    }

    val c = Offset(w * 0.5f, h * 0.72f)
    val petalLen = w * 0.78f
    val petalW = w * 0.34f
    for (i in 0 until 6) {
        rotate(degrees = i * 60f + 10f, pivot = c) {
            drawOval(
                color = Color(0xFFE9AACB),
                topLeft = Offset(c.x - petalW / 2f, c.y - petalLen),
                size = Size(petalW, petalLen)
            )
            drawOval(
                color = Color(0xFFFFEEF6).copy(alpha = 0.85f),
                topLeft = Offset(c.x - petalW * 0.32f, c.y - petalLen * 0.92f),
                size = Size(petalW * 0.64f, petalLen * 0.62f)
            )
            drawLine(
                color = Color(0xFFB4508C).copy(alpha = 0.7f),
                start = c,
                end = Offset(c.x, c.y - petalLen * 0.85f),
                strokeWidth = 3f * dp
            )
        }
    }

    // stamens
    for (i in 0 until 7) {
        val a = (-75f + i * 25f) * (3.1415927f / 180f)
        val len = w * (0.30f + 0.12f * ((i * 37) % 5) / 4f)
        val end = Offset(c.x + kotlin.math.sin(a) * len, c.y - kotlin.math.cos(a) * len)
        drawLine(Color(0xFF8A2430), start = c, end = end, strokeWidth = 5f * dp, cap = StrokeCap.Round)
        drawCircle(Color(0xFFC13A2E), radius = 9f * dp, center = end)
    }

    // dew + fine lines (high frequency detail makes the refraction easy to see)
    repeat(60) {
        drawCircle(
            color = Color.White.copy(alpha = 0.35f + rnd.nextFloat() * 0.4f),
            radius = (1.5f + rnd.nextFloat() * 3.5f) * dp,
            center = Offset(rnd.nextFloat() * w, h * 0.35f + rnd.nextFloat() * h * 0.65f)
        )
    }
    var x = 0f
    while (x < w) {
        drawLine(
            color = Color.White.copy(alpha = 0.10f),
            start = Offset(x, h * 0.45f),
            end = Offset(x, h),
            strokeWidth = 1.5f * dp
        )
        x += 18f * dp
    }
}
