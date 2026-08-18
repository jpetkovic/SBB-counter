package com.example.aiapp

import android.content.Context

private const val PREFS_NAME = "timer_settings"
private const val KEY_DUCK_SOUND = "duck_sound_enabled"

object DuckSoundSettings {

    fun isDuckSoundEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DUCK_SOUND, true)
    }

    fun setDuckSoundEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DUCK_SOUND, enabled)
            .apply()
    }
}
