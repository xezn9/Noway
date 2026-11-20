package com.mfp.filemanager.ui.views

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.CompoundButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.mfp.filemanager.ui.util.Immersive

/**
 * Drop-in replacement for SwitchMaterial in XML: same `android:text`, `isChecked` and
 * `setOnCheckedChangeListener { _, isChecked -> }`, but the switch is the Backdrop LiquidToggle.
 * Programmatic `isChecked = x` does not fire the listener (only the user does).
 */
class LiquidSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var checkedState by mutableStateOf(false)
    private var textState by mutableStateOf("")
    private var listener: ((CompoundButton?, Boolean) -> Unit)? = null

    private val textColorInt: Int
    private val windowBg: Int
    private val dark: Boolean = Immersive.isDarkTheme(context)

    init {
        attrs?.let {
            val a = context.obtainStyledAttributes(it, intArrayOf(android.R.attr.text))
            try { textState = a.getText(0)?.toString() ?: "" } finally { a.recycle() }
        }
        textColorInt = themeColor(android.R.attr.textColorPrimary, if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        windowBg = themeColor(android.R.attr.colorBackground, if (dark) 0xFF121212.toInt() else 0xFFFFFFFF.toInt())
    }

    var isChecked: Boolean
        get() = checkedState
        set(value) { checkedState = value }

    var text: CharSequence
        get() = textState
        set(value) { textState = value.toString() }

    fun setOnCheckedChangeListener(l: ((CompoundButton?, Boolean) -> Unit)?) {
        listener = l
    }

    private fun themeColor(attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true) && tv.type in
            TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) tv.data
        else if (tv.resourceId != 0) try { context.getColor(tv.resourceId) } catch (e: Exception) { fallback }
        else fallback
    }

    @Composable
    override fun Content() {
        // the toggle's glass thumb refracts this (the row background)
        val scene = rememberLayerBackdrop()
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .matchParentSize()
                    .layerBackdrop(scene)
                    .background(Color(windowBg))
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = textState,
                    color = Color(textColorInt),
                    fontSize = 16.sp,
                    // tapping the label flips the switch too (the toggle itself handles its own taps)
                    modifier = Modifier
                        .weight(1f)
                        .clickable(interactionSource = null, indication = null) {
                            val v = !checkedState
                            checkedState = v
                            listener?.invoke(null, v)
                        }
                )
                LiquidToggle(
                    selected = { checkedState },
                    onSelect = {
                        checkedState = it
                        listener?.invoke(null, it)
                    },
                    backdrop = scene,
                    dark = dark
                )
            }
        }
    }
}
