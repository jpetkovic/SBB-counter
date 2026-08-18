package com.example.aiapp

import android.content.Context

private const val PREFS_NAME = "timer_settings"
private const val KEY_SAVE_TO_CALENDAR = "save_to_calendar"

object CalendarSettings {

    fun isSaveToCalendarEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SAVE_TO_CALENDAR, true)
    }

    fun setSaveToCalendarEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SAVE_TO_CALENDAR, enabled)
            .apply()
    }
}
