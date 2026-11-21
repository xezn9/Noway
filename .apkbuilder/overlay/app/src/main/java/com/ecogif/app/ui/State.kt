package com.ecogif.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.ecogif.app.engine.GifInfo
import com.ecogif.app.engine.VideoInfo
import com.ecogif.app.engine.VideoToGif
import kotlinx.coroutines.Job
import kotlin.math.roundToInt

/** One running conversion at a time (shared by all tabs). */
@Stable
class TaskState {
    var busy by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var label by mutableStateOf("")
    var job: Job? = null
}

class DialogData(
    val title: String,
    val message: String,
    val confirmText: String,
    val dismissText: String,
    val confirmTint: Color,
    val onConfirm: (() -> Unit)?,
)

// ------------------------------------------------------------- tab 1: resize GIF

@Stable
class ResizeState {
    var uri by mutableStateOf<Uri?>(null)
    var bytes: ByteArray? = null
    var info by mutableStateOf<GifInfo?>(null)
    var name by mutableStateOf("")
    var sizeBytes by mutableLongStateOf(0L)

    var scale by mutableFloatStateOf(100f)
    var widthText by mutableStateOf("")
    var heightText by mutableStateOf("")
    var keepRatio by mutableStateOf(true)
    var eco by mutableStateOf(false)
    var dither by mutableStateOf(false)

    // crop box, as fractions (0..1) of the original GIF
    var cropL by mutableFloatStateOf(0f)
    var cropT by mutableFloatStateOf(0f)
    var cropR by mutableFloatStateOf(1f)
    var cropB by mutableFloatStateOf(1f)
    var cropping by mutableStateOf(false)

    private val srcW: Int get() = info?.width ?: 1
    private val srcH: Int get() = info?.height ?: 1

    /** The crop rectangle in real GIF pixels. */
    val cropX: Int get() = (cropL * srcW).roundToInt().coerceIn(0, srcW - 1)
    val cropY: Int get() = (cropT * srcH).roundToInt().coerceIn(0, srcH - 1)
    val cropW: Int get() = ((cropR * srcW).roundToInt() - cropX).coerceIn(1, srcW - cropX)
    val cropH: Int get() = ((cropB * srcH).roundToInt() - cropY).coerceIn(1, srcH - cropY)
    val isCropped: Boolean get() = info != null && (cropW != srcW || cropH != srcH)

    val outW: Int? get() = widthText.toIntOrNull()?.takeIf { it in 1..4096 }
    val outH: Int? get() = heightText.toIntOrNull()?.takeIf { it in 1..4096 }

    fun load(uri: Uri, bytes: ByteArray, info: GifInfo, name: String) {
        this.uri = uri
        this.bytes = bytes
        this.info = info
        this.name = name
        this.sizeBytes = bytes.size.toLong()
        cropL = 0f; cropT = 0f; cropR = 1f; cropB = 1f
        cropping = false
        applyScale(100f)
    }

    /** The percentage is relative to the cropped area. */
    fun applyScale(percent: Float) {
        if (info == null) return
        scale = percent
        widthText = (cropW * percent / 100f).roundToInt().coerceAtLeast(1).toString()
        heightText = (cropH * percent / 100f).roundToInt().coerceAtLeast(1).toString()
    }

    /** Moves the crop box and updates the output size right away. */
    fun setCrop(l: Float, t: Float, r: Float, b: Float) {
        val oldW = cropW
        val oldH = cropH
        cropL = l; cropT = t; cropR = r; cropB = b
        if (keepRatio) {
            applyScale(scale)
        } else {
            outW?.let { widthText = (it * cropW / oldW.toFloat()).roundToInt().coerceIn(1, 4096).toString() }
            outH?.let { heightText = (it * cropH / oldH.toFloat()).roundToInt().coerceIn(1, 4096).toString() }
        }
    }

    fun resetCrop() = setCrop(0f, 0f, 1f, 1f)

    fun setWidth(text: String) {
        widthText = text.filter { it.isDigit() }.take(4)
        if (info == null) return
        val w = widthText.toIntOrNull() ?: return
        if (keepRatio && w > 0) {
            heightText = (w * cropH / cropW.toFloat()).roundToInt().coerceAtLeast(1).toString()
            scale = (w * 100f / cropW).coerceIn(5f, 200f)
        }
    }

    fun setHeight(text: String) {
        heightText = text.filter { it.isDigit() }.take(4)
        if (info == null) return
        val h = heightText.toIntOrNull() ?: return
        if (keepRatio && h > 0) {
            widthText = (h * cropW / cropH.toFloat()).roundToInt().coerceAtLeast(1).toString()
            scale = (h * 100f / cropH).coerceIn(5f, 200f)
        }
    }
}

// ------------------------------------------------------------- tab 2: video -> GIF

@Stable
class VideoToGifState {
    var uri by mutableStateOf<Uri?>(null)
    var info by mutableStateOf<VideoInfo?>(null)
    var thumb by mutableStateOf<Bitmap?>(null)
    var name by mutableStateOf("")
    var sizeBytes by mutableLongStateOf(0L)

    var startSec by mutableFloatStateOf(0f)
    var lengthSec by mutableFloatStateOf(3f)
    var fps by mutableFloatStateOf(12f)
    var width by mutableFloatStateOf(480f)
    var dither by mutableStateOf(false)

    val maxWidth: Int get() = minOf(720, info?.width ?: 720).coerceAtLeast(16)
    val durationSec: Float get() = (info?.durationMs ?: 0L) / 1000f

    /** Length that really fits after the start point. */
    val effectiveLengthSec: Float
        get() = lengthSec.coerceAtMost((durationSec - startSec).coerceAtLeast(0.2f))

    val frameCount: Int get() = maxOf(1, (effectiveLengthSec * VideoToGif.effectiveFps(fps.roundToInt())).toInt())

    fun load(uri: Uri, info: VideoInfo, thumb: android.graphics.Bitmap?, name: String, size: Long) {
        this.uri = uri
        this.info = info
        this.thumb = thumb
        this.name = name
        this.sizeBytes = size
        startSec = 0f
        lengthSec = minOf(3f, info.durationMs / 1000f).coerceAtLeast(0.5f)
        fps = 12f
        width = minOf(480, maxWidth).toFloat()
    }
}

// ------------------------------------------------------------- tab 3: GIF -> video

@Stable
class GifToVideoState {
    var uri by mutableStateOf<Uri?>(null)
    var bytes: ByteArray? = null
    var info by mutableStateOf<GifInfo?>(null)
    var name by mutableStateOf("")
    var sizeBytes by mutableLongStateOf(0L)

    var scale by mutableFloatStateOf(100f)
    var loops by mutableFloatStateOf(1f)

    fun load(uri: Uri, bytes: ByteArray, info: GifInfo, name: String) {
        this.uri = uri
        this.bytes = bytes
        this.info = info
        this.name = name
        this.sizeBytes = bytes.size.toLong()
        scale = 100f
        loops = 1f
    }
}
