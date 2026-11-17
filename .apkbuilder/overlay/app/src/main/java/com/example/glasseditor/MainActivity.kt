package com.example.glasseditor

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

class Stroke2(val points: List<Offset>, val color: Color)

class MainActivity : ComponentActivity() {

    // الصورة تجي من لقطة الشاشة أو من المعرض (ACTION_EDIT) أو من المشاركة (ACTION_SEND)
    private fun incomingUri(): Uri? = when (intent?.action) {
        Intent.ACTION_SEND ->
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> intent?.data
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = incomingUri()
        val bmp = uri?.let { contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream) }
        setContent { Editor(bmp) { saveBitmap(it) } }
    }

    private fun saveBitmap(bmp: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "edit_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GlassEditor")
        }
        val out = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        out?.let { contentResolver.openOutputStream(it)?.use { s -> bmp.compress(Bitmap.CompressFormat.PNG, 100, s) } }
        Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun Editor(bitmap: Bitmap?, onSave: (Bitmap) -> Unit) {
    if (bitmap == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            Text("لا توجد صورة", color = Color.White)
        }
        return
    }

    val backdrop = rememberLayerBackdrop()
    val strokes = remember { mutableStateListOf<Stroke2>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var color by remember { mutableStateOf(Color.Red) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val palette = listOf(Color.Red, Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFF2196F3), Color.White, Color.Black)

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // الصورة + الرسم: هذا هو المحتوى الي يتشوّه خلف الزجاج
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).onSizeChanged { size = it }) {
            Image(
                bitmap.asImageBitmap(), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit
            )
            Canvas(
                Modifier.fillMaxSize().pointerInput(color) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDrag = { c, _ -> current = current + c.position },
                        onDragEnd = { strokes.add(Stroke2(current, color)); current = emptyList() }
                    )
                }
            ) {
                (strokes + Stroke2(current, color)).forEach { s ->
                    if (s.points.size > 1) {
                        val p = Path().apply {
                            moveTo(s.points[0].x, s.points[0].y)
                            s.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(p, s.color, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }

        // شريط علوي زجاجي
        GlassBar(backdrop, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp)) {
            GlassText("تراجع") { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }
            GlassText("مسح") { strokes.clear() }
            GlassText("حفظ") { onSave(render(bitmap, strokes, size)) }
        }

        // شريط الألوان الزجاجي
        GlassBar(backdrop, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)) {
            palette.forEach { c ->
                Box(
                    Modifier.size(if (c == color) 36.dp else 28.dp)
                        .background(c, CircleShape)
                        .clickable { color = c }
                )
            }
        }
    }
}

@Composable
fun GlassBar(backdrop: com.kyant.backdrop.backdrops.LayerBackdrop, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(50) },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.18f)) }
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun GlassText(t: String, onClick: () -> Unit) =
    Text(t, color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onClick))

// يرسم الصورة والخطوط على Bitmap نهائي للحفظ
fun render(src: Bitmap, strokes: List<Stroke2>, view: IntSize): Bitmap {
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    if (view.width == 0) return out
    val scale = minOf(view.width / src.width.toFloat(), view.height / src.height.toFloat())
    val ox = (view.width - src.width * scale) / 2f
    val oy = (view.height - src.height * scale) / 2f
    val canvas = android.graphics.Canvas(out)
    strokes.forEach { s ->
        val paint = android.graphics.Paint().apply {
            this.color = s.color.toArgb(); style = android.graphics.Paint.Style.STROKE
            strokeWidth = 8f * 3f / scale; strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND; isAntiAlias = true
        }
        val p = android.graphics.Path()
        s.points.forEachIndexed { i, pt ->
            val x = (pt.x - ox) / scale; val y = (pt.y - oy) / scale
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        canvas.drawPath(p, paint)
    }
    return out
}
