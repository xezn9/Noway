# EcoGif

Android app (Jetpack Compose) with Liquid Glass UI built on `io.github.kyant0:backdrop:2.0.0`.

Features
- Resize GIF (width/height, scale %, keep aspect ratio, Eco mode = half the frames)
  - live preview: every size/scale change is applied to the preview GIF immediately
  - crop box: drag any side / corner (or the inside) to cut the GIF, then resize the cropped area
- Video -> GIF (start, length, fps, width)
- GIF -> Video (MP4 / H.264, scale, repeat count)

Open the `EcoGif` folder in Android Studio (Narwhal or newer, JDK 21), let Gradle sync, run.
Results are saved to Pictures/EcoGif and Movies/EcoGif (no storage permission needed, Android 10+).

Liquid components used (unchanged, from your zips): LiquidBottomTabs, LiquidButton, LiquidSlider,
LiquidToggle, LiquidDialog -> app/src/main/java/com/kyant/backdrop/catalog/
