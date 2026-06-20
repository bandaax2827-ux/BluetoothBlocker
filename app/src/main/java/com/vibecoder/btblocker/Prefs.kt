package com.vibecoder.btblocker

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "bt_blocker_prefs"
    private const val KEY_BLOCKED = "blocked"
    private const val KEY_HARD = "hard_mode"

    private fun p(c: Context): SharedPreferences =
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isBlocked(c: Context): Boolean = p(c).getBoolean(KEY_BLOCKED, false)
    fun setBlocked(c: Context, v: Boolean) = p(c).edit().putBoolean(KEY_BLOCKED, v).apply()

    fun isHardMode(c: Context): Boolean = p(c).getBoolean(KEY_HARD, false)
    fun setHardMode(c: Context, v: Boolean) = p(c).edit().putBoolean(KEY_HARD, v).apply()
}