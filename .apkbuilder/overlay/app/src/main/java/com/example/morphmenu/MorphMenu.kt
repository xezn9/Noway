package com.example.morphmenu

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/* ───────────────────────── Demo Activity ───────────────────────── */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                // آخر عنصر حتى يطلع فوق كل شي
                MorphMenu(Modifier.fillMaxSize()) { label ->
                    Toast.makeText(this@MainActivity, label, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/* ───────────────────────── القياسات (كلها dp) ───────────────────────── */

private const val PAD = 40f      // مساحة إضافية حوالين الشكل (للـ overshoot والـ blur)
private const val BTN = 56f      // قطر زر الأفاتار
private const val MENU_W = 224f
private const val MENU_H = 320f
private const val MENU_R = 36f   // انحناء زوايا القائمة
private const val AREA_W = MENU_W + 2 * PAD
private const val AREA_H = MENU_H + 2 * PAD
private const val BTN_CX = AREA_W - PAD - BTN / 2
private const val BTN_CY = PAD + BTN / 2
private const val TR_X = AREA_W - PAD
private const val TR_Y = PAD

private val FILL = Color(0xFF2A2A2A)
private val FILL_PRESSED = Color(0xFF5A5A5A)
private val BORDER = Color(0xFF5E5E5E)
private val BORDER_PRESSED = Color(0xFF8C8C8C)

private data class Entry(val label: String, val icon: ImageVector?, val kind: Int = 0)

private val entries = listOf(
    Entry("Sign in", Icons.Outlined.AccountCircle),
    Entry("Refresh", Icons.Rounded.Refresh),
    Entry("Rate the app", Icons.Rounded.Star),
    Entry("Instagram", null, kind = 1),
    Entry("Support to keep the app free forever", null, kind = 2),
)

/* ───────────────────────── الويدجت الرئيسي ───────────────────────── */

@Composable
fun MorphMenu(modifier: Modifier = Modifier, onSelected: (String) -> Unit = {}) {
    // p = 0 مسكّر ، p = 1 مفتوح (Animatable بدون حدود حتى يسمح للـ spring بالـ overshoot)
    val p = remember { Animatable(0f) }
    val press = remember { Animatable(0f) }
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val topInset = WindowInsets.statusBars.let { with(LocalDensity.current) { it.getTop(this).toDp() } }

    fun toggle() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        open = !open
        scope.launch {
            p.animateTo(
                if (open) 1f else 0f,
                spring(dampingRatio = 0.65f, stiffness = 190f, visibilityThreshold = 0.001f)
            )
        }
    }

    Box(modifier) {
        // الضغط خارج القائمة يسكرها
        if (open) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { toggle() } })
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (PAD - 16f).dp, y = topInset + (12f - PAD).dp)
                .size(AREA_W.dp, AREA_H.dp)
        ) {
            // 1) الإطار (طبقة أفتح)  2) التعبئة (أصغر بـ 1.2 حتى يبين الإطار)
            GooLayer(Modifier.fillMaxSize(), p, press, BORDER, BORDER_PRESSED, inset = 0f)
            GooLayer(Modifier.fillMaxSize(), p, press, FILL, FILL_PRESSED, inset = 1.2f)

            // 3) العناصر، مقصوصة بنفس شكل الجسم المتحرك
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = BodyShape(p.value)
                    }
            ) {
                Column(
                    Modifier
                        .offset(PAD.dp, PAD.dp)
                        .size(MENU_W.dp, MENU_H.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    entries.forEachIndexed { i, e ->
                        MenuItem(e, i, p) {
                            if (open) {
                                onSelected(e.label)
                                toggle()
                            }
                        }
                    }
                }
            }

            // 4) أيقونة الأفاتار + زر الضغط
            if (!open) {
                Box(
                    Modifier
                        .offset((BTN_CX - BTN / 2).dp, (BTN_CY - BTN / 2).dp)
                        .size(BTN.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    scope.launch { press.animateTo(1f, tween(120)) }
                                    tryAwaitRelease()
                                    scope.launch { press.animateTo(0f, tween(120)) }
                                },
                                onTap = {
                                    scope.launch {
                                        delay(90) // وقفة صغيرة حتى تبين الإضاءة قبل الـ morph
                                        toggle()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AvatarIcon(p, press.value)
                }
            }
        }
    }
}

@Composable
private fun AvatarIcon(p: Animatable<Float, *>, press: Float) {
    Icon(
        Icons.Rounded.Person,
        contentDescription = null,
        tint = lerp(Color(0xFF8E8E8E), Color(0xFFC4C4C4), press),
        modifier = Modifier
            .size(30.dp)
            .graphicsLayer { alpha = (1f - p.value / 0.15f).coerceIn(0f, 1f) }
    )
}

