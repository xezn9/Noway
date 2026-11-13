package com.example.glassvolume

import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import kotlinx.coroutines.delay
import kotlin.math.abs

/** مدة أنيميشن الخروج قبل ما نخفي النافذة */
private const val EXIT_MS = 380L

/* ───────────── الزجاج ─────────────
   - عدسة + تغبيش خفيف من المكتبة
   - فوقها: لمعة علوية ناعمة + حافة زجاجية (ضوء من الزاوية العليا واليسرى والسفلى اليمنى)
   - boost: يكبّر قوة العدسة وقت اللمس (الزجاج «ينتفخ» تحت إصبعك) */

fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.06f),
    lensHeight: Dp = 12.dp,
    lensAmount: Dp = 24.dp,
    boost: () -> Float = { 0f },
    rim: Float = 1f,
    corner: Dp? = null,
    joinTop: () -> Float = { 0f },      // 0..1: يمسح الحافة من فوق (مكان التصاق جسر سائل)
    joinBottom: () -> Float = { 0f },   // 0..1: يمسح الحافة من تحت
    blurRadius: Dp = 1.dp,              // شدة الضبابية (من الإعدادات)
    rimColor: Color = Color.White,      // لون اللمعة والحافة (من الإعدادات)
) = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        val b = boost()
        if (blurRadius.value > 0.01f) blur(blurRadius.toPx())
        // انكسار صفر = بدون عدسة (نتجنب عدسة بقيمة صفر)
        if (lensHeight.value > 0.5f && lensAmount.value > 0.5f) {
            lens(lensHeight.toPx() * (1f + 0.2f * b), lensAmount.toPx() * (1f + 0.35f * b))
        }
    },
    onDrawSurface = {
        drawRect(tint)
        val r = corner?.toPx() ?: (minOf(size.width, size.height) / 2f)
        val cr = CornerRadius(r, r)
        val jt = joinTop()
        val jb = joinBottom()
        val erase = jt > 0.001f || jb > 0.001f
        // لو فيه التصاق: نرسم اللمعة + الحافة بطبقة مستقلة ونمسح منها مكان الجسر
        if (erase) drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
        // لمعة علوية ناعمة
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to rimColor.mulAlpha(0.14f * rim),
                0.5f to rimColor.mulAlpha(0f),
            ),
            cornerRadius = cr,
        )
        // حافة زجاجية
        val sw = 1.6.dp.toPx()
        drawRoundRect(
            brush = Brush.linearGradient(
                0f to rimColor.mulAlpha(0.85f * rim),
                0.32f to rimColor.mulAlpha(0.10f * rim),
                0.68f to rimColor.mulAlpha(0.08f * rim),
                1f to rimColor.mulAlpha(0.55f * rim),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            topLeft = Offset(sw / 2f, sw / 2f),
            size = Size(size.width - sw, size.height - sw),
            cornerRadius = CornerRadius(maxOf(r - sw / 2f, 0f)),
            style = Stroke(width = sw),
        )
        if (erase) {
            val er = 22.dp.toPx()
            fun eraseAt(center: Offset, a: Float) = drawRect(
                brush = Brush.radialGradient(
                    0f to Color.Black.copy(alpha = a.coerceIn(0f, 1f)),
                    0.6f to Color.Black.copy(alpha = a.coerceIn(0f, 1f)),
                    1f to Color.Transparent,
                    center = center,
                    radius = er,
                ),
                blendMode = BlendMode.DstOut,
            )
            if (jb > 0.001f) eraseAt(Offset(size.width / 2f, size.height), jb)
            if (jt > 0.001f) eraseAt(Offset(size.width / 2f, 0f), jt)
            drawIntoCanvas { it.restore() }
        }
    },
)

/** يضرب شفافية اللون (alpha) بمعامل، ويحصرها 0..1 */
private fun Color.mulAlpha(a: Float) = copy(alpha = (alpha * a).coerceIn(0f, 1f))

/** زجاج «الشريط» (السلايدر + البطاقة الموسّعة) حسب إعدادات المستخدم */
@Composable
private fun Modifier.barGlass(
    backdrop: Backdrop,
    shape: Shape,
    boost: () -> Float = { 0f },
    corner: Dp? = null,
    joinBottom: () -> Float = { 0f },
    lensScale: Float = 1f,
): Modifier {
    val st = LocalGlassStyle.current
    return liquidGlass(
        backdrop, shape,
        tint = st.barTint.copy(alpha = st.barTintAlpha),
        lensHeight = st.barLensHeight * lensScale,
        lensAmount = st.barLensAmount * lensScale,
        boost = boost,
        rim = st.barRimMul,
        corner = corner,
        joinBottom = joinBottom,
        blurRadius = st.barBlurDp,
        rimColor = st.rimColor,
    )
}

