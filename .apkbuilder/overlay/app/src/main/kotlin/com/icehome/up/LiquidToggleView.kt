package com.icehome.up

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidToggle

/**
 * Interruptor de cristal líquido hecho vista normal, con los mismos métodos que el antiguo
 * `Controls.Toggle`: [setChecked] (sin avisar), [toggle] (cambia y avisa, para la fila entera) y
 * [setOnChange] (avisa cuando lo cambia el usuario).
 *
 * Como en [LiquidSliderView], el cristal de la bola refracta un fondo liso del color de la tarjeta.
 */
class LiquidToggleView(
    context: Context,
    private val onColor: Int,
    private val offColor: Int,
    private val surfaceColor: Int
) : FrameLayout(context) {

    fun interface OnChange {
        fun onChange(on: Boolean)
    }

    private val dp = context.resources.displayMetrics.density
    private val checked = mutableStateOf(false)
    private var listener: OnChange? = null

    init {
        clipChildren = false
        clipToPadding = false
        val cv = ComposeView(context)
        cv.setContent {
          // La app es siempre de izquierda a derecha. Sin esto, en un móvil con idioma de derecha a
          // izquierda Compose empieza en RTL y luego cambia a LTR, y el arrastre se queda invertido.
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
                LiquidToggle(
                    selected = { checked.value },
                    onSelect = { on -> change(on, true) },
                    backdrop = backdrop,
                    modifier = Modifier.align(Alignment.Center),
                    accent = Color(onColor),
                    track = Color(offColor)
                )
            }
          }
        }
        addView(cv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setOnChange(l: OnChange?) {
        listener = l
    }

    fun isChecked(): Boolean = checked.value

    /** Pone el estado sin avisar. [animate] no hace falta: la bola siempre llega con su muelle. */
    @Suppress("UNUSED_PARAMETER")
    fun setChecked(on: Boolean, animate: Boolean) {
        checked.value = on
    }

    /** Desde la fila o el propio interruptor: cambia y avisa. */
    fun toggle(user: Boolean) {
        change(!checked.value, user)
    }

    private fun change(on: Boolean, user: Boolean) {
        if (on == checked.value) return
        checked.value = on
        // Android 14+ tiene una vibración distinta para encender y para apagar: se nota sin mirar.
        if (user) {
            performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 34) {
                    if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
            )
        }
        listener?.onChange(on)
    }

    /** 80 x 44 dp: la pista mide 64 x 28; la bola, al agrandarse al tocarla, se sale 8 dp por cada lado y no debe cortarse. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(Math.round(80f * dp), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(Math.round(44f * dp), MeasureSpec.EXACTLY)
        )
    }
}
