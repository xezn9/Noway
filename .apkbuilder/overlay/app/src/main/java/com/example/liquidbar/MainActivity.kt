package com.example.liquidbar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            DemoScreen()
        }
    }
}

private class Tab(val label: String, val icon: ImageVector, val palette: List<Color>)

private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()

private val Tabs = listOf(
    Tab(
        "Home",
        icon("home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z"),
        listOf(Color(0xFFFF5E62), Color(0xFFFF9966), Color(0xFFFFD93D))
    ),
    Tab(
        "Search",
        icon(
            "search",
            "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"
        ),
        listOf(Color(0xFF00C6FF), Color(0xFF0072FF), Color(0xFF8E2DE2))
    ),
    Tab(
        "Favorites",
        icon(
            "favorite",
            "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"
        ),
        listOf(Color(0xFFF953C6), Color(0xFFB91D73), Color(0xFFFF6A88))
    ),
    Tab(
        "Profile",
        icon(
            "person",
            "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"
        ),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D), Color(0xFFF2FC5E))
    )
)

@Composable
private fun DemoScreen() {
    val isDark = isSystemInDarkTheme()
    val contentColor = if (isDark) Color.White else Color.Black
    val iconTint = ColorFilter.tint(contentColor)

    val backdrop = rememberLayerBackdrop()
    var selected by remember { mutableIntStateOf(0) }
    val painters = Tabs.map { rememberVectorPainter(it.icon) }

    Box(Modifier.fillMaxSize()) {
        // Everything the glass can "see" lives inside layerBackdrop. The bar itself is outside it.
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            Wallpaper(selected)
        }

        LiquidBottomTabs(
            selectedTabIndex = { selected },
            onTabSelected = { selected = it },
            backdrop = backdrop,
            tabsCount = Tabs.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 36.dp, vertical = 16.dp)
        ) {
            Tabs.forEachIndexed { index, tab ->
                LiquidBottomTab({ selected = index }) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .paint(painters[index], colorFilter = iconTint)
                    )
                    BasicText(
                        tab.label,
                        style = TextStyle(contentColor, 12.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Wallpaper(selected: Int) {
    val palette = Tabs[selected].palette
    val c0 by animateColorAsState(palette[0], tween(600))
    val c1 by animateColorAsState(palette[1], tween(600))
    val c2 by animateColorAsState(palette[2], tween(600))

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(c0, c1, c2)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(0.25f), size.minDimension * 0.45f, Offset(size.width * 0.85f, size.height * 0.2f))
            drawCircle(Color.Black.copy(0.18f), size.minDimension * 0.4f, Offset(size.width * 0.1f, size.height * 0.55f))
            drawCircle(Color.White.copy(0.2f), size.minDimension * 0.3f, Offset(size.width * 0.7f, size.height * 0.85f))
        }

        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 16.dp, bottom = 180.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
        ) {
            item {
                BasicText(
                    Tabs[selected].label,
                    style = TextStyle(Color.White, 34.sp, FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items((1..30).toList()) { n ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Color.hsv(((n * 47 + selected * 70) % 360).toFloat(), 0.7f, 0.95f),
                            RoundedCornerShape(28.dp)
                        )
                        .padding(20.dp)
                ) {
                    BasicText(
                        "Card $n",
                        style = TextStyle(Color.White, 20.sp, FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}
