package com.mfp.filemanager.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import coil.dispose
import coil.load
import coil.request.CachePolicy
import coil.request.videoFrameMicros
import coil.size.Scale
import com.google.android.material.imageview.ShapeableImageView
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.ui.adapters.PdfFetcher
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * Single entry point used by every adapter:  GlassFileIcon.bind(imageView, file, seed)
 *  - folders / apk / zip / rar / txt / dex / xml / json / other files -> glass badge (own shape per type)
 *  - images / videos / audio / pdf -> real thumbnail inside a glass frame (glass badge while loading)
 */
object GlassFileIcon {

    private const val CORNER = 0.28f

    private val roundOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val rad = min(view.width, view.height) * CORNER
            outline.setRoundRect(0, 0, view.width, view.height, rad)
        }
    }

    /** Real app icon taken from the apk itself; glass APK badge while loading / if unreadable. */
    private fun bindApk(iv: ImageView, file: FileModel, ext: String) {
        val key = ApkIconLoader.key(file.path, file.dateModified)
        iv.tag = key
        fun show(bmp: Bitmap) {
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.setPadding(2, 2, 2, 2)
            iv.setImageBitmap(bmp)
        }
        val cached = ApkIconLoader.cached(key)
        if (cached != null) {
            show(cached)
            return
        }
        iv.setImageDrawable(GlassFileIconDrawable(FileKind.APK, ext))
        ApkIconLoader.load(iv.context, file.path, key) { bmp ->
            if (iv.tag == key) show(bmp) // row may have been recycled meanwhile
        }
    }

    fun bind(iv: ImageView, file: FileModel, thumbnailSeed: Long) {
        iv.tag = null
        iv.dispose() // cancel a thumbnail request of the previous row that used this view
        iv.colorFilter = null
        iv.setPadding(0, 0, 0, 0)
        iv.setBackgroundColor(Color.TRANSPARENT)
        iv.scaleType = ImageView.ScaleType.CENTER
        iv.foreground = null
        if (iv !is ShapeableImageView) {
            iv.outlineProvider = roundOutline
            iv.clipToOutline = false
        }

        val ext = FileKind.extensionOf(file.name)
        val kind = FileKind.of(file.name, file.isDirectory, file.type)

        if (kind == FileKind.APK && !file.isDirectory) {
            bindApk(iv, file, ext)
            return
        }

        val thumbnail = !file.isDirectory && when {
            file.type == FileType.IMAGE || file.type == FileType.VIDEO || file.type == FileType.AUDIO -> true
            kind == FileKind.PDF -> true
            else -> false
        }

        if (!thumbnail) {
            iv.setImageDrawable(GlassFileIconDrawable(kind, ext))
            return
        }

        val isPdf = kind == FileKind.PDF
        val badge = GlassFileIconDrawable(kind, ext)
        iv.load(File(file.path)) {
            if (isPdf) fetcherFactory(PdfFetcher.Factory())
            size(144, 144)
            bitmapConfig(Bitmap.Config.RGB_565)

            val key = "${file.path}_${file.dateModified}"
            memoryCacheKey(key)
            diskCacheKey(key)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            crossfade(false)
            scale(if (isPdf) Scale.FIT else Scale.FILL)

            if (file.type == FileType.VIDEO) {
                val micros = file.hashCode().toLong() + thumbnailSeed
                videoFrameMicros(abs(micros) % 60_000_000)
            }

            placeholder(badge)
            error(GlassFileIconDrawable(kind, ext))

            listener(
                onError = { _, _ ->
                    iv.scaleType = ImageView.ScaleType.CENTER
                    iv.foreground = null
                    if (iv !is ShapeableImageView) iv.clipToOutline = false
                },
                onSuccess = { _, _ ->
                    iv.scaleType = ImageView.ScaleType.CENTER_CROP
                    iv.colorFilter = null
                    iv.setPadding(0, 0, 0, 0)
                    iv.setBackgroundColor(Color.TRANSPARENT)
                    if (iv !is ShapeableImageView) iv.clipToOutline = true
                    iv.foreground = GlassFrameDrawable(kind.tint)
                }
            )
        }
    }
}

/** Glass rim + soft gloss laid over a real thumbnail (used as ImageView.foreground). */
class GlassFrameDrawable(private val tint: Int) : Drawable() {
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var radius = 0f

    override fun onBoundsChange(b: android.graphics.Rect) {
        super.onBoundsChange(b)
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val sw = maxOf(1.2f, w * 0.04f)
        rim.strokeWidth = sw
        rect.set(b.left + sw / 2f, b.top + sw / 2f, b.right - sw / 2f, b.bottom - sw / 2f)
        radius = min(w, h) * 0.28f
        rim.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            intArrayOf(Color.argb(0xF0, 255, 255, 255), Color.argb(0x22, 255, 255, 255), Color.argb(0x90, 255, 255, 255)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        gloss.shader = LinearGradient(
            0f, b.top.toFloat(), 0f, b.top + h * 0.5f,
            Color.argb(0x55, 255, 255, 255), Color.argb(0x00, 255, 255, 255), Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        canvas.drawRoundRect(rect, radius, radius, gloss)
        canvas.drawRoundRect(rect, radius, radius, rim)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
