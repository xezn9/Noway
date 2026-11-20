package com.mfp.filemanager.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.mfp.filemanager.R

private data class TabItem(@DrawableRes val icon: Int, val labelRes: Int)

// Same order/functions as the ViewPager in MainPagerFragment:
// 0 = Home, 1 = Music, 2 = Settings   (Bin tab removed, search lives next to the tabs now)
private val tabs = listOf(
    TabItem(R.drawable.ic_tab_home, R.string.tab_home),
    TabItem(R.drawable.ic_tab_music, R.string.tab_music),
    TabItem(R.drawable.ic_tab_settings, R.string.tab_settings)
)

/**
 * Hosts the existing View-based UI (nav host, mini player, progress overlay...) inside a
 * Compose layer that is registered as the backdrop. The Liquid Glass tabs + search button then
 * blur / refract whatever the screen is showing behind them.
 */
@Composable
fun MainContainer(
    contentView: android.view.View,
    selectedTab: () -> Int,
    onTabSelected: (Int) -> Unit,
    onTabClick: (Int) -> Unit,
    tabsVisible: () -> Boolean,
    darkTheme: Boolean,
    searchExpanded: () -> Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchQuery: () -> String,
    onSearchQueryChange: (String) -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val contentColor = if (darkTheme) Color.White else Color.Black
    // same glass tint LiquidBottomTabs uses, so the search button matches the bar
    val glassColor =
        if (darkTheme) Color(0xFF121212).copy(0.4f)
        else Color(0xFFFAFAFA).copy(0.4f)

    // Edge-to-edge: the window is not resized by the keyboard any more, so the whole screen
    // (content + bar) shrinks with imePadding, exactly like adjustResize did before.
    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Everything the glass should "see" goes inside the backdrop layer
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AndroidView(
                factory = { contentView },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Page titles: real Backdrop glass capsules drawn over the headers of every screen
        GlassTitleOverlay(backdrop = backdrop)

        AnimatedVisibility(
            visible = tabsVisible(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            SearchBottomBar(
                backdrop = backdrop,
                expanded = searchExpanded(),
                onExpandedChange = onSearchExpandedChange,
                query = searchQuery(),
                onQueryChange = onSearchQueryChange,
                placeholder = stringResource(R.string.search_files_hint),
                containerColor = glassColor,
                contentColor = contentColor,
                collapsedIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_tab_home),
                        contentDescription = null,
                        tint = contentColor
                    )
                },
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                LiquidBottomTabs(
                    selectedTabIndex = selectedTab,
                    onTabSelected = onTabSelected,
                    backdrop = backdrop,
                    tabsCount = tabs.size,
                    darkTheme = darkTheme,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, tab ->
                        LiquidBottomTab(onClick = { onTabClick(index) }) {
                            Icon(
                                painter = painterResource(tab.icon),
                                contentDescription = null,
                                tint = contentColor
                            )
                            Text(
                                text = stringResource(tab.labelRes),
                                color = contentColor,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Liquid Glass dialogs: drawn ABOVE everything and OUTSIDE the backdrop layer,
        // so the glass refracts the whole screen behind it.
        LiquidDialogHost(backdrop = backdrop, dark = darkTheme)
    }
}
