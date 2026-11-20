package com.mfp.filemanager.ui.theme

import com.mfp.filemanager.R

/** Every colour the user can pick as "App color". [argb] is the vivid colour used by glass parts
 *  (folders, tabs, toggles); [overlay] is the Material theme overlay for the classic views. */
object AccentPalette {

    class Swatch(val name: String, val argb: Int, val overlay: Int)

    val swatches: List<Swatch> = listOf(
        Swatch("Blue", 0xFF0A84FF.toInt(), R.style.ThemeOverlay_MFP_Accent_Blue),
        Swatch("Sky", 0xFF32ADE6.toInt(), R.style.ThemeOverlay_MFP_Accent_Sky),
        Swatch("Cyan", 0xFF00C7D9.toInt(), R.style.ThemeOverlay_MFP_Accent_Cyan),
        Swatch("Teal", 0xFF14B8A6.toInt(), R.style.ThemeOverlay_MFP_Accent_Teal),
        Swatch("Mint", 0xFF34D399.toInt(), R.style.ThemeOverlay_MFP_Accent_Mint),
        Swatch("Green", 0xFF30D158.toInt(), R.style.ThemeOverlay_MFP_Accent_Green),
        Swatch("Lime", 0xFFA3D90F.toInt(), R.style.ThemeOverlay_MFP_Accent_Lime),
        Swatch("Yellow", 0xFFFFD60A.toInt(), R.style.ThemeOverlay_MFP_Accent_Yellow),
        Swatch("Amber", 0xFFFFB020.toInt(), R.style.ThemeOverlay_MFP_Accent_Amber),
        Swatch("Orange", 0xFFFF8A1F.toInt(), R.style.ThemeOverlay_MFP_Accent_Orange),
        Swatch("Coral", 0xFFFF6B57.toInt(), R.style.ThemeOverlay_MFP_Accent_Coral),
        Swatch("Red", 0xFFFF453A.toInt(), R.style.ThemeOverlay_MFP_Accent_Red),
        Swatch("Rose", 0xFFFF4D8D.toInt(), R.style.ThemeOverlay_MFP_Accent_Rose),
        Swatch("Pink", 0xFFFF6FB5.toInt(), R.style.ThemeOverlay_MFP_Accent_Pink),
        Swatch("Magenta", 0xFFD946EF.toInt(), R.style.ThemeOverlay_MFP_Accent_Magenta),
        Swatch("Purple", 0xFF9B5CFF.toInt(), R.style.ThemeOverlay_MFP_Accent_Purple),
        Swatch("Violet", 0xFF6650A4.toInt(), R.style.ThemeOverlay_MFP_Accent_Violet),
        Swatch("Indigo", 0xFF5E5CE6.toInt(), R.style.ThemeOverlay_MFP_Accent_Indigo),
        Swatch("Brown", 0xFFA2845E.toInt(), R.style.ThemeOverlay_MFP_Accent_Brown),
        Swatch("Graphite", 0xFF8E8E93.toInt(), R.style.ThemeOverlay_MFP_Accent_Graphite),
    )

    /** Default colour of the app (Blue). */
    val default: Swatch get() = swatches[0]

    fun find(argb: Int): Swatch = swatches.firstOrNull { it.argb == argb } ?: default
}
