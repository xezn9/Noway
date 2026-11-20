package com.mfp.filemanager.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * Describes ONE dialog. Build it with the helpers in LiquidDialogs.kt
 * (confirm / info / input / choices) instead of creating it by hand.
 */
data class LiquidDialogSpec(
    val title: String,
    val message: String? = null,
    val confirmText: String? = "OK",
    val dismissText: String? = "Cancel",
    val confirmTint: Color = Color(0xFF0088FF),
    /** non-null => the dialog shows a text field pre-filled with this text */
    val inputInitial: String? = null,
    val inputHint: String? = null,
    /** non-null => the dialog shows a list of tappable choices (no confirm button) */
    val choices: List<String>? = null,
    val cancelable: Boolean = true,
    /** shown as a monospace-free "key: value" block (used by Details) */
    val rows: List<Pair<String, String>>? = null,
    val onConfirm: (input: String) -> Unit = {},
    val onChoice: (index: Int) -> Unit = {},
    val onDismiss: () -> Unit = {}
)

/** Global holder: any Fragment/Activity can show a dialog, MainContainer draws it. */
object LiquidDialogState {
    var current by mutableStateOf<LiquidDialogSpec?>(null)
        internal set

    fun show(spec: LiquidDialogSpec) {
        current = spec
    }

    fun dismiss() {
        current = null
    }
}

/**
 * Draws whatever LiquidDialogState holds. Put it OUTSIDE the Box that has layerBackdrop and pass
 * the screen backdrop, so the glass refracts the whole screen behind the dialog.
 */
@Composable
fun LiquidDialogHost(backdrop: Backdrop, dark: Boolean) {
    // remember the last spec so the exit animation still has content while current == null
    val holder = remember { arrayOfNulls<LiquidDialogSpec>(1) }
    val now = LiquidDialogState.current
    if (now != null) holder[0] = now
    val spec = holder[0] ?: return

    val visible = now != null
    val contentColor = if (dark) Color.White else Color(0xFF1B1B1F)
    val containerColor =
        if (dark) Color(0xFF121212).copy(0.45f) else Color(0xFFFAFAFA).copy(0.45f)
    val buttonSurface = if (dark) Color.White.copy(0.10f) else Color.White.copy(0.30f)
    val fieldSurface = if (dark) Color.White.copy(0.10f) else Color.Black.copy(0.06f)

    fun close(cancelled: Boolean) {
        LiquidDialogState.dismiss()
        if (cancelled) spec.onDismiss()
    }

    BackHandler(enabled = visible && spec.cancelable) { close(true) }

    val panelScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "dialogScale"
    )

    val onScrimTap by rememberUpdatedState { if (spec.cancelable) close(true) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (dark) 0.40f else 0.22f))
                .pointerInput(Unit) { detectTapGestures { onScrimTap() } }
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .padding(32.dp)
                    .widthIn(max = 340.dp)
                    .graphicsLayer {
                        scaleX = panelScale
                        scaleY = panelScale
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(36.dp) },
                        effects = {
                            vibrancy()
                            blur(14f.dp.toPx())
                            lens(16f.dp.toPx(), 32f.dp.toPx())
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    spec.title,
                    color = contentColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                // scrollable body so long messages (release notes) never overflow the screen
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    spec.message?.let {
                        Text(
                            it,
                            color = contentColor.copy(alpha = 0.72f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    spec.rows?.forEach { (k, v) ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(k, color = contentColor.copy(0.55f), fontSize = 12.sp)
                            Text(v, color = contentColor, fontSize = 15.sp)
                        }
                    }
                }

                // ---- text input (Rename) ----
                var inputState by remember(spec) {
                    mutableStateOf(
                        TextFieldValue(
                            spec.inputInitial ?: "",
                            TextRange(0, spec.inputInitial?.substringBeforeLast('.', spec.inputInitial)?.length ?: 0)
                        )
                    )
                }
                if (spec.inputInitial != null) {
                    val focus = remember(spec) { FocusRequester() }
                    LaunchedEffect(spec) { runCatching { focus.requestFocus() } }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(fieldSurface)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        if (inputState.text.isEmpty() && spec.inputHint != null) {
                            Text(spec.inputHint, color = contentColor.copy(0.4f), fontSize = 16.sp)
                        }
                        BasicTextField(
                            value = inputState,
                            onValueChange = { inputState = it },
                            singleLine = true,
                            textStyle = TextStyle(color = contentColor, fontSize = 16.sp),
                            cursorBrush = SolidColor(spec.confirmTint),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                close(false); spec.onConfirm(inputState.text)
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        )
                    }
                }

                // ---- choices list (Theme / Trash retention) ----
                spec.choices?.forEachIndexed { index, label ->
                    LiquidButton(
                        onClick = { close(false); spec.onChoice(index) },
                        backdrop = backdrop,
                        surfaceColor = buttonSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, color = contentColor, fontWeight = FontWeight.Medium)
                    }
                }

                // ---- buttons ----
                val hasDismiss = spec.dismissText != null
                val hasConfirm = spec.confirmText != null && spec.choices == null
                if (hasDismiss || hasConfirm) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (hasDismiss) {
                            LiquidButton(
                                onClick = { close(true) },
                                backdrop = backdrop,
                                surfaceColor = buttonSurface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(spec.dismissText!!, color = contentColor, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (hasConfirm) {
                            LiquidButton(
                                onClick = { close(false); spec.onConfirm(inputState.text) },
                                backdrop = backdrop,
                                tint = spec.confirmTint,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(spec.confirmText!!, color = Color.White, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
