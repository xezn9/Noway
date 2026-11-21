package com.ecogif.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecogif.app.engine.GifToVideo
import com.ecogif.app.engine.MediaSaver
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlin.math.roundToInt

private fun formatTime(sec: Float): String {
    val total = sec.roundToInt()
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        content()
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
) {
    GlassCard(backdrop, colors) {
        CardTitle(title, colors)
        if (hasFile) {
            preview()
            Text(details, color = colors.secondary, fontSize = 13.sp)
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
        tint = EcoGreen,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

// ------------------------------------------------------------------ tab 1

@Composable
fun ResizeScreen(
    state: ResizeState,
    task: TaskState,
    backdrop: Backdrop,
    colors: EcoColors,
    onPick: () -> Unit,
    onRun: () -> Unit,
) {
    ScreenColumn {
        val info = state.info
        PickCard(
            title = "GIF",
            hint = "Pick a GIF to change its dimensions.",
            pickText = "Choose GIF",
            changeText = "Choose another GIF",
            hasFile = state.uri != null && info != null,
            onPick = onPick,
            backdrop = backdrop,
            colors = colors,
            preview = { state.uri?.let { GifPreview(it) } },
            details = if (info == null) "" else
                "${info.width} × ${info.height} px  ·  ${info.frameCount} frames  ·  ${MediaSaver.formatSize(state.sizeBytes)}"
        )

        if (info != null) {
            GlassCard(backdrop, colors) {
                CardTitle("New size", colors)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(25f, 50f, 75f, 100f).forEach { p ->
                        LiquidButton(
                            onClick = { state.setScale(p) },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f),
                            surfaceColor = if (colors.dark) Color.White.copy(0.10f) else Color.White.copy(0.35f)
                        ) {
                            Text("${p.roundToInt()}%", color = colors.content, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                SliderRow(
                    title = "Scale",
                    valueText = "${state.scale.roundToInt()}%",
                    value = state.scale,
                    onValueChange = { state.setScale(it.roundToInt().toFloat()) },
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

            val valid = state.outW != null && state.outH != null
            RunButton(
                text = if (valid) "Resize & Save  (${state.outW} × ${state.outH})" else "Enter a valid size",
                enabled = valid && !task.busy,
                onClick = onRun,
                backdrop = backdrop
            )
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
    onPick: () -> Unit,
    onRun: () -> Unit,
) {
    ScreenColumn {
        val info = state.info
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
                "${info.width} × ${info.height} px  ·  ${formatTime(info.durationMs / 1000f)}  ·  ${MediaSaver.formatSize(state.sizeBytes)}"
        )

        if (info != null) {
            // the slider ranges depend on the video, so rebuild the sliders for every new video
            key(state.uri) {
                GlassCard(backdrop, colors) {
                    CardTitle("Clip", colors)
                    SliderRow(
                        title = "Start",
                        valueText = "${"%.1f".format(state.startSec)} s",
                        value = state.startSec,
                        onValueChange = { state.startSec = (it * 10).roundToInt() / 10f },
                        range = 0f..maxOf(0.5f, state.durationSec - 0.5f),
                        backdrop = backdrop,
                        colors = colors
                    )
                    SliderRow(
                        title = "Length",
                        valueText = "${"%.1f".format(state.effectiveLengthSec)} s",
                        value = state.lengthSec,
                        onValueChange = { state.lengthSec = (it * 10).roundToInt() / 10f },
                        range = 0.5f..15f,
                        backdrop = backdrop,
                        colors = colors
                    )
                }

                GlassCard(backdrop, colors) {
                    CardTitle("Quality", colors)
                    SliderRow(
                        title = "Frames per second",
                        valueText = "${state.fps.roundToInt()} fps",
                        value = state.fps,
                        onValueChange = { state.fps = it.roundToInt().toFloat() },
                        range = 5f..24f,
                        backdrop = backdrop,
                        colors = colors
                    )
                    val lo = minOf(120, state.maxWidth - 1).toFloat()
                    SliderRow(
                        title = "Width",
                        valueText = "${state.width.roundToInt()} px",
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
                    val w = state.width.roundToInt()
                    val h = com.ecogif.app.engine.VideoToGif.heightFor(info, w)
                    Text(
                        "≈ ${state.frameCount} frames  ·  $w × $h px",
                        color = colors.secondary,
                        fontSize = 13.sp
                    )
                }
            }

            RunButton(
                text = "Create GIF",
                enabled = !task.busy,
                onClick = onRun,
                backdrop = backdrop
            )
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
    onPick: () -> Unit,
    onRun: () -> Unit,
) {
    ScreenColumn {
        val info = state.info
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
                "${info.width} × ${info.height} px  ·  ${info.frameCount} frames  ·  ${MediaSaver.formatSize(state.sizeBytes)}"
        )

        if (info != null) {
            GlassCard(backdrop, colors) {
                CardTitle("Video", colors)
                SliderRow(
                    title = "Scale",
                    valueText = "${state.scale.roundToInt()}%",
                    value = state.scale,
                    onValueChange = { state.scale = it.roundToInt().toFloat() },
                    range = 25f..100f,
                    backdrop = backdrop,
                    colors = colors
                )
                SliderRow(
                    title = "Repeat the GIF",
                    valueText = "${state.loops.roundToInt()}×",
                    value = state.loops,
                    onValueChange = { state.loops = it.roundToInt().toFloat() },
                    range = 1f..10f,
                    backdrop = backdrop,
                    colors = colors
                )
                val percent = state.scale.roundToInt()
                val (ow, oh) = remember(info, percent) { GifToVideo.outputSize(info.width, info.height, percent) }
                val seconds = info.durationMs * state.loops.roundToInt() / 1000f
                Text(
                    "$ow × $oh px  ·  about ${formatTime(seconds)} long",
                    color = colors.secondary,
                    fontSize = 13.sp
                )
            }

            RunButton(
                text = "Convert to MP4",
                enabled = !task.busy,
                onClick = onRun,
                backdrop = backdrop
            )
        }
    }
}
