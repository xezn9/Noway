package com.ecogif.app.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

val EcoGreen = Color(0xFF1FA971)

class EcoColors(val dark: Boolean) {
    val content = if (dark) Color.White else Color(0xFF0E2A1E)
    val secondary = content.copy(alpha = 0.68f)
    val field = if (dark) Color.White.copy(0.10f) else Color.White.copy(0.50f)
    val card = if (dark) Color(0xFF0E1512).copy(0.42f) else Color.White.copy(0.36f)
}

// ----------------------------------------------------------------- icons

private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()

object EcoIcons {
    val Resize: ImageVector by lazy {
        icon("resize", "M21,11V3h-8l3.29,3.29 -10,10L3,13v8h8l-3.29,-3.29 10,-10z")
    }
    val Movie: ImageVector by lazy {
        icon(
            "movie",
            "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z"
        )
    }
    val Gif: ImageVector by lazy {
        icon(
            "gif",
            "M11.5,9H13v6h-1.5zM9,9H6c-0.6,0 -1,0.5 -1,1v4c0,0.5 0.4,1 1,1h3c0.6,0 1,-0.5 1,-1v-2H8.5v1.5h-2v-3H10V10c0,-0.5 -0.4,-1 -1,-1zM19,10.5V9h-4.5v6H16v-2h2v-1.5h-2v-1z"
        )
    }
}

// ----------------------------------------------------------------- wallpaper

/** Colours of one tab: wallpaper gradient, four glowing blobs and the accent. */
class Palette(
    val accent: Color, val accent2: Color,
    val top: Color, val mid: Color, val bot: Color,
    val b1: Color, val b2: Color, val b3: Color, val b4: Color,
)

fun paletteFor(tab: Int, dark: Boolean): Palette = when (tab) {
    // 1: video -> GIF  (sunset)
    1 -> if (dark) Palette(
        Color(0xFFFF7A2F), Color(0xFFFFC857),
        Color(0xFF1F0A12), Color(0xFF3B1A14), Color(0xFF24100A),
        Color(0xFFFF7A45).copy(0.55f), Color(0xFFFF4D8D).copy(0.50f),
        Color(0xFFFFC857).copy(0.40f), Color(0xFFE8590C).copy(0.50f)
    ) else Palette(
        Color(0xFFF2701F), Color(0xFFFFB02E),
        Color(0xFFFFF1E8), Color(0xFFFFE0CC), Color(0xFFFFF4D6),
        Color(0xFFFFB38A).copy(0.75f), Color(0xFFFF9EC0).copy(0.75f),
        Color(0xFFFFE08A).copy(0.80f), Color(0xFFFFA46B).copy(0.65f)
    )
    // 2: GIF -> video  (ocean / violet)
    2 -> if (dark) Palette(
        Color(0xFF4C7DFF), Color(0xFF9B6CFF),
        Color(0xFF0A0F24), Color(0xFF14204A), Color(0xFF0A1030),
        Color(0xFF5B8CFF).copy(0.55f), Color(0xFF9B5CFF).copy(0.50f),
        Color(0xFF3FD0E8).copy(0.40f), Color(0xFF3A5BDB).copy(0.55f)
    ) else Palette(
        Color(0xFF3D6BF0), Color(0xFF8E5CF5),
        Color(0xFFECF1FF), Color(0xFFDCE5FF), Color(0xFFEFE6FF),
        Color(0xFF9DB8FF).copy(0.75f), Color(0xFFC6A8FF).copy(0.75f),
        Color(0xFF9FE4F2).copy(0.80f), Color(0xFF8AA4F5).copy(0.65f)
    )
    // 0: resize  (eco green)
    else -> if (dark) Palette(
        EcoGreen, Color(0xFFB6F25C),
        Color(0xFF031A13), Color(0xFF0A3B2B), Color(0xFF052418),
        Color(0xFF3FD98B).copy(0.55f), Color(0xFF17B3A6).copy(0.50f),
        Color(0xFFB6F25C).copy(0.40f), Color(0xFF1FA971).copy(0.55f)
    ) else Palette(
        EcoGreen, Color(0xFF8BD62F),
        Color(0xFFEAFBF0), Color(0xFFD5F5E3), Color(0xFFF3FBD8),
        Color(0xFF7BE3A5).copy(0.75f), Color(0xFF8EE8DA).copy(0.75f),
        Color(0xFFD7F57F).copy(0.80f), Color(0xFF5ED79A).copy(0.65f)
    )
}

