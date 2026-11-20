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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.mfp.filemanager.R
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

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
            val glassBlur = com.mfp.filemanager.ui.theme.GlassConfig.blurDp(8f)
            if (glassBlur > 0.01f) blur(glassBlur.dp.toPx())
            val glassLensH = com.mfp.filemanager.ui.theme.GlassConfig.lensDp(24f)
            val glassLensA = com.mfp.filemanager.ui.theme.GlassConfig.lensDp(24f)
            if (glassLensH > 0.01f && glassLensA > 0.01f) lens(glassLensH.dp.toPx(), glassLensA.dp.toPx())
        },
        onDrawSurface = { drawRect(com.mfp.filemanager.ui.theme.GlassConfig.surface(container)) }
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
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f)
        )
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val wasExpanded = remember { booleanArrayOf(expanded) }
    // NOTE: opening the search no longer focuses the field / shows the keyboard by itself.
    // The keyboard only appears when the user taps the search field.
    LaunchedEffect(expanded) {
        if (expanded) {
            wasExpanded[0] = true
        } else if (wasExpanded[0]) {
            wasExpanded[0] = false
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    val density = LocalDensity.current
    val noRipple = remember { MutableInteractionSource() }

    val animationScope = rememberCoroutineScope()
    // same press behaviour as LiquidButton: press grow + finger-follow (tanh) + drag stretch
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

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
                    // tabs: while collapsing they get squeezed together with the capsule (uniform
                    // scale to fit the shrinking bar) and fade out near the end, like the reference
                    Box(
                        Modifier
                            .fillMaxSize()
                            .layout { measurable, c ->
                                val p = measurable.measure(Constraints.fixed(idleTabsW, fullH))
                                layout(c.maxWidth, c.maxHeight) {
                                    val fit = minOf(
                                        c.maxWidth / idleTabsW.toFloat(),
                                        c.maxHeight / fullH.toFloat()
                                    ).coerceIn(0f, 1f)
                                    p.placeRelativeWithLayer(
                                        (c.maxWidth - idleTabsW) / 2,
                                        (c.maxHeight - fullH) / 2
                                    ) {
                                        scaleX = fit
                                        scaleY = fit
                                        alpha = (1f - (progress.value - 0.3f) / 0.5f).coerceIn(0f, 1f)
                                    }
                                }
                            },
                        content = tabs
                    )
                    // icon that stays inside the circle
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = ((progress.value - 0.6f) * 2.5f).coerceIn(0f, 1f) },
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
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                val glassBlur = com.mfp.filemanager.ui.theme.GlassConfig.blurDp(8f)
                                if (glassBlur > 0.01f) blur(glassBlur.dp.toPx())
                                val glassLensH = com.mfp.filemanager.ui.theme.GlassConfig.lensDp(24f)
                                val glassLensA = com.mfp.filemanager.ui.theme.GlassConfig.lensDp(24f)
                                if (glassLensH > 0.01f && glassLensA > 0.01f) lens(glassLensH.dp.toPx(), glassLensA.dp.toPx())
                            },
                            // exactly the LiquidButton press effect; fades out while the field is
                            // open (k) so typing / selecting text doesn't wobble the field
                            layerBlock = {
                                val k = (1f - progress.value).coerceIn(0f, 1f)
                                val width = size.width
                                val height = size.height

                                val pressed = interactiveHighlight.pressProgress * k
                                val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, pressed)

                                val maxOffset = size.minDimension
                                val initialDerivative = 0.05f
                                val offset = interactiveHighlight.offset * k
                                translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                                translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                                val maxDragScale = 4f.dp.toPx() / size.height
                                val offsetAngle = atan2(offset.y, offset.x)
                                scaleX =
                                    scale +
                                        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                        (width / height).fastCoerceAtMost(1f)
                                scaleY =
                                    scale +
                                        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                        (height / width).fastCoerceAtMost(1f)
                            },
                            onDrawSurface = { drawRect(com.mfp.filemanager.ui.theme.GlassConfig.surface(containerColor)) }
                        )
                        .clickable(noRipple, indication = null) {
                            if (!expanded) {
                                onExpandedChange(true)
                            } else {
                                // tapping the open field focuses it and brings the keyboard
                                runCatching { focusRequester.requestFocus() }
                                keyboard?.show()
                            }
                        }
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                ) {
                  Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
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
                            .graphicsLayer { alpha = ((progress.value - 0.3f) / 0.3f).coerceIn(0f, 1f) },
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
            }
        ) { measurables, constraints ->
            val p = progress.value.coerceIn(-0.06f, 1.06f)
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
