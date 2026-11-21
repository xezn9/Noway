package com.ecogif.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecogif.app.engine.GifToVideo
import com.ecogif.app.engine.MediaSaver
import com.ecogif.app.engine.VideoToGif
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlin.math.roundToInt

private fun formatTime(sec: Float): String {
    val total = sec.roundToInt()
    return "%d:%02d".format(total / 60, total % 60)
}

/** Fixed signature shown at the bottom of every page. Change the text here. */
const val SIGNATURE = "EcoGif"

@Composable
private fun ScreenColumn(header: @Composable () -> Unit, content: @Composable () -> Unit) {
    // the whole page scrolls (header included) and fills the screen edge to edge
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        header()
        content()
        Text(
            SIGNATURE,
            color = LocalAccent.current.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.navigationBarsPadding().height(130.dp))
    }
}

@Composable
private fun PickCard(
    title: String,
    hint: String,
    pickText: String,
    changeText: String,
    hasFile: Boolean,
    onPick: () -> Unit,
    backdrop: Backdrop,
    colors: EcoColors,
    preview: @Composable () -> Unit,
    details: String,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    GlassCard(backdrop, colors) {
        CardTitle(title, colors)
        if (hasFile) {
            preview()
            Text(details, color = colors.secondary, fontSize = 13.sp)
            extra()
        } else {
            Text(hint, color = colors.secondary, fontSize = 14.sp)
        }
        LiquidButton(
            onClick = onPick,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = if (colors.dark) Color.White.copy(0.10f) else Color.White.copy(0.35f)
        ) {
            Text(if (hasFile) changeText else pickText, color = colors.content, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RunButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop,
) {
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        tint = LocalAccent.current,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/** A row of preset buttons; the selected one is tinted with the accent colour. */
@Composable
private fun PresetRow(
    values: List<Float>,
    selected: Float,
    onSelect: (Float) -> Unit,
    backdrop: Backdrop,
    colors: EcoColors,
) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        values.forEach { p ->
            val on = p.roundToInt() == selected.roundToInt()
            LiquidButton(
                onClick = { onSelect(p) },
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                tint = if (on) accent else Color.Unspecified,
                surfaceColor = if (on) Color.Unspecified
                else if (colors.dark) Color.White.copy(0.10f) else Color.White.copy(0.35f)
            ) {
                Text(
                    "${p.roundToInt()}%",
                    color = if (on) Color.White else colors.content,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

// ------------------------------------------------------------------ tab 1

@Composable
fun ResizeScreen(
    state: ResizeState,
    task: TaskState,
    backdrop: Backdrop,
    colors: EcoColors,
    header: @Composable () -> Unit,
    onPick: () -> Unit,
    onRun: () -> Unit,
) {
    ScreenColumn(header) {
        val info = state.info
        Staggered(0) {
            PickCard(
                title = "GIF",
                hint = "Pick a GIF to change its dimensions.",
                pickText = "Choose GIF",
                changeText = "Choose another GIF",
                hasFile = state.uri != null && info != null,
                onPick = onPick,
                backdrop = backdrop,
                colors = colors,
                preview = {
                    state.uri?.let {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
                        ) { ResizePreview(state, it) }
                    }
                },
                details = if (info == null) "" else buildString {
                    append("${info.width} × ${info.height}")
                    if (state.isCropped) append("  →  ${tr("crop")} ${state.cropW} × ${state.cropH}")
                    val ow = state.outW
                    val oh = state.outH
                    if (ow != null && oh != null && (ow != info.width || oh != info.height)) append("  →  $ow × $oh")
                    append(" ${tr("px")}  ·  ${info.frameCount} ${tr("frames")}  ·  ${MediaSaver.formatSize(state.sizeBytes)}")
                },
                extra = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiquidButton(
                            onClick = { state.cropping = !state.cropping },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f),
                            tint = if (state.cropping) LocalAccent.current else Color.Unspecified,
                            surfaceColor = if (state.cropping) Color.Unspecified
                            else if (colors.dark) Color.White.copy(0.10f) else Color.White.copy(0.35f)
                        ) {
                            Text(
                                if (state.cropping) "Done" else "Crop box",
                                color = if (state.cropping) Color.White else colors.content,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (state.isCropped) {
                            LiquidButton(
                                onClick = { state.resetCrop() },
                                backdrop = backdrop,
                                modifier = Modifier.weight(1f),
                                surfaceColor = if (colors.dark) Color.White.copy(0.10f) else Color.White.copy(0.35f)
                            ) {
                                Text("Reset crop", color = colors.content, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (state.cropping) {
                        Text(
                            "Drag any side or corner of the box to cut the GIF. Drag inside the box to move it.",
                            color = colors.secondary,
                            fontSize = 12.sp
                        )
                    }
                }
            )
        }

        if (info != null) {
            // quick numbers
            Staggered(1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "${state.outW ?: "–"} × ${state.outH ?: "–"}", "output px",
                        backdrop, colors, Modifier.weight(1.5f)
                    )
                    StatTile(
                        "${if (state.eco) (info.frameCount + 1) / 2 else info.frameCount}", "frames",
                        backdrop, colors, Modifier.weight(1f)
                    )
                }
            }

            Staggered(2) {
                GlassCard(backdrop, colors) {
                    CardTitle("New size", colors)
                    PresetRow(listOf(25f, 50f, 75f, 100f), state.scale, { state.applyScale(it) }, backdrop, colors)
                    SliderRow(
                        title = "Scale",
                        valueText = "${state.scale.roundToInt()}%",
                        value = state.scale,
                        onValueChange = { state.applyScale(it.roundToInt().toFloat()) },
                        range = 5f..200f,
                        backdrop = backdrop,
                        colors = colors
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField("Width (px)", state.widthText, state::setWidth, colors, Modifier.weight(1f))
                        NumberField("Height (px)", state.heightText, state::setHeight, colors, Modifier.weight(1f))
                    }
                    ToggleRow(
                        title = "Keep aspect ratio",
                        subtitle = "Changing one side updates the other",
                        checked = state.keepRatio,
                        onChange = { state.keepRatio = it },
                        backdrop = backdrop,
                        colors = colors
                    )
                }
            }

            Staggered(3) {
                GlassCard(backdrop, colors) {
                    CardTitle("Options", colors)
                    ToggleRow(
                        title = "Eco mode",
                        subtitle = "Drops every 2nd frame for a much smaller file",
                        checked = state.eco,
                        onChange = { state.eco = it },
                        backdrop = backdrop,
                        colors = colors
                    )
                    ToggleRow(
                        title = "Dithering",
                        subtitle = "Smoother gradients, bigger file",
                        checked = state.dither,
                        onChange = { state.dither = it },
                        backdrop = backdrop,
                        colors = colors
                    )
                }
            }

            val valid = state.outW != null && state.outH != null
            Staggered(4) {
                RunButton(
                    text = if (valid) "${tr("Resize & Save")}  (${state.outW} × ${state.outH})" else tr("Enter a valid size"),
                    enabled = valid && !task.busy,
                    onClick = onRun,
                    backdrop = backdrop
                )
            }
        }
    }
}

// ------------------------------------------------------------------ tab 2

@Composable
fun VideoToGifScreen(
    state: VideoToGifState,
    task: TaskState,
    backdrop: Backdrop,
    colors: EcoColors,
    header: @Composable () -> Unit,
    onPick: () -> Unit,
    onRun: () -> Unit,
) {
    ScreenColumn(header) {
        val info = state.info
        Staggered(0) {
            PickCard(
                title = "Video",
                hint = "Pick a video and turn a clip of it into a GIF.",
                pickText = "Choose video",
                changeText = "Choose another video",
                hasFile = state.uri != null && info != null,
                onPick = onPick,
                backdrop = backdrop,
                colors = colors,
                preview = { state.thumb?.let { BitmapPreview(it) } },
                details = if (info == null) "" else
                    "${info.width} × ${info.height} ${tr("px")}  ·  ${formatTime(info.durationMs / 1000f)}  ·  ${MediaSaver.formatSize(state.sizeBytes)}"
            )
        }

        if (info != null) {
            // the slider ranges depend on the video, so rebuild the sliders for every new video
            key(state.uri) {
                val w = state.width.roundToInt()
                val h = VideoToGif.heightFor(info, w)
                val realFps = VideoToGif.effectiveFps(state.fps.roundToInt())

                Staggered(1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("${state.frameCount}", "frames", backdrop, colors, Modifier.weight(1f))
                        StatTile("$realFps", "fps", backdrop, colors, Modifier.weight(1f))
                        StatTile("$w×$h", "px", backdrop, colors, Modifier.weight(1.3f))
                    }
                }

                Staggered(2) {
                    GlassCard(backdrop, colors) {
                        CardTitle("Clip", colors)
                        TimelineBar(state.startSec, state.effectiveLengthSec, state.durationSec, colors)
                        SliderRow(
                            title = "Start",
                            valueText = "${"%.1f".format(state.startSec)} ${tr("s")}",
                            value = state.startSec,
                            onValueChange = { state.startSec = (it * 10).roundToInt() / 10f },
                            range = 0f..maxOf(0.5f, state.durationSec - 0.5f),
                            backdrop = backdrop,
                            colors = colors
                        )
                        SliderRow(
                            title = "Length",
                            valueText = "${"%.1f".format(state.effectiveLengthSec)} ${tr("s")}",
                            value = state.lengthSec,
                            onValueChange = { state.lengthSec = (it * 10).roundToInt() / 10f },
                            range = 0.5f..15f,
                            backdrop = backdrop,
                            colors = colors
                        )
                    }
                }

                Staggered(3) {
                    GlassCard(backdrop, colors) {
                        CardTitle("Quality", colors)
                        SliderRow(
                            title = "Frames per second",
                            valueText = "${state.fps.roundToInt()} ${tr("fps")}",
                            value = state.fps,
                            onValueChange = { state.fps = it.roundToInt().toFloat() },
                            range = 5f..60f,
                            backdrop = backdrop,
                            colors = colors
                        )
                        if (state.fps.roundToInt() > VideoToGif.MAX_REAL_FPS) {
                            Text(
                                if (I18n.arabic) "يعرض الـ GIF بحد أقصى ${VideoToGif.MAX_REAL_FPS} إطار/ث"
                                else "GIF plays at ${VideoToGif.MAX_REAL_FPS} fps at most",
                                color = colors.secondary,
                                fontSize = 12.sp
                            )
                        }
                        val lo = minOf(120, state.maxWidth - 1).toFloat()
                        SliderRow(
                            title = "Width",
                            valueText = "${state.width.roundToInt()} ${tr("px")}",
                            value = state.width,
                            onValueChange = { state.width = it.roundToInt().toFloat() },
                            range = lo..state.maxWidth.toFloat(),
                            backdrop = backdrop,
                            colors = colors
                        )
                        ToggleRow(
                            title = "Dithering",
                            subtitle = "Smoother gradients, bigger file",
                            checked = state.dither,
                            onChange = { state.dither = it },
                            backdrop = backdrop,
                            colors = colors
                        )
                    }
                }
            }

            Staggered(4) {
                RunButton(
                    text = "Create GIF",
                    enabled = !task.busy,
                    onClick = onRun,
                    backdrop = backdrop
                )
            }
        }
    }
}

// ------------------------------------------------------------------ tab 3

@Composable
fun GifToVideoScreen(
    state: GifToVideoState,
    task: TaskState,
    backdrop: Backdrop,
    colors: EcoColors,
    header: @Composable () -> Unit,
    onPick: () -> Unit,
    onRun: () -> Unit,
) {
    ScreenColumn(header) {
        val info = state.info
        Staggered(0) {
            PickCard(
                title = "GIF",
                hint = "Pick a GIF and save it as an MP4 video.",
                pickText = "Choose GIF",
                changeText = "Choose another GIF",
                hasFile = state.uri != null && info != null,
                onPick = onPick,
                backdrop = backdrop,
                colors = colors,
                preview = { state.uri?.let { GifPreview(it) } },
                details = if (info == null) "" else
                    "${info.width} × ${info.height} ${tr("px")}  ·  ${info.frameCount} ${tr("frames")}  ·  ${MediaSaver.formatSize(state.sizeBytes)}"
            )
        }

        if (info != null) {
            val percent = state.scale.roundToInt()
            val (ow, oh) = remember(info, percent) { GifToVideo.outputSize(info.width, info.height, percent) }
            val loops = state.loops.roundToInt()
            val seconds = info.durationMs * loops / 1000f

            // big "result" panel
            Staggered(1) {
                GlassCard(backdrop, colors) {
                    CardTitle("MP4 result", colors)
                    Text(
                        "$ow × $oh",
                        color = LocalAccent.current,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (I18n.arabic) "${tr("about")} ${formatTime(seconds)}  ·  ${loops}× ${tr("repeat")}  ·  $percent% ${tr("size")}"
                        else "about ${formatTime(seconds)} long  ·  ${loops}× repeat  ·  $percent% size",
                        color = colors.secondary,
                        fontSize = 14.sp
                    )
                }
            }

            Staggered(2) {
                GlassCard(backdrop, colors) {
                    CardTitle("Size", colors)
                    PresetRow(listOf(25f, 50f, 75f, 100f), state.scale, { state.scale = it }, backdrop, colors)
                    SliderRow(
                        title = "Scale",
                        valueText = "$percent%",
                        value = state.scale,
                        onValueChange = { state.scale = it.roundToInt().toFloat() },
                        range = 25f..100f,
                        backdrop = backdrop,
                        colors = colors
                    )
                }
            }

            Staggered(3) {
                GlassCard(backdrop, colors) {
                    CardTitle("Repeat the GIF", colors)
                    Stepper(
                        text = "${loops}×",
                        onMinus = { state.loops = (loops - 1).coerceAtLeast(1).toFloat() },
                        onPlus = { state.loops = (loops + 1).coerceAtMost(10).toFloat() },
                        backdrop = backdrop,
                        colors = colors
                    )
                }
            }

            Staggered(4) {
                RunButton(
                    text = "Convert to MP4",
                    enabled = !task.busy,
                    onClick = onRun,
                    backdrop = backdrop
                )
            }
        }
    }
}

// ------------------------------------------------------------------ settings

private val pageNames = listOf("Resize", "To GIF", "To Video")

@Composable
private fun SwatchGrid(selected: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Swatches.chunked(5).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEachIndexed { c, sw ->
                    val index = r * 5 + c
                    val on = index == selected
                    val scale by animateFloatAsState(if (on) 1.14f else 1f, label = "swatch")
                    Box(
                        Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(sw.color)
                            .border(
                                if (on) 3.dp else 1.dp,
                                if (on) Color.White else Color.White.copy(0.35f),
                                CircleShape
                            )
                            .clickable { onPick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) Text("✓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    backdrop: Backdrop,
    colors: EcoColors,
    header: @Composable () -> Unit,
    onLanguage: (Boolean, androidx.compose.ui.geometry.Offset) -> Unit,
) {
    ScreenColumn(header) {
        Staggered(0) {
            GlassCard(backdrop, colors) {
                CardTitle("Language", colors)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to "العربية", false to "English").forEach { (ar, label) ->
                        val on = settings.arabic == ar
                        val center = remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                        Box(
                            Modifier
                                .weight(1f)
                                .androidx_onPositioned { center.value = it }
                        ) {
                        LiquidButton(
                            onClick = { if (!on) onLanguage(ar, center.value) },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            tint = if (on) LocalAccent.current else Color.Unspecified,
                            surfaceColor = if (on) Color.Unspecified
                            else if (colors.dark) Color.White.copy(0.10f) else Color.White.copy(0.35f)
                        ) {
                            Text(
                                label,
                                color = if (on) Color.White else colors.content,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                        }
                    }
                }
                ToggleRow(
                    title = "Splash screen",
                    subtitle = "Show the animated splash when the app opens",
                    checked = settings.showSplash,
                    onChange = { settings.changeSplash(it) },
                    backdrop = backdrop,
                    colors = colors
                )
            }
        }

        Staggered(1) {
            GlassCard(backdrop, colors) {
                CardTitle("Page colors", colors)
                Text(
                    "Choose a page, then a colour. This page shows a live preview of it.",
                    color = colors.secondary,
                    fontSize = 13.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pageNames.forEachIndexed { i, name ->
                        val on = settings.editTab == i
                        LiquidButton(
                            onClick = { settings.editTab = i },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f),
                            tint = if (on) Swatches[settings.colorIndex(i)].color else Color.Unspecified,
                            surfaceColor = if (on) Color.Unspecified
                            else if (colors.dark) Color.White.copy(0.10f) else Color.White.copy(0.35f)
                        ) {
                            Text(
                                name,
                                color = if (on) Color.White else colors.content,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                val page = settings.editTab
                SwatchGrid(settings.colorIndex(page)) { settings.setColor(page, it) }
                Text(
                    "${tr(pageNames[page])}: ${tr(Swatches[settings.colorIndex(page)].name)}",
                    color = colors.secondary,
                    fontSize = 13.sp
                )
            }
        }

        Staggered(2) {
            GlassCard(backdrop, colors) {
                CardTitle("Liquid glass", colors)
                Text(
                    "Changes every glass surface in the app, live.",
                    color = colors.secondary,
                    fontSize = 13.sp
                )
                SliderRow(
                    title = "Blur",
                    valueText = "${settings.glass.blurPct.roundToInt()}%",
                    value = settings.glass.blurPct,
                    onValueChange = { settings.setBlur(it.roundToInt().toFloat()) },
                    range = 0f..100f,
                    backdrop = backdrop,
                    colors = colors
                )
                SliderRow(
                    title = "Transparency",
                    valueText = "${settings.glass.transparencyPct.roundToInt()}%",
                    value = settings.glass.transparencyPct,
                    onValueChange = { settings.setTransparency(it.roundToInt().toFloat()) },
                    range = 0f..100f,
                    backdrop = backdrop,
                    colors = colors
                )
                SliderRow(
                    title = "Refraction",
                    valueText = "${settings.glass.refractionPct.roundToInt()}%",
                    value = settings.glass.refractionPct,
                    onValueChange = { settings.setRefraction(it.roundToInt().toFloat()) },
                    range = 0f..100f,
                    backdrop = backdrop,
                    colors = colors
                )
                LiquidButton(
                    onClick = { settings.resetGlass() },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    surfaceColor = if (colors.dark) Color.White.copy(0.10f) else Color.White.copy(0.35f)
                ) {
                    Text("Reset glass", color = colors.content, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Reports the centre of this element in window coordinates. */
private fun Modifier.androidx_onPositioned(onCenter: (androidx.compose.ui.geometry.Offset) -> Unit): Modifier =
    this.then(
        androidx.compose.ui.Modifier.onGloballyPositioned { c ->
            val p = c.positionInRoot()
            onCenter(androidx.compose.ui.geometry.Offset(p.x + c.size.width / 2f, p.y + c.size.height / 2f))
        }
    )
