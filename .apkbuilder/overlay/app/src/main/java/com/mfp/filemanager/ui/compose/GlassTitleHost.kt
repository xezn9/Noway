package com.mfp.filemanager.ui.compose

import android.graphics.Color as AColor
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
}

object GlassTitleState {
    val proxies = mutableStateListOf<TitleProxy>()

    private val loc = IntArray(2)
    private val rootLoc = IntArray(2)

    fun isAttached(tv: TextView) = proxies.any { it.view === tv }

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

    Box(modifier.fillMaxSize()) {
        for (p in GlassTitleState.proxies) {
            key(p) { GlassTitle(p, backdrop) }
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
                    blur(6f.dp.toPx())
                    lens(10f.dp.toPx(), 20f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(tint)
                    drawRect(sheen)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = p.text,
            color = textColor,
            fontSize = with(density) { p.textSizePx.toSp() },
            fontWeight = if (p.bold) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
