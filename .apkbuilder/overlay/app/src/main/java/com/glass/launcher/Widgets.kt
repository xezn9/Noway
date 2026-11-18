package com.glass.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ClockWidget(backdrop: Backdrop, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            battery = (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(1000)
        }
    }
    Column(modifier.glass(backdrop).padding(20.dp)) {
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 56.sp)
        Text(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())), color = Color.White.copy(0.85f), fontSize = 16.sp)
        Text("🔋 $battery%", color = Color.White.copy(0.85f), fontSize = 14.sp)
    }
}

private fun weatherText(code: Int) = when (code) {
    0 -> "☀️ صافي"; 1, 2, 3 -> "⛅ غائم جزئياً"; 45, 48 -> "🌫️ ضباب"
    in 51..67, in 80..82 -> "🌧️ مطر"; in 71..77 -> "❄️ ثلج"; in 95..99 -> "⛈️ عاصفة"
    else -> "🌡️"
}

@Composable
fun WeatherWidget(backdrop: Backdrop, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("...") }
    var granted by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    LaunchedEffect(granted) {
        while (true) {
            text = withContext(Dispatchers.IO) {
                try {
                    var lat = 33.31; var lon = 44.36 // Baghdad fallback
                    if (granted) {
                        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        lm.getProviders(true).firstNotNullOfOrNull { lm.getLastKnownLocation(it) }
                            ?.let { lat = it.latitude; lon = it.longitude }
                    }
                    val json = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code").readText())
                    val cur = json.getJSONObject("current")
                    "${cur.getDouble("temperature_2m").toInt()}°  ${weatherText(cur.getInt("weather_code"))}"
                } catch (e: Exception) { "—" }
            }
            delay(30 * 60 * 1000L)
        }
    }
    Box(modifier.glass(backdrop).padding(20.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 22.sp)
    }
}
