package com.example.glassnav

import android.content.Context

object Prefs {
    const val FILE = "glassnav"
    const val BAR = "bar"              // white home bar
    const val BACK = "back"            // edge back gesture
    const val COVER = "cover"          // cover the 3-button nav bar
    const val COVER_WHITE = "cover_white" // cover colour: false = black, true = white

    fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
