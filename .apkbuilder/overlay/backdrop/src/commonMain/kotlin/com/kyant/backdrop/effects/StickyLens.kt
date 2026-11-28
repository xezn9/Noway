package com.kyant.backdrop.effects

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.util.fastCoerceAtLeast
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.internal.StickyLensShaderString
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.cos
import kotlin.math.sin

/**
 * A rounded rectangle taking part in a sticky merge.
 * All values are in pixels, relative to the top-left corner of the element that draws the backdrop.
 */
@Immutable
class StickyRoundRect(
    val centerX: Float,
    val centerY: Float,
    val halfWidth: Float,
    val halfHeight: Float,
    val cornerRadius: Float = minOf(halfWidth, halfHeight)
)

private const val StickyMaxShapes = 3
private const val StickyFar = -100000f

/**
 * Sticky / metaball version of [lens].
 *
 * Draws ONE glass surface for the smooth union of up to 3 [StickyRoundRect]s.
 * Where the shapes are close they melt into each other with a viscous neck.
 * The element drawing the backdrop must be big enough to contain every shape.
 *
 * @param smoothness blend distance in px. Bigger = stickier (neck forms from a larger gap).
 * @param refractionHeight how deep (px) from the edge the refraction reaches.
 * @param refractionAmount how strong (px) the refraction bends at the very edge.
 * @param chromaticAberration radial dispersion strength (0 = off, ~0.25 = like the reference video).
 */
fun BackdropEffectScope.stickyLens(
    shapes: List<StickyRoundRect>,
    smoothness: Float,
    refractionHeight: Float,
    refractionAmount: Float,
    chromaticAberration: Float = 0f,
    tint: Color = Color.Transparent,
    rimWidth: Float = 2f,
    rimAlpha: Float = 0.9f,
    lightAngle: Float = 45f
) {
    if (!isRuntimeShaderSupported()) return
    if (shapes.isEmpty() || refractionHeight <= 0f) return

    if (padding > 0f) {
        padding = (padding - refractionHeight).fastCoerceAtLeast(0f)
    }
    val pad = padding
    val count = minOf(shapes.size, StickyMaxShapes)
    val angle = lightAngle * (3.1415927f / 180f)
    val argb = tint.toArgb()
    val tintA = ((argb shr 24) and 0xFF) / 255f
    val tintR = ((argb shr 16) and 0xFF) / 255f
    val tintG = ((argb shr 8) and 0xFF) / 255f
    val tintB = (argb and 0xFF) / 255f

    runtimeShaderEffect("StickyLens", StickyLensShaderString, "content") {
        setFloatUniform("offset", -pad, -pad)
        setFloatUniform("shapeCount", count.toFloat())
        setFloatUniform("smoothness", smoothness.fastCoerceAtLeast(0.001f))
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", refractionAmount)
        setFloatUniform("chromaticAberration", chromaticAberration)
        setFloatUniform("lightDir", cos(angle), sin(angle))
        setFloatUniform("tint", tintR, tintG, tintB, tintA)
        setFloatUniform("rimWidth", rimWidth.fastCoerceAtLeast(0.001f))
        setFloatUniform("rimAlpha", rimAlpha)

        val r = FloatArray(3) { 1f }
        for (i in 0 until StickyMaxShapes) {
            val s = if (i < count) shapes[i] else null
            val name = "shape$i"
            if (s != null) {
                setFloatUniform(name, s.centerX, s.centerY, s.halfWidth, s.halfHeight)
                r[i] = s.cornerRadius
            } else {
                setFloatUniform(name, StickyFar, StickyFar, 1f, 1f)
            }
        }
        setFloatUniform("radii", r[0], r[1], r[2])
    }
}
