package com.example.aiapp

import android.content.Context

object CustomItemsSettings {
    private const val PREFS_NAME = "custom_items"
    private const val MUSCLE_PREFIX = "muscles_"
    private const val EXERCISE_PREFIX = "exercises_"
    private const val HIDDEN_MUSCLE_PREFIX = "hidden_muscles_"
    private const val HIDDEN_EXERCISE_PREFIX = "hidden_exercises_"

    private fun getList(context: Context, key: String): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(key, null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    private fun addToList(context: Context, key: String, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getList(context, key)
        if (name in existing) return
        prefs.edit().putString(key, (existing + name).joinToString("\n")).apply()
    }

    private fun setList(context: Context, key: String, names: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, names.joinToString("\n")).apply()
    }

    fun getCustomMuscles(context: Context, bodyAreaKey: String): List<String> =
        getList(context, MUSCLE_PREFIX + bodyAreaKey)

    fun addCustomMuscle(context: Context, bodyAreaKey: String, name: String) =
        addToList(context, MUSCLE_PREFIX + bodyAreaKey, name)

    fun renameCustomMuscle(context: Context, bodyAreaKey: String, oldName: String, newName: String) {
        val key = MUSCLE_PREFIX + bodyAreaKey
        setList(context, key, getList(context, key).map { if (it == oldName) newName else it })
        migrateExercises(context, oldName, newName)
    }

    fun deleteCustomMuscle(context: Context, bodyAreaKey: String, name: String) {
        val key = MUSCLE_PREFIX + bodyAreaKey
        setList(context, key, getList(context, key).filter { it != name })
        clearExercises(context, name)
    }

    fun getHiddenMuscles(context: Context, bodyAreaKey: String): Set<String> =
        getList(context, HIDDEN_MUSCLE_PREFIX + bodyAreaKey).toSet()

    fun hideMuscle(context: Context, bodyAreaKey: String, name: String) =
        addToList(context, HIDDEN_MUSCLE_PREFIX + bodyAreaKey, name)

    fun getCustomExercises(context: Context, muscleKey: String): List<String> =
        getList(context, EXERCISE_PREFIX + muscleKey)

    fun addCustomExercise(context: Context, muscleKey: String, name: String) =
        addToList(context, EXERCISE_PREFIX + muscleKey, name)

    fun renameCustomExercise(context: Context, muscleKey: String, oldName: String, newName: String) {
        val key = EXERCISE_PREFIX + muscleKey
        setList(context, key, getList(context, key).map { if (it == oldName) newName else it })
    }

    fun deleteCustomExercise(context: Context, muscleKey: String, name: String) {
        val key = EXERCISE_PREFIX + muscleKey
        setList(context, key, getList(context, key).filter { it != name })
    }

    fun getHiddenExercises(context: Context, muscleKey: String): Set<String> =
        getList(context, HIDDEN_EXERCISE_PREFIX + muscleKey).toSet()

    fun hideExercise(context: Context, muscleKey: String, name: String) =
        addToList(context, HIDDEN_EXERCISE_PREFIX + muscleKey, name)

    fun migrateExercises(context: Context, oldMuscleKey: String, newMuscleKey: String) {
        val exercises = getList(context, EXERCISE_PREFIX + oldMuscleKey)
        if (exercises.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(EXERCISE_PREFIX + newMuscleKey, exercises.joinToString("\n"))
            .remove(EXERCISE_PREFIX + oldMuscleKey)
            .apply()
    }

    fun clearExercises(context: Context, muscleKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(EXERCISE_PREFIX + muscleKey)
            .apply()
    }
}
