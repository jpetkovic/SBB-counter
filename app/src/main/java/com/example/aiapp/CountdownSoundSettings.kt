package com.example.aiapp

import android.content.Context

private const val PREFS_NAME = "timer_settings"
private const val KEY_SOUND_THRESHOLD = "sound_threshold"

const val SOUND_THRESHOLD_MIN = 1
const val SOUND_THRESHOLD_MAX = 10
const val SOUND_THRESHOLD_DEFAULT = 5

object CountdownSoundSettings {

    fun getThreshold(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SOUND_THRESHOLD, SOUND_THRESHOLD_DEFAULT)
    }

    fun setThreshold(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SOUND_THRESHOLD, value)
            .apply()
    }
}