/** زجاج «الأزرار» حسب إعدادات المستخدم */
@Composable
private fun Modifier.btnGlass(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color,
    boost: () -> Float = { 0f },
    joinTop: () -> Float = { 0f },
): Modifier {
    val st = LocalGlassStyle.current
    return liquidGlass(
        backdrop, shape,
        tint = tint,
        lensHeight = st.btnLensHeight,
        lensAmount = st.btnLensAmount,
        boost = boost,
        rim = st.btnRimMul,
        joinTop = joinTop,
        blurRadius = st.btnBlurDp,
        rimColor = st.rimColor,
    )
}

private fun streamIcon(stream: Int, level: Float) = when (stream) {
    AudioManager.STREAM_RING -> Icons.Rounded.Notifications
    AudioManager.STREAM_NOTIFICATION -> Icons.Rounded.Sms
    else -> Icons.Rounded.Alarm
}

private fun ringerIcon(mode: Int) = when (mode) {
    AudioManager.RINGER_MODE_NORMAL -> Icons.Rounded.Notifications
    AudioManager.RINGER_MODE_VIBRATE -> Icons.Rounded.Vibration
    else -> Icons.Rounded.NotificationsOff
}

/* ───────────── أيقونة السماعة (مثل SF Symbols) ─────────────
   الموجات تظهر وتختفي بنابض حسب المستوى، وعند الصفر تنرسم شرطة قطرية */

@Composable
private fun SpeakerIcon(level: Float, tint: Color, modifier: Modifier = Modifier) {
    val spec = spring<Float>(0.55f, 420f)
    val a1 by animateFloatAsState(if (level > 0.001f) 1f else 0f, spec, label = "w1")
    val a2 by animateFloatAsState(if (level > 0.34f) 1f else 0f, spec, label = "w2")
    val a3 by animateFloatAsState(if (level > 0.67f) 1f else 0f, spec, label = "w3")
    val slash by animateFloatAsState(if (level <= 0.001f) 1f else 0f, spring<Float>(0.7f, 380f), label = "slash")

    Canvas(modifier) {
        val u = size.minDimension / 24f
        val body = Path().apply {
            moveTo(3f * u, 9.2f * u)
            lineTo(6.8f * u, 9.2f * u)
            lineTo(11.6f * u, 5f * u)
            lineTo(11.6f * u, 19f * u)
            lineTo(6.8f * u, 14.8f * u)
            lineTo(3f * u, 14.8f * u)
            close()
        }
        drawPath(body, tint)
        drawPath(body, tint, style = Stroke(width = 1.6f * u, join = StrokeJoin.Round))

        val cx = 11.8f * u
        val cy = 12f * u
        fun arc(r: Float, a: Float) {
            if (a <= 0.01f) return
            val rr = r * u * (0.8f + 0.2f * a)
            drawArc(
                color = tint,
                startAngle = -44f,
                sweepAngle = 88f,
                useCenter = false,
                topLeft = Offset(cx - rr, cy - rr),
                size = Size(rr * 2f, rr * 2f),
                alpha = a.coerceIn(0f, 1f),
                style = Stroke(width = 2.1f * u, cap = StrokeCap.Round),
            )
        }
        arc(4.6f, a1)
        arc(7.8f, a2)
        arc(11f, a3)

        if (slash > 0.01f) {
            val s0 = Offset(4.5f * u, 4.5f * u)
            val s1 = Offset(19.5f * u, 19.5f * u)
            val e = Offset(s0.x + (s1.x - s0.x) * slash, s0.y + (s1.y - s0.y) * slash)
            drawLine(tint, s0, e, strokeWidth = 2.1f * u, cap = StrokeCap.Round)
        }
    }
}

/* ───────────── ظهور متتابع بنابض ─────────────
   دخول: نابض فيه ارتداد خفيف (مثل iOS) / خروج: نابض متخامد بدون ارتداد */

