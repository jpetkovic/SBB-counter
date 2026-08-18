package com.example.aiapp

import android.content.Context

private const val PREFS_NAME = "timer_settings"
private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

object DisplaySettings {

    fun isKeepScreenOnEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_SCREEN_ON, false)
    }

    fun setKeepScreenOnEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_SCREEN_ON, enabled)
            .apply()
    }
}
