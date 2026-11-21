package com.ecogif.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecogif.app.engine.GifDecoder
import com.ecogif.app.engine.GifInfo
import com.ecogif.app.engine.GifResizer
import com.ecogif.app.engine.GifToVideo
import com.ecogif.app.engine.MediaSaver
import com.ecogif.app.engine.VideoInfo
import com.ecogif.app.engine.VideoToGif
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class LoadedVideo(val info: VideoInfo, val thumb: android.graphics.Bitmap?, val name: String, val size: Long)
private class LoadedGif(val bytes: ByteArray, val info: GifInfo, val name: String)

private fun share(context: Context, uri: Uri, mime: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun EcoGifApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val colors = remember(dark) { EcoColors(dark) }

    // wallpaper layer (what the glass refracts) and whole-screen layer (what the dialog refracts)
    val wallBackdrop = rememberLayerBackdrop()
    val screenBackdrop = rememberLayerBackdrop()

    var tab by remember { mutableIntStateOf(0) }
    val task = remember { TaskState() }
    val resize = remember { ResizeState() }
    val toGif = remember { VideoToGifState() }
    val toVideo = remember { GifToVideoState() }

    var dialog by remember { mutableStateOf(DialogData("", "", "OK", "Close", EcoGreen, null)) }
    var dialogVisible by remember { mutableStateOf(false) }

    fun showDialog(d: DialogData) {
        dialog = d
        dialogVisible = true
    }

    fun showError(message: String) = showDialog(
        DialogData("Something went wrong", message, "OK", "Close", Color(0xFFFF3B30), null)
    )

    // ------------------------------------------------------------ loading inputs

    fun loadGif(uri: Uri, onLoaded: (LoadedGif) -> Unit) {
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val bytes = MediaSaver.readAll(context, uri)
                    LoadedGif(bytes, GifDecoder.probe(bytes), MediaSaver.nameOf(context, uri))
                }
                onLoaded(loaded)
            } catch (e: Exception) {
                showError(e.message ?: "Could not read this GIF.")
            }
        }
    }

    fun loadVideo(uri: Uri) {
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    LoadedVideo(
                        VideoToGif.probe(context, uri),
                        VideoToGif.thumbnail(context, uri),
                        MediaSaver.nameOf(context, uri),
                        MediaSaver.sizeOf(context, uri)
                    )
                }
                toGif.load(uri, loaded.info, loaded.thumb, loaded.name, loaded.size)
            } catch (e: Exception) {
                showError(e.message ?: "Could not read this video.")
            }
        }
    }

    val pickGifForResize = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadGif(uri) { resize.load(uri, it.bytes, it.info, it.name) }
    }
    val pickGifForVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadGif(uri) { toVideo.load(uri, it.bytes, it.info, it.name) }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadVideo(uri)
    }
    val gifRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif"))
    val videoRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)

    // ------------------------------------------------------------ running a conversion

    fun perform(
        kind: MediaSaver.Kind,
        label: String,
        inputSize: Long,
        write: suspend (MediaSaver.Pending, (Float) -> Unit) -> Unit,
    ) {
        if (task.busy) return
        task.busy = true
        task.progress = 0f
        task.label = label
        task.job = scope.launch {
            var pending: MediaSaver.Pending? = null
            try {
                val done = withContext(Dispatchers.Default) {
                    val p = MediaSaver.create(context, kind)
                    pending = p
                    write(p) { task.progress = it }
                    MediaSaver.publish(context, p)
                    p
                }
                val outSize = MediaSaver.sizeOf(context, done.uri)
                val message = buildString {
                    append("Saved to ${done.displayPath}\n")
                    if (inputSize > 0) {
                        val diff = ((outSize - inputSize) * 100.0 / inputSize).roundToInt()
                        append("${MediaSaver.formatSize(inputSize)} → ${MediaSaver.formatSize(outSize)}")
                        append("  (${if (diff > 0) "+" else ""}$diff%)")
                    } else {
                        append("Size: ${MediaSaver.formatSize(outSize)}")
                    }
                }
                showDialog(
                    DialogData("Done!", message, "Share", "Close", EcoGreen) {
                        share(context, done.uri, kind.mime)
                    }
                )
            } catch (e: CancellationException) {
                pending?.let { MediaSaver.discard(context, it) }
                throw e
            } catch (e: Throwable) {
                pending?.let { MediaSaver.discard(context, it) }
                showError(e.message ?: e.javaClass.simpleName)
            } finally {
                task.busy = false
                task.job = null
            }
        }
    }

    fun openOut(pending: MediaSaver.Pending) =
        context.contentResolver.openOutputStream(pending.uri)
            ?: throw IllegalStateException("Cannot write the output file")

    fun runResize() {
        val bytes = resize.bytes ?: return
        val w = resize.outW ?: return
        val h = resize.outH ?: return
        val eco = resize.eco
        val dither = resize.dither
        perform(MediaSaver.Kind.GIF, "Resizing GIF…", bytes.size.toLong()) { pending, report ->
            openOut(pending).buffered().use { out ->
                GifResizer.resize(bytes, out, w, h, eco, dither, report)
            }
        }
    }

    fun runVideoToGif() {
        val uri = toGif.uri ?: return
        val info = toGif.info ?: return
        val start = (toGif.startSec * 1000).toLong()
        val length = (toGif.effectiveLengthSec * 1000).toLong()
        val fps = toGif.fps.roundToInt()
        val width = toGif.width.roundToInt().coerceAtMost(toGif.maxWidth)
        val dither = toGif.dither
        perform(MediaSaver.Kind.GIF, "Creating GIF…", 0L) { pending, report ->
            openOut(pending).buffered().use { out ->
                VideoToGif.convert(context, uri, out, info, start, length, fps, width, dither, report)
            }
        }
    }

    fun runGifToVideo() {
        val bytes = toVideo.bytes ?: return
        val info = toVideo.info ?: return
        val (w, h) = GifToVideo.outputSize(info.width, info.height, toVideo.scale.roundToInt())
        val loops = toVideo.loops.roundToInt()
        perform(MediaSaver.Kind.VIDEO, "Encoding MP4…", 0L) { pending, report ->
            val pfd = context.contentResolver.openFileDescriptor(pending.uri, "rw")
                ?: throw IllegalStateException("Cannot write the output file")
            pfd.use { GifToVideo.convert(bytes, it.fileDescriptor, w, h, loops, report) }
        }
    }

    // ------------------------------------------------------------ UI

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(screenBackdrop)) {
            EcoWallpaper(dark, Modifier.fillMaxSize().layerBackdrop(wallBackdrop))

            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Text("EcoGif", color = colors.content, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (tab) {
                            0 -> "Resize a GIF"
                            1 -> "Turn a video into a GIF"
                            else -> "Turn a GIF into a video"
                        },
                        color = colors.secondary,
                        fontSize = 14.sp
                    )
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> ResizeScreen(
                            resize, task, wallBackdrop, colors,
                            onPick = { pickGifForResize.launch(gifRequest) },
                            onRun = ::runResize
                        )
                        1 -> VideoToGifScreen(
                            toGif, task, wallBackdrop, colors,
                            onPick = { pickVideo.launch(videoRequest) },
                            onRun = ::runVideoToGif
                        )
                        else -> GifToVideoScreen(
                            toVideo, task, wallBackdrop, colors,
                            onPick = { pickGifForVideo.launch(gifRequest) },
                            onRun = ::runGifToVideo
                        )
                    }
                }
            }

            if (task.busy) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, bottom = 100.dp)
                ) {
                    GlassCard(wallBackdrop, colors) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(task.label, color = colors.content, fontWeight = FontWeight.SemiBold)
                            Text("${(task.progress * 100).roundToInt()}%", color = colors.secondary)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(colors.field)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(task.progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(EcoGreen)
                            )
                        }
                        LiquidButton(
                            onClick = { task.job?.cancel() },
                            backdrop = wallBackdrop,
                            modifier = Modifier.fillMaxWidth(),
                            surfaceColor = if (dark) Color.White.copy(0.10f) else Color.White.copy(0.35f)
                        ) {
                            Text("Cancel", color = colors.content, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            val tabLabels = listOf("Resize", "To GIF", "To Video")
            val tabIcons = listOf(EcoIcons.Resize, EcoIcons.Movie, EcoIcons.Gif)
            CompositionLocalProvider(LocalContentColor provides colors.content) {
                LiquidBottomTabs(
                    selectedTabIndex = { tab },
                    onTabSelected = { tab = it },
                    backdrop = wallBackdrop,
                    tabsCount = 3,
                    darkTheme = dark,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    repeat(3) { i ->
                        LiquidBottomTab(onClick = { tab = i }) {
                            Icon(tabIcons[i], contentDescription = null, modifier = Modifier.size(24.dp))
                            Text(tabLabels[i], fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // the dialog sits OUTSIDE the recorded screen layer so it can refract it
        LiquidDialog(
            visible = dialogVisible,
            onDismiss = { dialogVisible = false },
            backdrop = screenBackdrop,
            dark = dark,
            title = dialog.title,
            message = dialog.message,
            confirmText = dialog.confirmText,
            dismissText = dialog.dismissText,
            confirmTint = dialog.confirmTint,
            onConfirm = {
                dialogVisible = false
                dialog.onConfirm?.invoke()
            }
        )
    }
}