@Composable
private fun PopIn(
    visible: Boolean, enterDelay: Int, exitDelay: Int, closeMs: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // سرعة الخروج: نقيس على 380ms (الافتراضي). المدة أقصر = نابض أقوى وتأخير أقل، والعكس
    val k = closeMs / 380f
    val p = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            if (enterDelay > 0) delay(enterDelay.toLong())
            p.animateTo(1f, spring(0.68f, 300f))
        } else {
            if (exitDelay > 0) delay((exitDelay * k).toLong())
            p.animateTo(0f, spring(1f, (700f / (k * k)).coerceIn(15f, 20000f)))
        }
    }
    Box(
        modifier.graphicsLayer {
            val v = p.value
            val s = 0.55f + 0.45f * v
            scaleX = s; scaleY = s
            translationX = (1f - v) * 56.dp.toPx()
            alpha = (v * 1.6f).coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(1f, 0.5f)
        }
    ) { content() }
}

/* ───────────── اللوحة ───────────── */

@Composable
fun VolumeOverlay(
    ctrl: VolumeController,
    /** تفعيل/تعطيل نافذة اللمس الصغيرة (نعطّلها أثناء الإغلاق) */
    onTouchable: (Boolean) -> Unit = {},
    /** مكان اللوحة بالشاشة: الخدمة تحط عليه نافذة اللمس الصغيرة، وكل شي برّاها يمر للتطبيق اللي تحت طبيعي */
    onPanelBounds: (Rect) -> Unit = {},
    onClosed: () -> Unit,
) {
    // نسخة من الشاشة خلف اللوحة (مخفية، بس الزجاج يقرأ منها بنفس الإحداثيات بالضبط)
    val backdrop = rememberLayerBackdrop()

    var expanded by remember { mutableStateOf(false) }
    var hiding by remember { mutableStateOf(false) }
    var forceClose by remember { mutableStateOf(false) }
    // «الإمساك»: الشريط يبدأ رفيع مثل ايفون، ولمسة وحدة عليه تكبّره لحجمه الطبيعي (ويبقى لين تنسكّر اللوحة)
    var grabbed by remember { mutableStateOf(false) }
    var fullBounds by remember { mutableStateOf<Rect?>(null) }
    var sliderBounds by remember { mutableStateOf<Rect?>(null) }
    var swipeRaw by remember { mutableFloatStateOf(0f) }
    var swipeX by remember { mutableFloatStateOf(0f) }
    val pulse = remember { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 70.dp.toPx() }

    val visible = ctrl.active && !hiding

    // نافذة اللمس: وهو رفيع تغطي منطقة الشريط بس (حتى ما تبلع لمس التطبيق اللي تحته)، وبعد التكبير تغطي اللوحة كلها
    LaunchedEffect(grabbed, expanded, fullBounds, sliderBounds) {
        val b = if (!expanded && !grabbed) sliderBounds ?: fullBounds else fullBounds
        b?.let(onPanelBounds)
    }

    // فتح + إغلاق تلقائي إذا انتركت بدون لمس (أي لمس أو زر صوت يعيد العد)
    LaunchedEffect(ctrl.active, ctrl.interaction, expanded, grabbed, ctrl.dragging, forceClose) {
        if (!ctrl.active) { hiding = false; grabbed = false; return@LaunchedEffect }
        hiding = false
        onTouchable(!forceClose)
        if (!forceClose && ctrl.dragging) return@LaunchedEffect
        if (!forceClose) delay(if (expanded || grabbed) 4000L else 2500L)
        onTouchable(false)   // بدأ الإغلاق: اللمس يمر للتطبيق اللي تحت
        hiding = true
        // مدة الأنيميشن من سلايدر التطبيق
        delay(ctrl.closeDurationMs().toLong() + 40L)
        expanded = false; grabbed = false; forceClose = false; swipeRaw = 0f; swipeX = 0f
        onClosed()
    }

    // لمسة برّه اللوحة (مثلاً سحب للصفحة 2): نسكّر اللوحة، واللمسة نفسها تكمل للتطبيق اللي تحت بدون ما نلمسها
    LaunchedEffect(ctrl.closeRequest) {
        if (ctrl.closeRequest > 0 && ctrl.active) forceClose = true
    }

    // نبضة خفيفة لما تضغط زر صوت واللوحة ظاهرة
    LaunchedEffect(ctrl.keyPulse) {
        if (ctrl.active && ctrl.keyPulse > 0) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, spring(0.4f, 320f))
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
        LocalGlassStyle provides ctrl.style,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.001f }
                    .layerBackdrop(backdrop)
            ) {
                val s = ctrl.shot
                if (s != null) {
                    Image(
                        bitmap = s, contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF2B2B30)))
                }
            }

            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    // نقيس مكان اللوحة قبل أي تحويل (سحب/نبضة) حتى نافذة اللمس ما تتحرك وسط السحب
                    .onGloballyPositioned { fullBounds = it.boundsInWindow() }
            ) {
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = swipeX
                        val ps = 1f + 0.03f * pulse.value
                        scaleX = ps; scaleY = ps
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    }
                    // اسحب اللوحة لليمين حتى تسكّرها (مع مقاومة مطّاطية لليسار)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { dx ->
                            swipeRaw += dx
                            swipeX = if (swipeRaw < 0f) swipeRaw * 0.22f else swipeRaw
                        },
                        onDragStarted = { ctrl.dragging = true },
                        onDragStopped = { velocity ->
                            ctrl.dragging = false
                            if (swipeRaw > dismissPx || velocity > 1500f) {
                                forceClose = true
                            } else {
                                animate(swipeX, 0f, animationSpec = spring(0.62f, 380f)) { v, _ ->
                                    swipeX = v
                                    swipeRaw = v
                                }
                            }
                        },
                    )
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.changes.any { it.changedToDown() || it.changedToUp() }) ctrl.bump()
                            }
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        val origin = TransformOrigin(1f, 0.5f)
                        (fadeIn(tween(240)) +
                            scaleIn(spring(0.72f, 300f), initialScale = 0.55f, transformOrigin = origin)) togetherWith
                            (fadeOut(tween(140)) +
                                scaleOut(spring(1f, 520f), targetScale = 0.55f, transformOrigin = origin)) using
                            SizeTransform(clip = false)
                    },
                    label = "morph",
                ) { isExpanded ->
                    if (isExpanded) PopIn(visible, 0, 0, ctrl.closeDurationMs()) { ExpandedCard(backdrop, ctrl) }
                    else CollapsedColumn(
                        backdrop, ctrl, visible,
                        grabbed = grabbed,
                        onGrab = { grabbed = true },
                        onSliderBounds = { sliderBounds = it },
                    ) { expanded = true }
                }
            }
            }
        }
    }
}

