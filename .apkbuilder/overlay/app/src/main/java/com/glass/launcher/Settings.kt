package com.glass.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

class Pref<T>(initial: T, private val save: (T) -> Unit) {
    var value by mutableStateOf(initial)
        private set
    fun set(v: T) { value = v; save(v) }
}

/** All launcher settings, persisted in SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private fun int(k: String, d: Int) = Pref(sp.getInt(k, d)) { sp.edit().putInt(k, it).apply() }
    private fun bool(k: String, d: Boolean) = Pref(sp.getBoolean(k, d)) { sp.edit().putBoolean(k, it).apply() }
    private fun float(k: String, d: Float) = Pref(sp.getFloat(k, d)) { sp.edit().putFloat(k, it).apply() }

    val homeCols = int("homeCols", 4)
    val drawerCols = int("drawerCols", 4)
    val iconSize = int("iconSize", 56)
    val showLabels = bool("showLabels", true)
    val showClock = bool("showClock", true)
    val showWeather = bool("showWeather", true)
    val showSearch = bool("showSearch", true)
    val fahrenheit = bool("fahrenheit", false)
    val swipeDown = bool("swipeDown", true)
    val glassTint = float("glassTint", 0.16f)
    val initialized = bool("initialized", false)
}

@Composable
private fun Section(title: String) =
    Text(title, color = Color(0xFF9EC5FF), fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))

@Composable
private fun SwitchRow(title: String, pref: Pref<Boolean>) {
    Row(Modifier.fillMaxWidth().clickable { pref.set(!pref.value) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(pref.value, { pref.set(it) })
    }
}

@Composable
private fun StepperRow(title: String, pref: Pref<Int>, range: IntRange) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        TextButton({ if (pref.value > range.first) pref.set(pref.value - 1) }) { Text("−", color = Color.White, fontSize = 22.sp) }
        Text("${pref.value}", color = Color.White, fontSize = 16.sp)
        TextButton({ if (pref.value < range.last) pref.set(pref.value + 1) }) { Text("+", color = Color.White, fontSize = 22.sp) }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
fun SettingsScreen(
    prefs: Prefs, apps: List<AppInfo>, hidden: List<String>, onHidden: (List<String>) -> Unit,
    onResetHome: () -> Unit, backdrop: Backdrop, onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var showHidden by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().glass(backdrop, 0.dp).background(Color.Black.copy(alpha = 0.55f))
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("‹", color = Color.White, fontSize = 30.sp) }
            Text("الإعدادات", color = Color.White, fontSize = 24.sp)
        }

        Section("الشاشة الرئيسية")
        StepperRow("عدد الأعمدة", prefs.homeCols, 3..6)
        SliderRow("حجم الأيقونات", prefs.iconSize.value.toFloat(), 40f..72f) { prefs.iconSize.set(it.toInt()) }
        SwitchRow("إظهار أسماء التطبيقات", prefs.showLabels)
        SwitchRow("ودجت الساعة", prefs.showClock)
        SwitchRow("ودجت الطقس", prefs.showWeather)

        Section("مكتبة التطبيقات")
        StepperRow("عدد الأعمدة", prefs.drawerCols, 3..6)
        SwitchRow("شريط البحث", prefs.showSearch)

        Section("الطقس")
        SwitchRow("الفهرنهايت بدل السيليزية", prefs.fahrenheit)

        Section("المظهر")
        SliderRow("شفافية الزجاج", prefs.glassTint.value, 0f..0.5f) { prefs.glassTint.set(it) }

        Section("الإيماءات")
        SwitchRow("السحب للأسفل يفتح الإشعارات", prefs.swipeDown)

        Section("التطبيقات")
        TextButton({ showHidden = !showHidden }) {
            Text("التطبيقات المخفية (${hidden.size})", color = Color.White, fontSize = 16.sp)
        }
        if (showHidden) apps.forEach { app ->
            val isHidden = app.pkg in hidden
            Row(
                Modifier.fillMaxWidth().clickable { onHidden(if (isHidden) hidden - app.pkg else hidden + app.pkg) }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(app.icon, app.label, Modifier.size(32.dp))
                Text(app.label, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Checkbox(isHidden, null)
            }
        }

        Section("النظام")
        TextButton({ ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
            Text("تعيين كلانشر افتراضي", color = Color.White, fontSize = 16.sp)
        }
        TextButton(onResetHome) { Text("إعادة ضبط الشاشة الرئيسية والدوك", color = Color(0xFFFF8A80), fontSize = 16.sp) }
    }
}