@Composable
private fun MenuItem(e: Entry, index: Int, p: Animatable<Float, *>, onClick: () -> Unit) {
    Row(
        Modifier
            .graphicsLayer {
                // كل عنصر يطلع بتأخير بسيط عن اللي قبله (stagger)
                val o = ((p.value - 0.40f - index * 0.055f) / 0.28f).coerceIn(0f, 1f)
                alpha = o
                translationY = 10.dp.toPx() * (1f - o)
            }
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(start = 22.dp, top = 16.dp, end = 44.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when {
                e.icon != null -> Icon(e.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                e.kind == 1 -> InstagramIcon()
                else -> Box(
                    Modifier.size(22.dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("$", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(e.label, color = Color.White, fontSize = 15.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun InstagramIcon() {
    Canvas(Modifier.size(22.dp)) {
        val sw = Stroke(width = 1.9.dp.toPx())
        drawRoundRect(
            Color.White,
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
            cornerRadius = CornerRadius(size.width * 0.3f),
            style = sw
        )
        drawCircle(Color.White, radius = size.width * 0.22f, style = sw)
        drawCircle(Color.White, radius = 1.3.dp.toPx(), center = Offset(size.width * 0.76f, size.height * 0.24f))
    }
}

/* ───────────────────────── طبقة الـ Gooey ───────────────────────── */

@Composable
private fun GooLayer(
    modifier: Modifier,
    p: Animatable<Float, *>,
    press: Animatable<Float, *>,
    color: Color,
    pressedColor: Color,
    inset: Float,
) {
    val density = LocalDensity.current.density
    // الـ blur + threshold يشتغل من Android 12 (API 31) وفوق. تحته يرسم الأشكال بدون التحام.
    val effect = remember(density) {
        if (Build.VERSION.SDK_INT >= 31) gooEffect(7f * density) else null
    }
    Canvas(modifier.graphicsLayer { renderEffect = effect }) {
        val pv = p.value
        val pr = press.value * (1f - pv.coerceIn(0f, 1f))
        drawShapes(pv, lerp(color, pressedColor, pr), inset)
    }
}

@RequiresApi(31)
private fun gooEffect(sigma: Float): RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.DECAL)
    // نفس المصفوفة اللي بنسخة Flutter: الألفا × 25 − 12 (الإزاحة بوحدات 0..255)
    val matrix = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 25f, -12f * 255f,
        )
    )
    val color = android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
    // inner (blur) أول، بعدين outer (threshold)
    return android.graphics.RenderEffect.createChainEffect(color, blur).asComposeRenderEffect()
}

/* ───────────────────────── الشكل (Geometry) ───────────────────────── */

private fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private class Geo(p: Float, d: Float) {
    var hasBody = false
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f
    var radius = 0f
    val circleR: Float

    init {
        val w = MENU_W * d * p
        val h = MENU_H * d * p
        if (w > 2f && h > 2f) {
            val t = p.coerceIn(0f, 1f)
            // الزاوية العليا اليمنى تنتقل من مركز الزر إلى زاوية القائمة النهائية
            val trx = (BTN_CX + (TR_X - BTN_CX) * t) * d
            val try_ = (BTN_CY + (TR_Y - BTN_CY) * t) * d
            var r = (BTN / 2 + (MENU_R - BTN / 2) * t) * d
            r = min(r, min(w, h) / 2f)
            left = trx - w
            top = try_
            right = trx
            bottom = try_ + h
            radius = r
            hasBody = true
        }
        // الدايرة (الزر) تصغر وتختفي بنص الفتح، وترجع تطلع "كنتوء" عند الإغلاق
        circleR = (BTN / 2) * d * (1f - smooth((p - 0.45f) / 0.40f))
    }
}

private fun DrawScope.drawShapes(p: Float, color: Color, inset: Float) {
    val g = Geo(p, density)
    val ins = inset * density

    // +1dp تعويض بسيط لأن الـ blur يصغّر الدايرة شوي
    val cr = g.circleR + 1f * density - ins
    if (g.circleR > 0.5f && cr > 0f) {
        drawCircle(color, cr, Offset(BTN_CX * density, BTN_CY * density))
    }
    if (g.hasBody) {
        val w = g.right - g.left - 2 * ins
        val h = g.bottom - g.top - 2 * ins
        if (w > 2f && h > 2f) {
            drawRoundRect(
                color,
                topLeft = Offset(g.left + ins, g.top + ins),
                size = Size(w, h),
                cornerRadius = CornerRadius(max(g.radius - ins, 0f))
            )
        }
    }
}

private class BodyShape(private val p: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val g = Geo(p, density.density)
        if (g.hasBody) {
            path.addRoundRect(RoundRect(g.left, g.top, g.right, g.bottom, CornerRadius(g.radius)))
        }
        return Outline.Generic(path)
    }
}
