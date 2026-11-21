package com.ecogif.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecogif.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val Navy = Color(0xFF0A1E36)
private val Navy2 = Color(0xFF050D1A)
private val Teal = Color(0xFF14B8A6)
private val Orange = Color(0xFFFF5A1F)
private val Amber = Color(0xFFFFB81F)

/**
 * Animated start screen: drifting colour glows, the app icon springs in with a light sweep,
 * then the name and tagline fade in and everything dissolves into the app. Tap to skip.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logo = remember { Animatable(0f) }
    val shine = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val out = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val finished = remember { booleanArrayOf(false) }

    fun finish() {
        if (finished[0]) return
        finished[0] = true
        scope.launch {
            out.animateTo(1f, tween(380))
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        launch { logo.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 90f)) }
        delay(450)
        launch { shine.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        delay(150)
        title.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        delay(750)
        finish()
    }

    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "driftValue"
    )

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - out.value
                val s = 1f + 0.06f * out.value
                scaleX = s
                scaleY = s
            }
            .background(Brush.verticalGradient(listOf(Navy, Navy2)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
        contentAlignment = Alignment.Center
    ) {
        // drifting colour glows (the colours of the icon)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun glow(c: Color, cx: Float, cy: Float, r: Float, phase: Float) {
                val x = cx + w * 0.10f * sin(drift + phase)
                val y = cy + h * 0.05f * cos(drift * 1.3f + phase)
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = 0.55f * logo.value), Color.Transparent), Offset(x, y), r),
                    r, Offset(x, y)
                )
            }
            glow(Teal, w * 0.12f, h * 0.18f, w * 0.80f, 0f)
            glow(Orange, w * 0.92f, h * 0.55f, w * 0.85f, 2.1f)
            glow(Amber, w * 0.25f, h * 0.92f, w * 0.75f, 4.2f)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(168.dp)
                    .graphicsLayer {
                        val s = 0.35f + 0.65f * logo.value
                        scaleX = s
                        scaleY = s
                        rotationZ = (1f - logo.value) * -14f
                        alpha = logo.value.coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(40.dp))
                    .drawWithContent {
                        drawContent()
                        // light sweep across the icon
                        val p = shine.value
                        if (p > 0f && p < 1f) {
                            val x = -size.width * 0.4f + (size.width * 1.8f) * p
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(0.35f), Color.Transparent),
                                    start = Offset(x, 0f),
                                    end = Offset(x + size.width * 0.4f, size.height * 0.4f)
                                )
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // the launcher foreground has transparent padding; crop it away so only the art shows
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.requiredSize(227.dp)
                )
            }

            Spacer(Modifier.height(26.dp))

            Box(
                Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 24f
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "EcoGif",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Smaller GIFs, made simple",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
