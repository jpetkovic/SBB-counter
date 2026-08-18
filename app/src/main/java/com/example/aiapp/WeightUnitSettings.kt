package com.example.aiapp

import android.content.Context

private const val PREFS_NAME = "timer_settings"
private const val KEY_WEIGHT_UNIT = "weight_unit"

enum class WeightUnit(val label: String) {
    KG("kg"),
    LB("lb"),
}

object WeightUnitSettings {

    fun getUnit(context: Context): WeightUnit {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WEIGHT_UNIT, WeightUnit.KG.name)
        return WeightUnit.entries.firstOrNull { it.name == name } ?: WeightUnit.KG
    }

    fun setUnit(context: Context, unit: WeightUnit) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WEIGHT_UNIT, unit.name)
            .apply()
    }
}
