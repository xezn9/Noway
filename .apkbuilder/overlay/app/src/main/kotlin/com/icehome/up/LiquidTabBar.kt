package com.icehome.up

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import kotlin.math.roundToInt

/**
 * Barra de pestañas de abajo, con el cristal líquido de Kyant0/AndroidLiquidGlass
 * (biblioteca «backdrop», componente LiquidBottomTabs).
 *
 * Por fuera es una vista normal y tiene los mismos métodos que tenía la antigua `GlassBar`
 * ([select], [setOnSelect], [setSource], [heightFor], [onPageScroll] e [invalidate]), así que
 * `SettingsActivity` casi no cambia. Por dentro es un `ComposeView`.
 *
 * Cómo ve lo que hay debajo: la biblioteca necesita un «backdrop», que es una capa donde se graba
 * el fondo. Aquí el fondo no es Compose, son las páginas de la app (vistas de toda la vida), así
 * que en cada fotograma dibujamos la vista `source` dentro de esa capa ([paintSource]). Cuando el
 * resto de la app llama a `bar.invalidate()` ("lo de debajo ha cambiado") subimos un contador que
 * Compose lee al dibujar, y la capa se vuelve a grabar.
 */
class LiquidTabBar(
    context: Context,
    private val labels: Array<String>,
    iconKinds: IntArray,
    private val baseColor: Int,
    private val textColor: Int,
    private val selectedColor: Int
) : FrameLayout(context) {

    fun interface OnSelect {
        fun onSelect(index: Int)
    }

    private val dp = context.resources.displayMetrics.density

    /** Pestaña elegida. La leen la barra de Compose y [pick]. */
    private val selected: MutableIntState = mutableIntStateOf(0)

    /**
     * Contador de «lo de debajo ha cambiado». Es nulo mientras se construye la vista: el
     * constructor de View puede llamar a [invalidate] antes de que existan nuestros campos.
     */
    private var tick: MutableIntState? = null

    private var source: View? = null
    private var listener: OnSelect? = null
    private val myLoc = IntArray(2)
    private val srcLoc = IntArray(2)

    /** Un icono de la app por pestaña (se pintan igual que antes, solo cambia el contenedor). */
    private val icons: Array<Icons> = Array(labels.size) { Icons(iconKinds[it], textColor) }

    init {
        tick = mutableIntStateOf(0)
        // La cápsula, al hincharse, se sale un poco de su sitio: que la vista no la corte.
        clipChildren = false
        clipToPadding = false
        val cv = ComposeView(context)
        // La app es siempre de izquierda a derecha (ver LiquidToggleView): se fija LTR desde el
        // primer fotograma para que el arrastre de la píldora no quede invertido.
        cv.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TabsContent() }
        }
        addView(cv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    // ------------------------------------------------------------------ API de GlassBar

    /** Alto total de la vista para una barra de [barDp]: la barra más 8 dp de aire arriba y abajo. */
    fun heightFor(barDp: Float): Int = Math.round((barDp + 16f) * dp)

    /** Centro de la pestaña [index] en la pantalla (de ahí sale la onda de [TabReveal]). */
    fun tabCenterX(index: Int): Float {
        val inset = INSET_DP * dp
        val seg = (width - 2f * inset) / labels.size
        return x + inset + seg * (index + 0.5f)
    }

    fun centerY(): Float = y + height / 2f

    fun setSource(v: View?) {
        source = v
        invalidate()
    }

    fun setOnSelect(l: OnSelect?) {
        listener = l
    }

    /** Cambio desde el código (atrás, presentación…): mueve la píldora, pero no avisa. */
    @Suppress("UNUSED_PARAMETER")
    fun select(index: Int, animate: Boolean) {
        if (index in labels.indices) selected.intValue = index
    }

    /**
     * La página se ha desplazado. La barra antigua se encogía al bajar; la de cristal líquido
     * de Kyant0 no tiene ese modo, así que ahora no hace nada.
     */
    @Suppress("UNUSED_PARAMETER")
    fun onPageScroll(y: Int, dy: Int) {
    }

    /** Lo que hay debajo ha cambiado: que el cristal vuelva a mirar. */
    override fun invalidate() {
        super.invalidate()
        tick?.let { it.intValue = it.intValue + 1 }
    }

    // ------------------------------------------------------------------ por dentro

    /** Elige una pestaña (toque o arrastre) y avisa a la app una sola vez. */
    private fun pick(index: Int) {
        if (index == selected.intValue) return
        selected.intValue = index
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        listener?.onSelect(index)
    }

    /** Dibuja las páginas de la app dentro de la capa del cristal, en el mismo sitio que en pantalla. */
    private fun paintSource(canvas: Canvas) {
        val s = source ?: return
        if (!s.isAttachedToWindow || !isAttachedToWindow) return
        getLocationInWindow(myLoc)
        s.getLocationInWindow(srcLoc)
        canvas.save()
        try {
            canvas.translate(
                (srcLoc[0] - myLoc[0] - s.scrollX).toFloat(),
                (srcLoc[1] - myLoc[1] - s.scrollY).toFloat()
            )
            s.draw(canvas)
        } catch (t: Throwable) {
            // Sin fondo que mirar, el cristal queda liso; no es motivo para fallar.
            android.util.Log.w("HyperSpring", "LiquidTabBar: no se pudo leer el fondo", t)
        } finally {
            canvas.restore()
        }
    }

    @Composable
    private fun TabsContent() {
        val backdrop = rememberLayerBackdrop()
        val selectedState = selected
        val tickState = tick
        val selectedIndex = remember(selectedState) { { selectedState.intValue } }
        val base = remember(baseColor) { Color(baseColor) }
        val accent = remember(selectedColor) { Color(selectedColor) }
        val label = remember(textColor) { Color(textColor) }

        Box(Modifier.fillMaxSize()) {
            // Capa invisible donde se graba el fondo (el mismo truco que usa la biblioteca con
            // `alpha(0f).layerBackdrop(...)`): aquí van las páginas de la app.
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(0f)
                    .layerBackdrop(backdrop)
                    .drawBehind {
                        // Leerlo aquí hace que Compose vuelva a dibujar cuando sube el contador.
                        tickState?.intValue
                        drawIntoCanvas { c ->
                            drawRect(base)
                            paintSource(c.nativeCanvas)
                        }
                    }
            )
            LiquidBottomTabs(
                selectedTabIndex = selectedIndex,
                onTabSelected = { pick(it) },
                backdrop = backdrop,
                tabsCount = labels.size,
                darkTheme = true,
                accent = accent,
                container = base.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = INSET_DP.dp)
            ) {
                for (i in labels.indices) {
                    val painter = remember(i) { DrawablePainter(icons[i]) }
                    LiquidBottomTab(onClick = { pick(i) }) {
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        BasicText(
                            text = labels[i],
                            maxLines = 1,
                            style = TextStyle(
                                color = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** Hueco a cada lado: la cápsula, al hincharse al tocarla, crece unos 8 dp hacia fuera. */
        const val INSET_DP = 18f
    }

    /** Pinta un `Drawable` de Android (los iconos de la app) dentro de Compose. */
    private class DrawablePainter(private val d: Drawable) : Painter() {
        override val intrinsicSize: Size = Size.Unspecified

        override fun DrawScope.onDraw() {
            drawIntoCanvas { c ->
                d.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                d.draw(c.nativeCanvas)
            }
        }
    }
}
