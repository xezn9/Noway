package com.example.liquidtabs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { App() }
        }
    }
}

private val tabIcons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Person)
private val tabLabels = listOf("Home", "Explore", "Profile")

@Composable
private fun App() {
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val push = remember { PushController(scope) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var barBounds by remember { mutableStateOf(Rect.Zero) }

    // Center of each tab button (in root coordinates): where the glass wave is born.
    val tabCenter: (Int) -> Offset? = { i ->
        val b = barBounds
        if (b.width <= 0f) null
        else {
            val pad = with(density) { 4.dp.toPx() }          // the bar has 4dp of padding
            val tabW = (b.width - 2 * pad) / tabIcons.size
            Offset(b.left + pad + tabW * (i + 0.5f), b.center.y)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        // Everything the glass bar has to "see through" lives in the backdrop layer.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            PushStack(
                controller = push,
                modifier = Modifier.fillMaxSize(),
                detail = { title -> DetailScreen(title, onBack = { push.close() }) }
            ) {
                TabRevealHost(
                    selectedTab = tab,
                    tabCenter = tabCenter,
                    modifier = Modifier.fillMaxSize()
                ) { index, revealBase ->
                    when (index) {
                        0 -> HomePage(revealBase)
                        1 -> ExplorePage(revealBase)
                        else -> ProfilePage(revealBase)
                    }
                }
            }
        }

        LiquidBottomTabs(
            selectedTabIndex = { tab },
            onTabSelected = {
                tab = it
                push.close()
            },
            backdrop = backdrop,
            tabsCount = tabIcons.size,
            darkTheme = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 14.dp)
                .onGloballyPositioned { barBounds = it.boundsInRoot() }
        ) {
            tabIcons.forEachIndexed { i, icon ->
                LiquidBottomTab(onClick = {
                    tab = i
                    push.close()
                }) {
                    Icon(icon, contentDescription = tabLabels[i], tint = Color.White, modifier = Modifier.size(24.dp))
                    Text(tabLabels[i], color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
