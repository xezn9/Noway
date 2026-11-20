package com.mfp.filemanager.ui.util

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.WindowManager
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Real full-screen for every screen of the app:
 *  - status bar + navigation bar are hidden (a swipe from the edge shows them for a moment only)
 *  - content is drawn edge to edge, also behind the camera cut-out / punch hole
 *
 * Call [apply] in onCreate (after super.onCreate) and [hideBars] in onWindowFocusChanged.
 */
object Immersive {

    /** Height of the camera cut-out at the top (px). Headers add it to their top padding. */
    @Volatile
    var topInset: Int = 0
        private set

    fun apply(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        // remember the cut-out height (display cut-out only, NOT the bars that we hide anyway)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            topInset = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            ViewCompat.onApplyWindowInsets(v, insets)
        }

        hideBars(activity)
    }

    fun hideBars(activity: Activity) {
        val window = activity.window
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** True when the current app theme has a dark window background (follows the in-app theme setting). */
    fun isDarkTheme(ctx: Context): Boolean {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
        return ColorUtils.calculateLuminance(tv.data) < 0.5
    }
}
