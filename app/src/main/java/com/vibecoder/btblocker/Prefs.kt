package com.vibecoder.btblocker

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "bt_blocker_prefs"
    private const val KEY_BLOCKED = "blocked"
    private const val KEY_HARD_MODE = "hard_mode"
    private const val KEY_PARENTAL_ZONE = "parental_zone"

    fun isBlocked(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLOCKED, false)
    }

    fun setBlocked(context: Context, blocked: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BLOCKED, blocked)
            .apply()
    }

    fun isHardMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HARD_MODE, false)
    }

    fun setHardMode(context: Context, hardMode: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HARD_MODE, hardMode)
            .apply()
    }

    fun isParentalZoneActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PARENTAL_ZONE, false)
    }

    fun setParentalZoneActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PARENTAL_ZONE, active)
            .apply()
    }
}