/** Colours glide smoothly from one tab's palette to the next. */
@Composable
fun animatedPalette(t: Palette): Palette {
    val spec = tween<Color>(durationMillis = 800)
    return Palette(
        animateColorAsState(t.accent, spec, label = "accent").value,
        animateColorAsState(t.accent2, spec, label = "accent2").value,
        animateColorAsState(t.top, spec, label = "top").value,
        animateColorAsState(t.mid, spec, label = "mid").value,
        animateColorAsState(t.bot, spec, label = "bot").value,
        animateColorAsState(t.b1, spec, label = "b1").value,
        animateColorAsState(t.b2, spec, label = "b2").value,
        animateColorAsState(t.b3, spec, label = "b3").value,
        animateColorAsState(t.b4, spec, label = "b4").value,
    )
}

val LocalAccent = compositionLocalOf { EcoGreen }

// ----------------------------------------------------------------- wallpaper

/** Animated wallpaper: the glow blobs drift when [shift] (the tab position) changes. */
@Composable
fun EcoWallpaper(dark: Boolean, palette: Palette, shift: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(palette.top, palette.mid, palette.bot)))
        fun blob(i: Int, cx: Float, cy: Float, r: Float, c: Color) {
            val x = cx + w * 0.16f * sin(shift * 1.9f + i * 1.3f)
            val y = cy + h * 0.06f * cos(shift * 1.4f + i * 0.9f)
            drawCircle(
                brush = Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(x, y), radius = r),
                radius = r,
                center = Offset(x, y)
            )
        }
        blob(0, w * 0.15f, h * 0.10f, w * 0.75f, palette.b1)
        blob(1, w * 0.95f, h * 0.35f, w * 0.70f, palette.b2)
        blob(2, w * 0.10f, h * 0.62f, w * 0.65f, palette.b3)
        blob(3, w * 0.85f, h * 0.90f, w * 0.80f, palette.b4)
        // a few soft stripes so the lens refraction is clearly visible
        val stripe = if (dark) Color.White.copy(0.05f) else Color.White.copy(0.35f)
        for (i in 0..5) {
            drawRect(stripe, topLeft = Offset(0f, h * (0.08f + i * 0.17f)), size = Size(w, h * 0.012f))
        }
    }
}

// ----------------------------------------------------------------- building blocks

@Composable
fun GlassCard(
    backdrop: Backdrop,
    colors: EcoColors,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = {
                    vibrancy()
                    blur(10f.dp.toPx())
                    lens(10f.dp.toPx(), 20f.dp.toPx())
                },
                onDrawSurface = { drawRect(colors.card) }
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
fun CardTitle(text: String, colors: EcoColors) {
    Text(text, color = colors.content, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop,
    colors: EcoColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = colors.content, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(valueText, color = colors.secondary, fontSize = 15.sp)
        }
        LiquidSlider(
            value = { value },
            onValueChange = onValueChange,
            valueRange = range,
            visibilityThreshold = (range.endInclusive - range.start) / 1000f,
            backdrop = backdrop,
            dark = colors.dark
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    colors: EcoColors,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.content, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = colors.secondary, fontSize = 12.sp)
        }
        LiquidToggle(
            selected = { checked },
            onSelect = onChange,
            backdrop = backdrop,
            dark = colors.dark
        )
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    colors: EcoColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = colors.secondary, fontSize = 12.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = colors.content, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(LocalAccent.current),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.field)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun BitmapPreview(bitmap: Bitmap, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(190.dp)
        )
    }
}

/** Animated GIF preview (uses the platform ImageDecoder, so it loops like a normal GIF). */
@Composable
fun GifPreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var drawable by remember(uri) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(uri) {
        drawable = withContext(Dispatchers.IO) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    val big = maxOf(info.size.width, info.size.height)
                    decoder.setTargetSampleSize(maxOf(1, big / 720))
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(0.18f)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = { iv ->
                iv.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.start()
            },
            modifier = Modifier.fillMaxWidth().height(190.dp)
        )
    }
}
