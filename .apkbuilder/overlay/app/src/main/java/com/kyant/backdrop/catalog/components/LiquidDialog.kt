package com.kyant.backdrop.catalog.components

import com.kyant.backdrop.catalog.utils.LocalGlassLevels
import com.kyant.backdrop.catalog.utils.scaleAlpha
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * Liquid-glass dialog: light scrim + a glass panel that refracts the WHOLE screen behind it
 * (pass the screen-level backdrop, not just the wallpaper), with a title, message and two buttons.
 */
@Composable
fun LiquidDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    dark: Boolean,
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    confirmTint: Color = Color(0xFF0088FF),
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassLevels.current
    BackHandler(enabled = visible, onBack = onDismiss)

    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val contentColor = if (dark) Color.White else Color(0xFF1B1B1F)
    val containerColor =
        if (dark) Color(0xFF121212).copy(0.45f)
        else Color(0xFFFAFAFA).copy(0.45f)
    val buttonSurface =
        if (dark) Color.White.copy(0.10f)
        else Color.White.copy(0.30f)

    val panelScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "dialogScale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160)),
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (dark) 0.40f else 0.22f))
                .pointerInput(Unit) { detectTapGestures { currentOnDismiss() } },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .padding(32.dp)
                    .widthIn(max = 340.dp)
                    .graphicsLayer {
                        scaleX = panelScale
                        scaleY = panelScale
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(36.dp) },
                        effects = {
                            vibrancy()
                            blur((14f.dp.toPx()) * glass.blurK)
                            lens((16f.dp.toPx()) * glass.lensK, (32f.dp.toPx()) * glass.lensK)
                        },
                        onDrawSurface = { drawRect(containerColor.scaleAlpha(glass.opacityK)) }
                    )
                    // swallow taps so touching the panel doesn't dismiss
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    title,
                    color = contentColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    message,
                    color = contentColor.copy(alpha = 0.72f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidButton(
                        onClick = onDismiss,
                        backdrop = backdrop,
                        surfaceColor = buttonSurface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(dismissText, color = contentColor, fontWeight = FontWeight.Medium)
                    }
                    LiquidButton(
                        onClick = onConfirm,
                        backdrop = backdrop,
                        tint = confirmTint,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(confirmText, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
