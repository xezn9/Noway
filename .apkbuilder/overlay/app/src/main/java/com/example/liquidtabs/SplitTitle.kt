package com.example.liquidtabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val CHAR_MS = 820
private const val STAGGER_MS = 26

/**
 * Port of SplitTitle.java: a big title that enters letter by letter. Each line is a "window"
 * and the letters rise from under it, rotating a little, one after another.
 */
@Composable
fun SplitTitle(
    text: String,
    delayMs: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 40.sp,
    color: Color = Color.White
) {
    val measurer = rememberTextMeasurer()
    val style = remember(fontSize, color) {
        TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.025f).em
        )
    }
    val total = CHAR_MS + STAGGER_MS * text.length
    val t = remember { Animatable(-1f) }
    LaunchedEffect(text) {
        t.snapTo(-1f)
        delay(delayMs.toLong())
        t.snapTo(0f)
        t.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
    }

    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val layout = remember(text, maxW, style) {
            measurer.measure(text, style, constraints = Constraints(maxWidth = maxW))
        }
        val glyphs = remember(text, style) { text.map { measurer.measure(it.toString(), style) } }
        val hDp = with(LocalDensity.current) { layout.size.height.toDp() }

        Canvas(Modifier.fillMaxWidth().height(hDp)) {
            val tt = t.value
            if (tt < 0f) return@Canvas                       // letters not out yet
            if (tt >= total + STAGGER_MS * layout.lineCount) {
                drawText(layout)                              // all arrived: whole title at once
                return@Canvas
            }
            var k = 0                                         // letter number in the whole title
            for (li in 0 until layout.lineCount) {
                val top = layout.getLineTop(li)
                val lineH = layout.getLineBottom(li) - top
                val start = layout.getLineStart(li)
                val end = layout.getLineEnd(li)
                // The line's "window": what is below it is not seen (letters come out of there).
                clipRect(0f, top - lineH * 0.2f, size.width, top + lineH) {
                    for (i in start until end) {
                        val kk = k++
                        val ch = text[i]
                        if (ch == ' ' || ch == '\n') continue
                        val p = ((tt - kk * STAGGER_MS) / CHAR_MS).coerceIn(0f, 1f)
                        if (p <= 0f) continue
                        val e = EXPO_OUT.transform(p)
                        val box = layout.getBoundingBox(i)
                        val dy = (1f - e) * lineH
                        rotate(14f * (1f - e), pivot = Offset(box.center.x, box.bottom + dy)) {
                            drawText(
                                glyphs[i],
                                topLeft = Offset(box.left, box.top + dy),
                                alpha = minOf(1f, p * 2.5f)
                            )
                        }
                    }
                }
                k++                                           // the gap between lines counts too
            }
        }
    }
}
