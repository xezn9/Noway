package com.example.glassvolume

import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidDialog
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidToggle
import kotlin.math.roundToInt

private const val MAX_DOWN_DELAY = 450f

@Composable
fun SettingsScreen(prefs: SharedPreferences) {
    val ctx = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    var dark by remember {
        mutableStateOf(
            if (prefs.contains(Prefs.KEY_APP_DARK)) prefs.getBoolean(Prefs.KEY_APP_DARK, false) else systemDark
        )
    }
    var style by remember { mutableStateOf(GlassStyle.load(prefs)) }
    var delayMs by remember {
        mutableIntStateOf(prefs.getInt(Prefs.KEY_DOWN_DELAY, Prefs.DEFAULT_DOWN_DELAY))
    }
    // سرعة الإغلاق: 0 = أسرع، 1 = أبطأ
    var closeFrac by remember {
        mutableFloatStateOf(
            Prefs.closeDelayToFraction(prefs.getInt(Prefs.KEY_CLOSE_DELAY, Prefs.DEFAULT_CLOSE_DELAY))
        )
    }
    var tab by remember { mutableIntStateOf(0) }
    var showReset by remember { mutableStateOf(false) }

    // نافذة الألوان
    var editing by remember { mutableStateOf<ColorSlot?>(null) }
    var lastSlot by remember { mutableStateOf(ColorSlot.BarFill) }
    var session by remember { mutableIntStateOf(0) }
    val shownSlot = editing ?: lastSlot

    val updateStyle: (GlassStyle) -> Unit = { s ->
        style = s
        s.save(prefs)
    }

    // أيقونات شريط الحالة تتبع الوضع (داكن/فاتح)
    val activity = ctx as? ComponentActivity
    SideEffect {
        activity?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, w.decorView)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    // wallBackdrop: الخلفية بس (للبطاقات والأزرار والشريط السفلي)
    // screenBackdrop: الشاشة كلها (للنوافذ المنبثقة، لازم تنحط برّا الصندوق اللي يسجّله)
    val wallBackdrop = rememberLayerBackdrop()
    val screenBackdrop = rememberLayerBackdrop()
    val ink = inkColor(dark)

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(screenBackdrop)) {
            Wallpaper(dark, Modifier.fillMaxSize().layerBackdrop(wallBackdrop))

            Column(
                Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
            ) {
                // كل صفحة تاخذ الشاشة كاملة (بدون شريط حالة ولا معاينة مثبّتة)، والمعاينة صارت أول عنصر داخل الصفحة
                Box(Modifier.weight(1f)) {
                    Crossfade(tab, label = "page") { t ->
                        when (t) {
                            0 -> HomePage(wallBackdrop, dark)
                            1 -> GlassPage(style, updateStyle, wallBackdrop, dark)
                            2 -> ColorsPage(style, wallBackdrop, dark) { slot ->
                                session++
                                lastSlot = slot
                                editing = slot
                            }
                            else -> AdvancedPage(
                                dark = dark,
                                onDark = {
                                    dark = it
                                    prefs.edit().putBoolean(Prefs.KEY_APP_DARK, it).apply()
                                },
                                delayMs = delayMs,
                                onDelay = {
                                    delayMs = it
                                    prefs.edit().putInt(Prefs.KEY_DOWN_DELAY, it).apply()
                                },
                                closeFrac = closeFrac,
                                onClose = {
                                    closeFrac = it
                                    prefs.edit().putInt(Prefs.KEY_CLOSE_DELAY, Prefs.fractionToCloseDelay(it)).apply()
                                },
                                onReset = { showReset = true },
                                backdrop = wallBackdrop,
                            )
                        }
                    }
                }
            }

            // الشريط السفلي (Liquid Bottom Tabs)
            val tabs = listOf(
                Icons.Rounded.Home to "الرئيسية",
                Icons.Rounded.BlurOn to "الزجاج",
                Icons.Rounded.Palette to "الألوان",
                Icons.Rounded.Tune to "متقدم",
            )
            LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { tab = it },
                backdrop = wallBackdrop,
                tabsCount = tabs.size,
                darkTheme = dark,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                tabs.forEachIndexed { i, (icon, label) ->
                    LiquidBottomTab(onClick = { tab = i }) {
                        Icon(icon, contentDescription = null, tint = ink)
                        Text(label, color = ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // النوافذ المنبثقة: برّا الصندوق اللي يسجّل الشاشة
        LiquidDialog(
            visible = showReset,
            onDismiss = { showReset = false },
            backdrop = screenBackdrop,
            dark = dark,
            title = "إعادة الضبط؟",
            message = "كل خيارات الزجاج والألوان راح ترجع للقيم الافتراضية.",
            confirmText = "إعادة ضبط",
            dismissText = "إلغاء",
            confirmTint = Color(0xFFFF3B30),
            onConfirm = {
                updateStyle(GlassStyle.Default)
                showReset = false
            },
        )

        GlassColorDialog(
            visible = editing != null,
            session = session,
            title = shownSlot.label,
            color = shownSlot.get(style),
            defaultColor = shownSlot.get(GlassStyle.Default),
            allowAlpha = shownSlot.allowAlpha,
            onColor = { c -> updateStyle(shownSlot.set(style, c)) },
            onDismiss = { editing = null },
            backdrop = screenBackdrop,
            dark = dark,
        )
    }
}

/* ───────────── الخلفية ───────────── */

@Composable
private fun Wallpaper(dark: Boolean, modifier: Modifier) {
    val colors =
        if (dark) listOf(Color(0xFF0B1026), Color(0xFF2A1250), Color(0xFF08323A))
        else listOf(Color(0xFFBFD9FF), Color(0xFFFFC9DE), Color(0xFFCFF5E3))
    val blobA = if (dark) Color(0xFF4F7CFF) else Color(0xFF7FB2FF)
    val blobB = if (dark) Color(0xFFB04DFF) else Color(0xFFFF8FB8)
    val blobC = if (dark) Color(0xFF19D3B0) else Color(0xFF8FE8C2)
    Box(
        modifier
            .background(Brush.linearGradient(colors))
            .drawBehind {
                val w = size.width
                val h = size.height
                // بقع ملوّنة ناعمة حتى الزجاج يكون عنده شي يكسره
                drawCircle(blobA.copy(alpha = 0.55f), radius = w * 0.42f, center = Offset(w * 0.15f, h * 0.18f))
                drawCircle(blobB.copy(alpha = 0.50f), radius = w * 0.38f, center = Offset(w * 0.92f, h * 0.45f))
                drawCircle(blobC.copy(alpha = 0.50f), radius = w * 0.45f, center = Offset(w * 0.30f, h * 0.88f))
            }
    )
}

/* ───────────── الصفحات ───────────── */

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 128.dp),   // تحت: مكان الشريط السفلي
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun HomePage(backdrop: Backdrop, dark: Boolean) {
    val ctx = LocalContext.current
    val ink = inkColor(dark)
    PageColumn {
        Text("Glass Volume", color = ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        GlassCard(backdrop, dark) {
            Text(
                "1) فعّل خدمة «Glass Volume» من إعدادات الوصول. إذا النظام منعها: معلومات التطبيق ← ⋮ ← السماح بالإعدادات المقيّدة.",
                color = ink, fontSize = 15.sp,
            )
            LiquidButton(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                backdrop = backdrop,
                tint = AccentBlue,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("فتح إعدادات الوصول", color = Color.White, fontWeight = FontWeight.Medium) }
        }
        GlassCard(backdrop, dark) {
            Text("2) فعّل صلاحية «عدم الإزعاج» حتى يشتغل وضع الصامت.", color = ink, fontSize = 15.sp)
            LiquidButton(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                backdrop = backdrop,
                tint = AccentBlue,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("فتح صلاحية عدم الإزعاج", color = Color.White, fontWeight = FontWeight.Medium) }
        }
        GlassCard(backdrop, dark) {
            Text("3) اضغط زر الصوت الفيزيائي وراح تطلع اللوحة.", color = ink, fontSize = 15.sp)
            Text(
                "تغييراتك بتبويب «الزجاج» و«الألوان» تنطبق على اللوحة بالضغطة الجاية.",
                color = ink.copy(alpha = 0.65f), fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun GlassPage(
    style: GlassStyle,
    onStyle: (GlassStyle) -> Unit,
    backdrop: Backdrop,
    dark: Boolean,
) {
    PageColumn {
        GlassStylePreview(style, Modifier.fillMaxWidth())
        GroupTitle("الشريط (السلايدر)", dark)
        GlassCard(backdrop, dark) {
            SettingSlider("شدة الضبابية", pct(style.barBlur), { style.barBlur },
                { onStyle(style.copy(barBlur = it)) }, backdrop, dark)
            SettingSlider("شدة الانكسار", pct(style.barRefraction), { style.barRefraction },
                { onStyle(style.copy(barRefraction = it)) }, backdrop, dark)
            SettingSlider("شدة الشفافية", pct(style.barTransparency), { style.barTransparency },
                { onStyle(style.copy(barTransparency = it)) }, backdrop, dark)
            SettingSlider("لمعة الحافة", pct(style.barRimMul), { style.barRim },
                { onStyle(style.copy(barRim = it)) }, backdrop, dark)
        }

        GroupTitle("الأزرار", dark)
        GlassCard(backdrop, dark) {
            SettingSlider("شدة الضبابية", pct(style.btnBlur), { style.btnBlur },
                { onStyle(style.copy(btnBlur = it)) }, backdrop, dark)
            SettingSlider("شدة الانكسار", pct(style.btnRefraction), { style.btnRefraction },
                { onStyle(style.copy(btnRefraction = it)) }, backdrop, dark)
            SettingSlider("شدة الشفافية", pct(style.btnTransparency), { style.btnTransparency },
                { onStyle(style.copy(btnTransparency = it)) }, backdrop, dark)
            SettingSlider("لمعة الحافة", pct(style.btnRimMul), { style.btnRim },
                { onStyle(style.copy(btnRim = it)) }, backdrop, dark)
        }
    }
}

@Composable
private fun ColorsPage(
    style: GlassStyle,
    backdrop: Backdrop,
    dark: Boolean,
    onPick: (ColorSlot) -> Unit,
) {
    PageColumn {
        GlassStylePreview(style, Modifier.fillMaxWidth())
        listOf(0 to "الشريط", 1 to "الأزرار", 2 to "عام").forEach { (group, title) ->
            GroupTitle(title, dark)
            GlassCard(backdrop, dark) {
                ColorSlot.entries.filter { it.group == group }.forEach { slot ->
                    ColorRow(slot.label, slot.get(style), dark) { onPick(slot) }
                }
            }
        }
    }
}

@Composable
private fun AdvancedPage(
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    delayMs: Int,
    onDelay: (Int) -> Unit,
    closeFrac: Float,
    onClose: (Float) -> Unit,
    onReset: () -> Unit,
    backdrop: Backdrop,
) {
    val ink = inkColor(dark)
    val closeMs = Prefs.fractionToCloseDelay(closeFrac)
    PageColumn {
        GlassCard(backdrop, dark) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("الوضع الداكن", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                LiquidToggle(
                    selected = { dark },
                    onSelect = onDark,
                    backdrop = backdrop,
                    dark = dark,
                )
            }
        }

        GlassCard(backdrop, dark) {
            Text("تأخير ظهور اللوحة مع زر «خفض الصوت»: $delayMs ملي ثانية",
                color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            LiquidSlider(
                value = { delayMs / MAX_DOWN_DELAY },
                onValueChange = { onDelay((it * MAX_DOWN_DELAY).roundToInt()) },
                valueRange = 0f..1f,
                visibilityThreshold = 0.001f,
                backdrop = backdrop,
                dark = dark,
            )
            Text(
                "زر رفع الصوت يطلع فوراً بدون تأخير. زر خفض الصوت يتأخر شوية حتى ما تنلقط اللوحة بلقطة الشاشة (خفض الصوت + باور). قلّل الرقم حتى تطلع أسرع، وإذا شفت اللوحة بلقطة الشاشة كبّره.",
                color = ink.copy(alpha = 0.65f), fontSize = 13.sp,
            )
        }

        GlassCard(backdrop, dark) {
            Text("سرعة إغلاق اللوحة: $closeMs ملي ثانية",
                color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            LiquidSlider(
                value = { closeFrac },
                onValueChange = onClose,
                valueRange = 0f..1f,
                visibilityThreshold = 0.001f,
                backdrop = backdrop,
                dark = dark,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("أسرع", color = ink.copy(alpha = 0.7f), fontSize = 12.sp)
                Text("أبطأ", color = ink.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Text(
                "هذا يتحكم بسرعة أنيميشن إغلاق اللوحة بأي إغلاق: تلقائي، أو سحبها، أو لمس برّه اللوحة، أو سحب بين صفحات الشاشة الرئيسية. يمين = أسرع، يسار = أبطأ.",
                color = ink.copy(alpha = 0.65f), fontSize = 13.sp,
            )
        }

        LiquidButton(
            onClick = onReset,
            backdrop = backdrop,
            tint = Color(0xFFFF3B30),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("إعادة ضبط الزجاج والألوان", color = Color.White, fontWeight = FontWeight.Medium) }
    }
}
