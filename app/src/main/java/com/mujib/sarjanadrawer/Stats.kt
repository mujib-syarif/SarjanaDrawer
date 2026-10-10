package com.mujib.sarjanadrawer

import android.content.Context

object Stats {
    const val STROKES = "strokes"
    const val HEROES = "heroes_placed"
    const val SHOTS = "screenshots"

    private fun prefs(c: Context) = c.getSharedPreferences("stats", Context.MODE_PRIVATE)

    fun inc(c: Context, key: String) {
        val sp = prefs(c)
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
    }

    fun get(c: Context, key: String): Int = prefs(c).getInt(key, 0)
}