/* ───────────── المصغّر ───────────── */

@Composable
private fun CollapsedColumn(
    backdrop: Backdrop,
    ctrl: VolumeController,
    visible: Boolean,
    grabbed: Boolean,
    onGrab: () -> Unit,
    onSliderBounds: (Rect) -> Unit,
    onMore: () -> Unit,
) {
    val stream = AudioManager.STREAM_MUSIC
    val level = ctrl.levels[stream] ?: 0f
    val st = LocalGlassStyle.current

    // أثناء السحب: السلايدر «يلتصق» بزر الرنين عن طريق جسر سائل بينهم
    val joinState = animateFloatAsState(if (ctrl.dragging) 1f else 0f, spring(0.5f, 340f), label = "join")

    // 0 = رفيع (مثل ايفون)، 1 = الحجم الطبيعي
    val expand by animateFloatAsState(if (grabbed) 1f else 0f, spring(0.64f, 360f), label = "expand")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PopIn(visible, 0, 90, ctrl.closeDurationMs(), Modifier.onGloballyPositioned { onSliderBounds(it.boundsInWindow()) }) {
            GlassSlider(
                backdrop, level,
                onValueChange = { ctrl.setLevel(stream, it) },
                onDragging = { ctrl.dragging = it },
                width = 60.dp, height = 180.dp,
                kick = ctrl.edgeKick, kickDir = ctrl.edgeDir,
                icon = { tint -> SpeakerIcon(level, tint, Modifier.fillMaxSize()) },
                joinBottom = { joinState.value },
                expand = expand,
                onGrab = onGrab,
            )
        }
        StickyBridge(gap = 12.dp, width = 60.dp, color = st.rimColor, join = { joinState.value })
        // الأزرار تنحجز مكانها دائماً (حتى الشريط ما يتحرك) وتظهر بس بعد ما يتكبّر الشريط
        Box(Modifier.size(52.dp)) {
            if (grabbed) PopIn(visible, 45, 45, ctrl.closeDurationMs()) {
                // الزر ينسحب شوية باتجاه السلايدر وقت الالتصاق
                Box(Modifier.graphicsLayer { translationY = -3.dp.toPx() * joinState.value }) {
                    RingerButton(backdrop, ctrl.ringer, joinTop = { joinState.value }) { ctrl.cycleRinger() }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(52.dp)) {
            if (grabbed) PopIn(visible, 90, 0, ctrl.closeDurationMs()) {
                GlassButton(backdrop, onClick = onMore) {
                    Icon(Icons.Rounded.MoreHoriz, null, tint = st.btnIcon, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/* ───────────── الجسر السائل ─────────────
   يرسم «رقبة» زجاجية منحنية بين السلايدر وزر الرنين (مثل التحام قطرتين).
   الأطراف تتلاشى بنعومة حتى تذوب داخل الشكلين، والوسط يسمك مع السحب. */

private val BRIDGE_OVERLAP = 14.dp

@Composable
private fun StickyBridge(gap: Dp, width: Dp, color: Color, join: () -> Float) {
    Box(Modifier.size(width, gap), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(width + 24.dp, gap + BRIDGE_OVERLAP * 2)
                .drawBehind {
                    val j = join().coerceIn(0f, 1f)
                    if (j <= 0.01f) return@drawBehind
                    val ov = BRIDGE_OVERLAP.toPx()
                    val cx = size.width / 2f
                    // حافة السلايدر السفلية (بعد ما يكبر وقت اللمس) وحافة الزر العلوية (بعد ما ينسحب)
                    val yS = ov + 4.5.dp.toPx() * j
                    val yR = ov + gap.toPx() - 3.dp.toPx() * j
                    val yT = yS - 7.dp.toPx()
                    val yB = yR + 7.dp.toPx()
                    val yC = (yT + yB) / 2f
                    val hh = (yB - yT) / 2f
                    val hwT = 16.dp.toPx()
                    val hwB = 16.dp.toPx()
                    val hwM = (3f + 7f * j).dp.toPx()   // سمك الرقبة بالنص

                    val path = Path().apply {
                        moveTo(cx - hwT, yT)
                        cubicTo(cx - hwT, yT + hh * 0.6f, cx - hwM, yC - hh * 0.5f, cx - hwM, yC)
                        cubicTo(cx - hwM, yC + hh * 0.5f, cx - hwB, yB - hh * 0.6f, cx - hwB, yB)
                        lineTo(cx + hwB, yB)
                        cubicTo(cx + hwB, yB - hh * 0.6f, cx + hwM, yC + hh * 0.5f, cx + hwM, yC)
                        cubicTo(cx + hwM, yC - hh * 0.5f, cx + hwT, yT + hh * 0.6f, cx + hwT, yT)
                        close()
                    }
                    fun fade(a: Float) = Brush.verticalGradient(
                        0f to color.mulAlpha(0f),
                        0.22f to color.mulAlpha(a),
                        0.78f to color.mulAlpha(a),
                        1f to color.mulAlpha(0f),
                        startY = yT,
                        endY = yB,
                    )
                    drawPath(path, fade(0.14f * j))
                    drawPath(path, fade(0.6f * j), style = Stroke(width = 1.4.dp.toPx()))
                }
        )
    }
}

/* ───────────── زر الرنين: أنيميشن تبديل الحالة ───────────── */

@Composable
private fun RingerButton(backdrop: Backdrop, mode: Int, joinTop: () -> Float = { 0f }, onClick: () -> Unit) {
    // صامت = الزر يتحول لأبيض والأيقونة غامقة (بنابض)
    val silent by animateFloatAsState(
        if (mode == AudioManager.RINGER_MODE_SILENT) 1f else 0f, spring(0.8f, 380f), label = "silent",
    )
    val st = LocalGlassStyle.current
    GlassButton(backdrop, onClick = onClick, silent = silent, joinTop = joinTop) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 40)) + scaleIn(spring(0.55f, 420f), initialScale = 0.2f)) togetherWith
                    (fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.2f))
            },
            label = "ringerIcon",
        ) { m ->
            // الأيقونة تدور وتدخل (مثل هزّة الجرس)
            val rot by transition.animateFloat(
                transitionSpec = { spring(0.5f, 260f) }, label = "rot",
            ) { st ->
                when (st) {
                    EnterExitState.PreEnter -> -70f
                    EnterExitState.Visible -> 0f
                    EnterExitState.PostExit -> 70f
                }
            }
            Icon(
                ringerIcon(m), null,
                tint = lerp(st.btnIcon, st.silentIcon, silent),
                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rot },
            )
        }
    }
}

