package com.example.glassnav

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Home() }
            }
        }
    }
}

@Composable
private fun Home() {
    val ctx = LocalContext.current
    val sp = remember { Prefs.sp(ctx) }
    var bar by remember { mutableStateOf(sp.getBoolean(Prefs.BAR, true)) }
    var back by remember { mutableStateOf(sp.getBoolean(Prefs.BACK, true)) }
    var cover by remember { mutableStateOf(sp.getBoolean(Prefs.COVER, false)) }
    var white by remember { mutableStateOf(sp.getBoolean(Prefs.COVER_WHITE, false)) }
    var light by remember { mutableStateOf(sp.getBoolean(Prefs.EDGE_LIGHT, false)) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Glass Nav", style = MaterialTheme.typography.headlineLarge)

        GlassDemo()

        Button(
            onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Enable the service (Accessibility)") }

        SwitchRow("White home bar", bar) { bar = it; sp.edit().putBoolean(Prefs.BAR, it).apply() }
        SwitchRow("Back gesture from edges", back) { back = it; sp.edit().putBoolean(Prefs.BACK, it).apply() }
        SwitchRow("Cover the 3-button nav bar", cover) { cover = it; sp.edit().putBoolean(Prefs.COVER, it).apply() }
        SwitchRow("Cover colour: white (off = black)", white) { white = it; sp.edit().putBoolean(Prefs.COVER_WHITE, it).apply() }

        Text("Back gesture glass", style = MaterialTheme.typography.titleMedium)
        EdgePreview(light)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = !light,
                onClick = { light = false; sp.edit().putBoolean(Prefs.EDGE_LIGHT, false).apply() },
                label = { Text("Dark") }
            )
            FilterChip(
                selected = light,
                onClick = { light = true; sp.edit().putBoolean(Prefs.EDGE_LIGHT, true).apply() },
                label = { Text("Light") }
            )
        }

        Text(
            "Tip: set System > Navigation mode to 3-button, then turn on \"Cover the 3-button nav bar\".",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

/** Live Liquid Glass preview made with Backdrop 2.0.0 */
@Composable
private fun GlassDemo() {
    val backdrop = rememberLayerBackdrop()
    val t = rememberInfiniteTransition(label = "demo")
    val x by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "x"
    )

    Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(28.dp))) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(Brush.linearGradient(listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B))))
        ) {
            Box(
                Modifier.offset(x = (20 + 200 * x).dp, y = 20.dp).size(70.dp)
                    .clip(CircleShape).background(Color(0xFF00E5FF))
            )
            Box(
                Modifier.offset(x = (240 - 200 * x).dp, y = 110.dp).size(60.dp)
                    .clip(CircleShape).background(Color(0xFFFFEB3B))
            )
        }

        Box(
            Modifier
                .align(Alignment.Center)
                .width(220.dp)
                .height(72.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(16.dp.toPx(), 32.dp.toPx())
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.18f)) }
                ),
            contentAlignment = Alignment.Center
        ) { Text("Liquid Glass", color = Color.White) }
    }
}

/** Animated preview of the back-gesture bulge in the chosen style. */
@Composable
private fun EdgePreview(light: Boolean) {
    val t = rememberInfiniteTransition(label = "edge")
    val k by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "k"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B))))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val maxW = BulgeMaxW.toPx()
            withTransform({ scale(-1f, 1f, Offset(size.width / 2f, 0f)) }) {
                drawGlassBulge(light, maxW * k, size.height / 2f, BulgeHalfH.toPx() * 0.9f, k * 1.4f, 1f)
            }
        }
    }
}
