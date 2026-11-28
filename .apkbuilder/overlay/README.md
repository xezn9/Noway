# StickyGlass

Demo project: Backdrop 2.0.0 (Kyant0/AndroidLiquidGlass, tag 2.0.0) with the library source INSIDE the project (`:backdrop`),
modified to support sticky / viscous (metaball) liquid glass.

## What was changed in the library
- `backdrop/.../internal/StickyShaders.kt`  (new) AGSL shader: smooth-union SDF of up to 3 rounded rects + refraction + dispersion + tint + rim light
- `backdrop/.../effects/StickyLens.kt`       (new) `BackdropEffectScope.stickyLens(...)` + `StickyRoundRect`
- Everything else in `:backdrop` is untouched 2.0.0 source (build files trimmed to Android only).

## Demo
`androidApp/.../StickyGlassDemo.kt` - tab bar + search circle melt into one glass surface.
- "تشغيل تلقائي": loops the choreography of the reference video
- "اسحب بإصبعك": drag the bar, it stretches a neck to the circle and springs back

Tuning constants are at the top of `StickyGlassDemo.kt` (StickinessDp, RefractionHeightDp, RefractionAmountDp, DispersionAmount ...).
Needs Android 13+ (AGSL runtime shaders).
