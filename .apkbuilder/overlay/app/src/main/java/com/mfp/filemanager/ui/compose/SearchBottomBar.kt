package com.mfp.filemanager.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.mfp.filemanager.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val BarHeight = 64.dp      // height of the tabs bar and the search button when closed
private val CompactHeight = 48.dp  // height of the back circle and the search field when open
private val Gap = 8.dp
private val BarInset = 24.dp       // outer horizontal margin (same as the old bottom tabs)
private val SideInset = 16.dp      // inner inset of the search icon / text
private val SearchIconSize = 24.dp

/** Liquid-glass capsule, same look as the glass used by LiquidBottomTabs. */
private fun Modifier.glassPill(backdrop: Backdrop, container: Color): Modifier = this
    .drawBackdrop(
        backdrop = backdrop,
        shape = { Capsule() },
        effects = {
            vibrancy()
            blur(8f.dp.toPx())
            lens(24f.dp.toPx(), 24f.dp.toPx())
        },
        onDrawSurface = { drawRect(container) }
    )

/**
 * Bottom tabs bar + search button.
 *
 * - Closed: [tabs] inside a capsule + a round search button on the side.
 * - Tap search: the tabs capsule shrinks into a circle (showing [collapsedIcon]) and the
 *   search button grows into a text field.
 * - Tap the circle / system back: everything goes back.
 *
 * [tabs] is the ready-made LiquidBottomTabs (it draws its own glass while the bar is closed;
 * this composable draws the glass of the collapsed circle and cross-fades between them).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchBottomBar(
    backdrop: Backdrop,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onSearch: (String) -> Unit = {},
    containerColor: Color = Color.White.copy(alpha = 0.14f),
    contentColor: Color = Color.White,
    collapsedIcon: @Composable () -> Unit = {
        Icon(
            painter = painterResource(R.drawable.ic_tab_home),
            contentDescription = null,
            tint = contentColor
        )
    },
    tabs: @Composable BoxScope.() -> Unit,
) {
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
        )
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val wasExpanded = remember { booleanArrayOf(expanded) }
    LaunchedEffect(expanded) {
        if (expanded) {
            wasExpanded[0] = true
            // Showing the keyboard blocks the UI thread for a moment and resizes the window.
            // If it starts together with the morph, the morph skips frames and "snaps".
            // So let the morph play first, then focus + show the keyboard.
            withTimeoutOrNull(600) { snapshotFlow { progress.value }.first { it >= 0.8f } }
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        } else if (wasExpanded[0]) {
            wasExpanded[0] = false
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    val density = LocalDensity.current
    val noRipple = remember { MutableInteractionSource() }

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = BarInset)) {
        val fullH = with(density) { BarHeight.roundToPx() }
        val idleTabsW = with(density) { (maxWidth - Gap - BarHeight).roundToPx() }

        Layout(
            content = {
                // ───────── tabs capsule ─────────
                Box(
                    Modifier.graphicsLayer {
                        // clip only while collapsing, so the "press to grow" effect of the
                        // tabs bar can still overflow the capsule when the bar is idle
                        clip = progress.value > 0.001f
                        shape = CircleShape
                    }
                ) {
                    // glass of the collapsed circle (fades in while the bar collapses)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = progress.value }
                            .glassPill(backdrop, containerColor)
                    )
                    // tabs: fade out quickly while collapsing, keep their original width
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = (1f - progress.value * 3f).coerceIn(0f, 1f) }
                            .layout { measurable, c ->
                                val p = measurable.measure(Constraints.fixed(idleTabsW, fullH))
                                layout(c.maxWidth, c.maxHeight) {
                                    p.placeRelative(0, (c.maxHeight - fullH) / 2)
                                }
                            },
                        content = tabs
                    )
                    // icon that stays inside the circle
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = ((progress.value - 0.5f) * 2f).coerceIn(0f, 1f) },
                        contentAlignment = Alignment.Center
                    ) { collapsedIcon() }
                    // while search is open, tapping the circle restores the bar
                    if (expanded) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable(noRipple, indication = null) { onExpandedChange(false) }
                        )
                    }
                }

                // ───────── search capsule ─────────
                Box(
                    Modifier
                        .glassPill(backdrop, containerColor)
                        .clickable(noRipple, indication = null, enabled = !expanded) {
                            onExpandedChange(true)
                        }
                ) {
                    // search icon: from the center (circle) to the start (field)
                    Icon(
                        painter = painterResource(R.drawable.ic_tab_search),
                        contentDescription = "search",
                        tint = contentColor,
                        modifier = Modifier.layout { measurable, c ->
                            val size = SearchIconSize.roundToPx()
                            val p = measurable.measure(Constraints.fixed(size, size))
                            layout(c.maxWidth, c.maxHeight) {
                                val centered = (c.maxWidth - size) / 2
                                val start = SideInset.roundToPx()
                                p.placeRelative(
                                    lerp(centered, start, progress.value),
                                    (c.maxHeight - size) / 2
                                )
                            }
                        }
                    )
                    // text field: appears once the glass has stretched
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(start = SideInset + SearchIconSize + 10.dp, end = SideInset)
                            .graphicsLayer { alpha = ((progress.value - 0.55f) / 0.45f).coerceIn(0f, 1f) },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            enabled = expanded,
                            singleLine = true,
                            textStyle = TextStyle(color = contentColor, fontSize = 17.sp),
                            cursorBrush = SolidColor(contentColor),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                onSearch(query)
                                keyboard?.hide()
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) {
                                        Text(
                                            placeholder,
                                            color = contentColor.copy(alpha = 0.55f),
                                            fontSize = 17.sp,
                                            maxLines = 1
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val p = progress.value
            val full = constraints.maxWidth
            val gap = Gap.roundToPx()
            val compactH = CompactHeight.roundToPx()

            val h = lerp(fullH, compactH, p)
            val tabsW = lerp(full - gap - fullH, compactH, p).coerceAtLeast(0)
            val searchW = (full - gap - tabsW).coerceAtLeast(0)

            val tabsPlaceable = measurables[0].measure(Constraints.fixed(tabsW, h))
            val searchPlaceable = measurables[1].measure(Constraints.fixed(searchW, h))

            layout(full, fullH) {
                val y = (fullH - h) / 2
                tabsPlaceable.placeRelative(0, y)
                searchPlaceable.placeRelative(tabsW + gap, y)
            }
        }
    }
}
