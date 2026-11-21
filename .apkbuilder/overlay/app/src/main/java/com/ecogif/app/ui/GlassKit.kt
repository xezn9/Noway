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
import androidx.compose.ui.graphics.toArgb
import com.kyant.backdrop.catalog.utils.LocalGlassLevels
import com.kyant.backdrop.catalog.utils.scaleAlpha
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
    val Settings: ImageVector by lazy {
        icon(
            "settings",
            "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"
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

/** Builds a whole wallpaper palette (gradient + glow blobs + accent) from one chosen colour. */
fun paletteFor(base: Color, dark: Boolean): Palette {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    val h = hsv[0]
    fun hue(d: Float) = ((h + d) % 360f + 360f) % 360f
    return if (dark) Palette(
        base, Color.hsv(hue(40f), 0.55f, 1f),
        Color.hsv(h, 0.80f, 0.10f), Color.hsv(h, 0.75f, 0.23f), Color.hsv(h, 0.80f, 0.14f),
        Color.hsv(h, 0.60f, 0.95f, 0.55f), Color.hsv(hue(32f), 0.70f, 0.90f, 0.50f),
        Color.hsv(hue(-32f), 0.60f, 0.95f, 0.40f), base.copy(alpha = 0.55f)
    ) else Palette(
        base, Color.hsv(hue(40f), 0.70f, 0.95f),
        Color.hsv(h, 0.07f, 1f), Color.hsv(h, 0.16f, 1f), Color.hsv(hue(20f), 0.10f, 1f),
        Color.hsv(h, 0.42f, 1f, 0.75f), Color.hsv(hue(32f), 0.40f, 1f, 0.75f),
        Color.hsv(hue(-32f), 0.45f, 1f, 0.80f), Color.hsv(h, 0.55f, 0.95f, 0.65f)
    )
}

class Swatch(val name: String, val color: Color)

/** The 20 colours a page can use. */
val Swatches = listOf(
    Swatch("Green", Color(0xFF1FA971)), Swatch("Orange", Color(0xFFFF7A2F)),
    Swatch("Blue", Color(0xFF4C7DFF)), Swatch("Red", Color(0xFFE5484D)),
    Swatch("Pink", Color(0xFFF0509A)), Swatch("Purple", Color(0xFF9B5CFF)),
    Swatch("Teal", Color(0xFF14B8A6)), Swatch("Cyan", Color(0xFF22C5E8)),
    Swatch("Lime", Color(0xFF84CC16)), Swatch("Yellow", Color(0xFFF5C518)),
    Swatch("Amber", Color(0xFFF59E0B)), Swatch("Coral", Color(0xFFFF6B6B)),
    Swatch("Indigo", Color(0xFF5B5BD6)), Swatch("Violet", Color(0xFF8E4EC6)),
    Swatch("Magenta", Color(0xFFD6409F)), Swatch("Mint", Color(0xFF3DDC84)),
    Swatch("Sky", Color(0xFF38BDF8)), Swatch("Gold", Color(0xFFD4A017)),
    Swatch("Rose", Color(0xFFFB7185)), Swatch("Slate", Color(0xFF64748B)),
)

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
    val glass = LocalGlassLevels.current
    Column(
        modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = {
                    vibrancy()
                    blur(10f.dp.toPx() * glass.blurK)
                    lens(10f.dp.toPx() * glass.lensK, 20f.dp.toPx() * glass.lensK)
                },
                onDrawSurface = { drawRect(colors.card.scaleAlpha(glass.opacityK)) }
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