/* ───────────── الموسّع ───────────── */

@Composable
private fun ExpandedCard(backdrop: Backdrop, ctrl: VolumeController) {
    val st = LocalGlassStyle.current
    Column(
        Modifier
            .barGlass(backdrop, RoundedCornerShape(36.dp), corner = 36.dp, lensScale = 1.28f)
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Text("الصوت", color = st.labelColor, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ctrl.streams.forEachIndexed { i, stream ->
                val level = ctrl.levels[stream] ?: 0f
                val isMusic = stream == AudioManager.STREAM_MUSIC
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassSlider(
                        backdrop, level,
                        onValueChange = { ctrl.setLevel(stream, it) },
                        onDragging = { ctrl.dragging = it },
                        width = 48.dp, height = 180.dp,
                        kick = if (isMusic) ctrl.edgeKick else 0,
                        kickDir = ctrl.edgeDir,
                        riseDelayMs = 80 + i * 70,   // كل سلايدر يعبّي من تحت بالتتابع
                    )
                    Spacer(Modifier.height(12.dp))
                    if (isMusic) {
                        SpeakerIcon(level, st.iconOnGlass, Modifier.size(24.dp))
                    } else {
                        Icon(streamIcon(stream, level), null, tint = st.iconOnGlass, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

/* ───────────── زر زجاجي ───────────── */

@Composable
private fun GlassButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    silent: Float = 0f,          // 0..1: زر الصامت يتحول للون «زر الصامت»
    joinTop: () -> Float = { 0f },
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val st = LocalGlassStyle.current
    val pf by animateFloatAsState(if (pressed) 1f else 0f, spring(0.5f, 480f), label = "btnPress")
    val base = st.btnTint.copy(alpha = (st.btnTintAlpha + 0.14f * pf).coerceIn(0f, 0.95f))
    val silentColor = st.silentFill.copy(alpha = (0.86f + 0.14f * pf).coerceIn(0f, 0.95f))
    val tint = lerp(base, silentColor, silent.coerceIn(0f, 1f))
    Box(
        Modifier
            .size(52.dp)
            .graphicsLayer {
                val s = 1f + 0.14f * pf
                scaleX = s; scaleY = s
            }
            .btnGlass(backdrop, CircleShape, tint, boost = { pf }, joinTop = joinTop)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
                )
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/* ───────────── سلايدر زجاجي ─────────────
   - الضغط على أي مكان: التعبئة تقفز لذاك المكان بنابض جميل
   - بعدها السحب يتبع إصبعك مباشرة، مع نقرات اهتزاز خفيفة كل درجة
   - يتوسّع والعدسة تقوى أثناء اللمس، ويتمطّط عند الحد (مثل المطاط)
   - سطح التعبئة «سائل»: ينحني ويتمايل لما تتغيّر القيمة بسرعة
   - الأيقونة تنقلب أبيض/غامق بنعومة حسب مستوى التعبئة */

@Composable
fun GlassSlider(
    backdrop: Backdrop,
    value: Float,
    onValueChange: (Float) -> Unit,
    onDragging: (Boolean) -> Unit,
    width: Dp,
    height: Dp,
    kick: Int = 0,
    kickDir: Float = 0f,
    riseDelayMs: Int = -1,
    icon: (@Composable (Color) -> Unit)? = null,
    joinBottom: () -> Float = { 0f },
    /** 0 = رفيع وصغير (مثل شريط ايفون)، 1 = الحجم الطبيعي */
    expand: Float = 1f,
    /** يُستدعى لما تلمس الشريط وهو رفيع (حتى يتكبّر) */
    onGrab: (() -> Unit)? = null,
) {
    val expandState = rememberUpdatedState(expand)
    val valueState = rememberUpdatedState(value)
    var dragging by remember { mutableStateOf(false) }
    var moved by remember { mutableStateOf(false) }
    var overshoot by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val st = LocalGlassStyle.current

    val grow by animateFloatAsState(if (dragging) 1f else 0f, spring(0.5f, 340f), label = "grow")
    val stretch by animateFloatAsState(overshoot, spring(0.4f, 300f), label = "stretch")
    // قبل السحب: نابض ناعم (تأثير القفزة). بعد ما يبدأ السحب: يلحق الإصبع تقريباً فوراً
    val fill by animateFloatAsState(
        targetValue = value,
        animationSpec = if (moved) spring(1f, 2500f) else spring(0.82f, 420f),
        label = "fill",
    )
    // نابض رخو جداً: الفرق بينه وبين القيمة يحرّك سطح السائل
    val soft by animateFloatAsState(value, spring(0.35f, 160f), label = "soft")

    // دخول التعبئة من تحت (للبطاقة الموسّعة)
    var risen by remember { mutableStateOf(riseDelayMs < 0) }
    LaunchedEffect(Unit) {
        if (riseDelayMs >= 0) { delay(riseDelayMs.toLong()); risen = true }
    }
    val rise by animateFloatAsState(if (risen) 1f else 0f, spring(0.72f, 170f), label = "rise")

    // ارتداد مطّاطي لما تضغط زر الصوت وانت بالحد الأعلى/الأدنى
    var seenKick by remember { mutableIntStateOf(kick) }
    LaunchedEffect(kick) {
        if (kick != seenKick) {
            seenKick = kick
            if (!dragging) {
                overshoot = kickDir * 0.32f
                delay(120)
                overshoot = 0f
            }
        }
    }

    val shape = RoundedCornerShape(percent = 50)
    // الصندوق الخارجي = منطقة اللمس الكاملة (حتى وهو رفيع تكدر تمسكه بسهولة)، والداخلي = الرسم اللي يصغر ويكبر
    Box(
        Modifier
            .size(width, height)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val h = size.height.toFloat()
                    val thinStart = expandState.value < 0.5f
                    val startValue = valueState.value
                    dragging = true; moved = false; onDragging(true)
                    if (thinStart) onGrab?.invoke()
                    val first = (1f - down.position.y / h).coerceIn(0f, 1f)
                    var lastStep = ((if (thinStart) startValue else first) * 16f).toInt()
                    // وهو رفيع: اللمسة الأولى بس تكبّره بدون ما تقفز القيمة، والسحب بعدها نسبي
                    if (!thinStart) onValueChange(first)

                    do {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.first()
                        if (c.pressed) {
                            if (!moved && abs(c.position.y - down.position.y) > viewConfiguration.touchSlop) moved = true
                            if (!thinStart || moved) {
                                val raw = if (thinStart) startValue + (down.position.y - c.position.y) / h
                                else 1f - c.position.y / h
                                val v = raw.coerceIn(0f, 1f)
                                onValueChange(v)
                                val step = (v * 16f).toInt()
                                if (step != lastStep) {
                                    lastStep = step
                                    haptic.performHapticFeedback(
                                        if (v <= 0f || v >= 1f) HapticFeedbackType.LongPress
                                        else HapticFeedbackType.TextHandleMove
                                    )
                                }
                                overshoot = when {
                                    raw > 1f -> (raw - 1f).coerceAtMost(0.5f)
                                    raw < 0f -> raw.coerceAtLeast(-0.5f)
                                    else -> 0f
                                }
                            }
                            c.consume()
                        }
                    } while (ev.changes.any { it.pressed })

                    dragging = false; moved = false; overshoot = 0f; onDragging(false)
                }
            }
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // الحجم الرفيع: عرض ~24% وطول ~62%، ملتصق بحافة الشاشة
                val ex = 0.24f + 0.76f * expand
                val ey = 0.62f + 0.38f * expand
                scaleX = ex * (1f + 0.15f * grow) * (1f - abs(stretch) * 0.07f)
                scaleY = ey * ((1f + 0.05f * grow) + abs(stretch) * 0.28f)
                translationX = (1f - expand) * (size.width * (1f - 0.24f) / 2f + 4.dp.toPx())
                transformOrigin = TransformOrigin(0.5f, if (stretch > 0f) 1f else if (stretch < 0f) 0f else 0.5f)
            }
            .barGlass(backdrop, shape, boost = { grow }, joinBottom = joinBottom)
            .clip(shape)
    ) {
        // التعبئة السائلة
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val f = (fill * rise).coerceIn(0f, 1f)
                    if (f > 0.002f) {
                        val w = size.width
                        val h = size.height
                        val top = h * (1f - f)
                        val edge = if (f < 0.12f) f / 0.12f else 1f
                        val bulge = ((value - soft) * h * 0.45f).coerceIn(-h * 0.05f, h * 0.05f) * edge
                        val path = Path().apply {
                            moveTo(0f, h)
                            lineTo(0f, top)
                            cubicTo(
                                w * 0.28f, top - bulge * 1.33f,
                                w * 0.72f, top - bulge * 1.33f,
                                w, top,
                            )
                            lineTo(w, h)
                            close()
                        }
                        drawPath(
                            path,
                            Brush.verticalGradient(
                                listOf(st.barFill.mulAlpha(0.97f), st.barFill.mulAlpha(0.88f)),
                                startY = top,
                                endY = h,
                            ),
                        )
                    }
                }
        )
        if (icon != null) {
            val iconDark by remember { derivedStateOf { fill * rise > 0.17f } }
            val tint by animateColorAsState(
                if (iconDark) st.iconOnFill else st.iconOnGlass,
                tween(180), label = "iconTint",
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .size(22.dp)
                    .graphicsLayer { alpha = ((expand - 0.4f) / 0.6f).coerceIn(0f, 1f) }
            ) { icon(tint) }
        }
    }
    }
}

