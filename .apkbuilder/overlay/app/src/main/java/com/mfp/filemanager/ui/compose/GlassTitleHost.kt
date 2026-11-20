package com.mfp.filemanager.ui.compose

import android.graphics.Color as AColor
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/**
 * One page title (a real TextView somewhere in a header). The TextView stays in the layout (so
 * the header keeps its size / position and the pager swipes it around) but is invisible;
 * the Compose overlay draws a real Backdrop glass capsule + the text exactly on top of it.
 */
class TitleProxy(val view: TextView) {
    var text by mutableStateOf("")
    var left by mutableIntStateOf(0)
    var top by mutableIntStateOf(0)
    var width by mutableIntStateOf(0)
    var height by mutableIntStateOf(0)
    var alpha by mutableFloatStateOf(0f)
    var textSizePx by mutableFloatStateOf(0f)
    var bold by mutableStateOf(false)
    var textColor by mutableIntStateOf(AColor.WHITE)

    /** ZArchiver-style path line under the title ("" = none). Already cut with a leading "…" to fit. */
    var subtitle by mutableStateOf("")
    var subtitleSizePx by mutableFloatStateOf(0f)

    // cache for the "…rage/emulated/0" cut, so we don't re-measure the path every frame
    var subSrc: String = ""
    var subMax: Float = -1f
    var subOut: String = ""
}

object GlassTitleState {
    val proxies = mutableStateListOf<TitleProxy>()

    private val loc = IntArray(2)
    private val rootLoc = IntArray(2)

    fun isAttached(tv: TextView) = proxies.any { it.view === tv }

    /** The Toolbar's own subtitle TextView (null when the toolbar has no subtitle). */
    fun findSubtitleView(bar: androidx.appcompat.widget.Toolbar, title: TextView): TextView? {
        val sub = bar.subtitle?.toString().orEmpty()
        if (sub.isEmpty()) return null
        for (i in 0 until bar.childCount) {
            val c = bar.getChildAt(i)
            if (c is TextView && c !== title && c.text.toString() == sub) return c
        }
        return null
    }

    /** "/storage/emulated/0/Download/x" -> "…rage/emulated/0/Download/x" (cut from the START, like ZArchiver). */
    private fun ellipsizeStart(p: TitleProxy, sv: TextView, maxWidth: Float): String {
        val src = sv.text.toString()
        val max = maxWidth.coerceAtLeast(0f)
        if (src == p.subSrc && kotlin.math.abs(max - p.subMax) < 1f) return p.subOut
        val paint = sv.paint
        var out = src
        if (paint.measureText(src) > max) {
            var fitted = false
            var cut = 1
            while (cut < src.length) {
                val cand = "…" + src.substring(cut)
                if (paint.measureText(cand) <= max) { out = cand; fitted = true; break }
                cut++
            }
            if (!fitted) out = "…"
        }
        p.subSrc = src
        p.subMax = max
        p.subOut = out
        return out
    }

    /** Called from GlassHeader for the title TextView of every screen. */
    fun attach(tv: TextView) {
        if (isAttached(tv)) return
        val proxy = TitleProxy(tv)
        proxies.add(proxy)
        tv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                proxies.remove(proxy)
                tv.removeOnAttachStateChangeListener(this)
            }
        })
    }

    /** Copies position / text / alpha of every title TextView into Compose state (runs every frame). */
    fun sync(composeRoot: View) {
        if (proxies.isEmpty()) return
        composeRoot.getLocationInWindow(rootLoc)
        for (p in proxies.toList()) {
            val v = p.view
            if (!v.isAttachedToWindow) continue
            v.getLocationInWindow(loc)
            p.left = loc[0] - rootLoc[0]
            p.top = loc[1] - rootLoc[1]
            p.width = v.width
            p.height = v.height
            p.text = v.text?.toString() ?: ""
            p.textSizePx = v.textSize

            // Fit the capsule to the text itself (not to the whole TextView, which can be much
            // wider: centred / match_parent titles, pager neighbours leaking in from the side).
            var capLeft = p.left.toFloat()
            var capWidth = v.width.toFloat()
            val lay = v.layout
            if (lay != null && lay.lineCount > 0 && p.text.isNotEmpty()) {
                val textLeft = v.compoundPaddingLeft + lay.getLineLeft(0)
                val w = lay.getLineWidth(0) + v.paddingLeft + v.paddingRight
                if (w < v.width) {
                    capWidth = w
                    capLeft += textLeft - v.paddingLeft
                }
            }

            // Toolbar titles (back button + menu icons, RTL, long names) end up laid out partly
            // off-screen. Centre the capsule between the back button and the menu icons and keep
            // it inside the screen.
            val bar = v.parent as? androidx.appcompat.widget.Toolbar
            val subV = if (bar != null) findSubtitleView(bar, v) else null
            var subtitleText = ""
            var capTop = p.top.toFloat()
            var capHeight = v.height.toFloat()
            if (bar != null && bar.width > 0) {
                val density = v.resources.displayMetrics.density
                val gap = 8f * density
                bar.getLocationInWindow(loc)
                val barLeft = (loc[0] - rootLoc[0]).toFloat()
                val barWidth = bar.width.toFloat()
                val barCenter = barLeft + barWidth / 2f
                var minX = barLeft
                var maxX = barLeft + barWidth
                for (i in 0 until bar.childCount) {
                    val c = bar.getChildAt(i)
                    if (c === v || c === subV || c.visibility != View.VISIBLE || c.width <= 0) continue
                    val cl = barLeft + c.left
                    val cr = barLeft + c.right
                    if ((cl + cr) / 2f < barCenter) minX = maxOf(minX, cr) else maxX = minOf(maxX, cl)
                }
                minX += gap
                maxX -= gap
                val hPad = (v.paddingLeft + v.paddingRight).toFloat()
                val triW = if (subV != null) 11f * density else 0f // corner triangle (7dp) + gap (4dp)
                val titleW = v.paint.measureText(p.text) * 1.08f
                val subW = if (subV != null) subV.paint.measureText(subV.text.toString()) * 1.04f + triW else 0f
                val wanted = maxOf(titleW, subW) + hPad
                capWidth = minOf(wanted, maxOf(maxX - minX, 0f))
                capLeft = (barCenter - capWidth / 2f).coerceIn(minX, maxOf(minX, maxX - capWidth))

                if (subV != null) {
                    // two lines (name + path): the capsule wraps both of them
                    subtitleText = ellipsizeStart(p, subV, capWidth - hPad - triW)
                    subV.getLocationInWindow(loc)
                    val sTop = (loc[1] - rootLoc[1]).toFloat()
                    val sBottom = sTop + subV.height
                    val tTop = p.top.toFloat()
                    val tBottom = tTop + v.height
                    val vPad = 6f * density
                    capTop = minOf(tTop, sTop) - vPad
                    capHeight = maxOf(tBottom, sBottom) + vPad - capTop
                }
            }
            p.subtitle = subtitleText
            p.subtitleSizePx = subV?.textSize ?: 0f
            p.top = capTop.toInt()
            p.height = capHeight.toInt()
            p.left = capLeft.toInt()
            p.width = capWidth.toInt()
            p.bold = v.typeface?.isBold == true
            p.textColor = v.currentTextColor or (0xFF shl 24)

            // effective alpha = alpha of all parents (page fade-in, header drop-in, ...).
            // the TextView's own alpha is 0 on purpose, so it is not part of this.
            var a = if (v.visibility == View.VISIBLE) 1f else 0f
            var par = v.parent
            while (par is View) {
                a *= par.alpha
                if (par.visibility != View.VISIBLE) a = 0f
                par = par.parent
            }
            p.alpha = a
        }
    }
}

