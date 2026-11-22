package com.openanim.app

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("enabled", v).apply()
    }

    fun duration(c: Context): Int = sp(c).getInt("duration", 420)
    fun setDuration(c: Context, v: Int) {
        sp(c).edit().putInt("duration", v).apply()
    }
}