/* ───────────── معاينة حيّة للإعدادات ─────────────
   نفس مكوّنات اللوحة الحقيقية (سلايدر + زر رنين + زر ⋯ + البطاقة الموسّعة)
   فوق خلفية ملوّنة، حتى تشوف الضبابية والانكسار والألوان وقت ما تغيّرها. */

@Composable
fun GlassStylePreview(style: GlassStyle, modifier: Modifier = Modifier) {
    val pb = rememberLayerBackdrop()
    var music by remember { mutableFloatStateOf(0.6f) }
    var ring by remember { mutableFloatStateOf(0.35f) }
    var mode by remember { mutableIntStateOf(AudioManager.RINGER_MODE_NORMAL) }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
        LocalGlassStyle provides style,
    ) {
        Box(
            modifier
                .fillMaxWidth()
                .height(236.dp)
                .clip(RoundedCornerShape(28.dp))
        ) {
            // خلفية ملوّنة فيها خطوط ودوائر حتى الانكسار والضبابية يبانون بوضوح
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(pb)
                    .drawBehind {
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color(0xFF3A7BFF), Color(0xFFFF5C8A), Color(0xFFFFB84D)),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height),
                            )
                        )
                        val w = size.width
                        val h = size.height
                        drawCircle(Color(0xFF00E0B0), radius = h * 0.30f, center = Offset(w * 0.18f, h * 0.25f))
                        drawCircle(Color(0xFF6A3DFF), radius = h * 0.26f, center = Offset(w * 0.82f, h * 0.78f))
                        drawCircle(Color.White.copy(alpha = 0.9f), radius = h * 0.10f, center = Offset(w * 0.55f, h * 0.2f))
                        repeat(7) { i ->
                            drawRect(
                                Color.Black.copy(alpha = 0.22f),
                                topLeft = Offset(w * (0.08f + i * 0.14f), 0f),
                                size = Size(6.dp.toPx(), h),
                            )
                        }
                    }
            )

            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // المصغّر: سلايدر
                GlassSlider(
                    pb, music,
                    onValueChange = { music = it },
                    onDragging = {},
                    width = 56.dp, height = 150.dp,
                    icon = { tint -> SpeakerIcon(music, tint, Modifier.fillMaxSize()) },
                )
                // أزرار
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RingerButton(pb, mode) {
                        mode = when (mode) {
                            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                            else -> AudioManager.RINGER_MODE_NORMAL
                        }
                    }
                    GlassButton(pb, onClick = {}) {
                        Icon(Icons.Rounded.MoreHoriz, null, tint = style.btnIcon, modifier = Modifier.size(24.dp))
                    }
                }
                // الموسّع
                Column(
                    Modifier
                        .barGlass(pb, RoundedCornerShape(32.dp), corner = 32.dp, lensScale = 1.28f)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text("الصوت", color = style.labelColor, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlassSlider(pb, music, { music = it }, {}, width = 42.dp, height = 96.dp)
                            Spacer(Modifier.height(8.dp))
                            SpeakerIcon(music, style.iconOnGlass, Modifier.size(22.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlassSlider(pb, ring, { ring = it }, {}, width = 42.dp, height = 96.dp)
                            Spacer(Modifier.height(8.dp))
                            Icon(Icons.Rounded.Notifications, null, tint = style.iconOnGlass, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}