/**
 * Put this OUTSIDE the layerBackdrop box (like the dialogs) so the glass refracts the page
 * content (lists, cards) that scrolls under the header.
 */
@Composable
fun GlassTitleOverlay(backdrop: Backdrop, modifier: Modifier = Modifier) {
    val view = LocalView.current
    DisposableEffect(view) {
        val l = ViewTreeObserver.OnPreDrawListener {
            GlassTitleState.sync(view)
            true
        }
        view.viewTreeObserver.addOnPreDrawListener(l)
        onDispose {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(l)
        }
    }

    // Positions are absolute left/top pixels: force LTR, otherwise (RTL languages) Compose starts
    // every capsule at the right edge and the title ends up shifted / cut off.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        Box(modifier.fillMaxSize()) {
            for (p in GlassTitleState.proxies) {
                key(p) { GlassTitle(p, backdrop) }
            }
        }
    }
}

@Composable
private fun GlassTitle(p: TitleProxy, backdrop: Backdrop) {
    if (p.width <= 0 || p.height <= 0) return
    val density = LocalDensity.current
    val textColor = Color(p.textColor)
    val lightText = textColor.luminance() > 0.5f // light text => dark theme => dark glass
    val tint = if (lightText) Color(0xFF121212).copy(0.40f) else Color(0xFFFAFAFA).copy(0.40f)
    val sheen = if (lightText) Color.White.copy(0.06f) else Color.White.copy(0.18f)

    Box(
        Modifier
            .graphicsLayer {
                translationX = p.left.toFloat()
                translationY = p.top.toFloat()
                alpha = p.alpha
            }
            .size(
                with(density) { p.width.toDp() },
                with(density) { p.height.toDp() }
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    val glassBlur = com.mfp.filemanager.ui.theme.GlassConfig.blurDp(6f)
                    if (glassBlur > 0.01f) blur(glassBlur.dp.toPx())
                    val glassLensH = com.mfp.filemanager.ui.theme.GlassConfig.lensDp(10f)
                    val glassLensA = com.mfp.filemanager.ui.theme.GlassConfig.lensDp(20f)
                    if (glassLensH > 0.01f && glassLensA > 0.01f) lens(glassLensH.dp.toPx(), glassLensA.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(com.mfp.filemanager.ui.theme.GlassConfig.surface(tint))
                    drawRect(com.mfp.filemanager.ui.theme.GlassConfig.surface(sheen))
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (p.subtitle.isEmpty()) {
            Text(
                text = p.text,
                color = textColor,
                fontSize = with(density) { p.textSizePx.toSp() },
                fontWeight = if (p.bold) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            val subColor = textColor.copy(alpha = 0.62f)
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = p.text,
                    color = textColor,
                    fontSize = with(density) { p.textSizePx.toSp() },
                    fontWeight = if (p.bold) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CornerTriangle(subColor, 7.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = p.subtitle,
                        color = subColor,
                        fontSize = with(density) { p.subtitleSizePx.toSp() },
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/** The small "◢" in front of the path: tells you that tapping it opens the parent folders. */
@Composable
private fun CornerTriangle(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w, 0f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path, color)
    }
}
