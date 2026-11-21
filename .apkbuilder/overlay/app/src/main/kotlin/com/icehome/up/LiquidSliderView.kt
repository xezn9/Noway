package com.icehome.up

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidSlider
import kotlin.math.abs

/**
 * Deslizador de cristal líquido (LiquidSlider de Kyant0) hecho vista normal, con los mismos
 * métodos que el antiguo `Controls.Slider`: [setFraction] (0 a 1, sin avisar) y [setOnChange]
 * (avisa con la fracción y con `done`, que es true al soltar el dedo).
 *
 * El cristal de la bola necesita algo que refractar. Aquí es un fondo liso del color de la
 * tarjeta ([surfaceColor]), que es lo que hay debajo de verdad: no hace falta grabar la pantalla.
 */
class LiquidSliderView(
    context: Context,
    private val trackColor: Int,
    private val fillColor: Int,
    private val surfaceColor: Int
) : FrameLayout(context) {

    fun interface OnChange {
        fun onChange(fraction: Float, done: Boolean)
    }

    private val dp = context.resources.displayMetrics.density
    private val fraction = mutableFloatStateOf(0f)
    private var listener: OnChange? = null
    private var firstTouch: Runnable? = null
    private var downX = 0f
    private var downY = 0f
    private var claimed = false

    init {
        // Para que la cápsula, al agrandarse, no se corte en los bordes de la vista.
        clipChildren = false
        clipToPadding = false
        val cv = ComposeView(context)
        cv.setContent {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val backdrop = rememberLayerBackdrop()
            val surface = Color(surfaceColor)
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                        .background(surface)
                )
                LiquidSlider(
                    value = { fraction.floatValue },
                    onValueChange = { f ->
                        val old = fraction.floatValue
                        fraction.floatValue = f
                        // Un toque al llegar a un extremo, como los deslizadores del sistema.
                        if (old != f && (f <= 0f || f >= 1f)) {
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        listener?.onChange(f, false)
                    },
                    valueRange = 0f..1f,
                    visibilityThreshold = 0.001f,
                    backdrop = backdrop,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    dark = true,
                    accent = Color(fillColor),
                    track = Color(trackColor),
                    onValueChangeFinished = {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        listener?.onChange(fraction.floatValue, true)
                    }
                )
            }
          }
        }
        addView(cv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setOnChange(l: OnChange?) {
        listener = l
    }

    /** Pone la bola en [f] (0 a 1) sin avisar. La bola llega con su muelle. */
    fun setFraction(f: Float) {
        fraction.floatValue = f.coerceIn(0f, 1f)
    }

    /** Se llama una vez al empezar a tocar (la app lo usa para cortar la animación de llenado). */
    fun setOnFirstTouch(r: Runnable?) {
        firstTouch = r
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(Math.round(44f * dp), MeasureSpec.EXACTLY)
        )
    }

    /**
     * Arrastre horizontal: el deslizador; vertical: la lista se desplaza. Cuando el dedo se va de
     * lado, se le dice a la lista que no se lleve el gesto.
     */
    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                claimed = false
                firstTouch?.run()
            }

            MotionEvent.ACTION_MOVE -> {
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                val dx = abs(e.x - downX)
                if (!claimed && dx > slop && dx > abs(e.y - downY)) {
                    claimed = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> claimed = false
        }
        return super.dispatchTouchEvent(e)
    }
}
