package com.mfp.filemanager.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.mfp.filemanager.ui.theme.GlassConfig
import com.mfp.filemanager.ui.util.Immersive

/**
 * Live preview for the Liquid Glass sliders in Settings: a colourful scene (stripes, circles, big
 * letters) with a real Backdrop glass card on top. It uses exactly the same GlassConfig as the
 * tabs / dialogs / folders, so what you see here is what the rest of the app gets.
 */
class GlassPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private val dark: Boolean = Immersive.isDarkTheme(context)

    @Composable
    override fun Content() {
        val scene = rememberLayerBackdrop()
        val accent = GlassConfig.accent
        val light = lerp(accent, Color.White, 0.55f)
        val deep = lerp(accent, Color.Black, 0.45f)
        val glassTint = if (dark) Color(0xFF121212).copy(0.4f) else Color(0xFFFAFAFA).copy(0.4f)

        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            // ---- the scene the glass looks at ----
            Box(
                Modifier
                    .matchParentSize()
                    .layerBackdrop(scene)
                    .drawBehind {
                        drawRect(Brush.linearGradient(listOf(light, accent, deep), Offset.Zero, Offset(size.width, size.height)))
                        // diagonal bars: their edges bend visibly when refraction is high
                        val bar = size.height * 0.16f
                        var x = -size.height
                        var i = 0
                        while (x < size.width + size.height) {
                            drawLine(
                                color = (if (i % 2 == 0) Color.White else Color.Black).copy(alpha = 0.30f),
                                start = Offset(x, size.height),
                                end = Offset(x + size.height, 0f),
                                strokeWidth = bar
                            )
                            x += bar * 2.4f
                            i++
                        }
                        drawCircle(Color.White.copy(0.75f), size.height * 0.20f, Offset(size.width * 0.18f, size.height * 0.28f))
                        drawCircle(Color.Black.copy(0.45f), size.height * 0.16f, Offset(size.width * 0.84f, size.height * 0.74f))
                    }
            ) {
                Text(
                    text = "ABC 123",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // ---- real glass on top (outside the layer it samples) ----
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 210.dp, height = 84.dp)
                    .drawBackdrop(
                        backdrop = scene,
                        shape = { RoundedCornerShape(42.dp) },
                        effects = {
                            vibrancy()
                            val glassBlur = GlassConfig.blurDp(8f)
                            if (glassBlur > 0.01f) blur(glassBlur.dp.toPx())
                            val glassLensH = GlassConfig.lensDp(24f)
                            val glassLensA = GlassConfig.lensDp(24f)
                            if (glassLensH > 0.01f && glassLensA > 0.01f) lens(glassLensH.dp.toPx(), glassLensA.dp.toPx())
                        },
                        onDrawSurface = { drawRect(GlassConfig.surface(glassTint)) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Liquid Glass",
                    color = if (dark) Color.White else Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
