package com.example.aiapp

import android.content.Context
import android.media.ToneGenerator

enum class BeepSound(val toneType: Int) {
    BEEP(ToneGenerator.TONE_PROP_BEEP),
    BEEP2(ToneGenerator.TONE_PROP_BEEP2),
    ACK(ToneGenerator.TONE_PROP_ACK),
    NACK(ToneGenerator.TONE_PROP_NACK),
    PIP(ToneGenerator.TONE_SUP_PIP),
}

private const val PREFS_NAME = "timer_settings"
private const val KEY_BEEP_SOUND = "beep_sound"

object SoundSettings {

    fun getSelected(context: Context): BeepSound {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_BEEP_SOUND, BeepSound.BEEP.name)
        return runCatching { BeepSound.valueOf(name!!) }.getOrDefault(BeepSound.BEEP)
    }

    fun setSelected(context: Context, sound: BeepSound) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BEEP_SOUND, sound.name)
            .apply()
    }
}
