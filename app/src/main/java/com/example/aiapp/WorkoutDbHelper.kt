package com.example.aiapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WorkoutSession(
    val sessionId: Long,
    val createdAt: Long,
    val exercises: String,
)

data class WorkoutCycleRecord(
    val bodyArea: String?,
    val muscle: String?,
    val exercise: String?,
    val weightKg: Int,
    val rep: Int,
    val timeSec: Int,
    val delaySec: Int,
    val repetitions: String,
    val createdAt: Long,
)

data class ExerciseDefaults(
    val rep: Int,
    val delaySec: Int,
    val timeSec: Int,
    val weightKg: Int,
)

fun formatWorkoutDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

fun buildSessionDetailMessage(context: Context, records: List<WorkoutCycleRecord>): String {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val totalSeconds = records.sumOf { it.rep * it.timeSec + maxOf(it.rep - 1, 0) * it.delaySec }
    val placeholder = context.getString(R.string.value_placeholder)
    return buildString {
        records.forEachIndexed { index, record ->
            append(context.getString(R.string.history_cycle, index + 1))
            append(" (${timeFormat.format(Date(record.createdAt))})\n")
            append(context.getString(R.string.detail_body_area, record.bodyArea ?: placeholder)).append("\n")
            append(context.getString(R.string.detail_muscle, record.muscle ?: placeholder)).append("\n")
            append(context.getString(R.string.detail_exercise, record.exercise ?: placeholder)).append("\n")
            append(context.getString(R.string.detail_weight, record.weightKg)).append("\n")
            append(context.getString(R.string.detail_rep_count, record.rep)).append("\n")
            append(context.getString(R.string.detail_repetitions, record.repetitions)).append("\n")
            append(context.getString(R.string.detail_rest, record.delaySec)).append("\n")
            if (index != records.lastIndex) append("\n")
        }
        append("\n")
        append(context.getString(R.string.training_elapsed, formatWorkoutDuration(totalSeconds)))
    }
}

class WorkoutDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WORKOUTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SESSION_ID INTEGER NOT NULL,
                $COLUMN_BODY_AREA TEXT,
                $COLUMN_MUSCLE TEXT,
                $COLUMN_EXERCISE TEXT,
                $COLUMN_WEIGHT_KG INTEGER NOT NULL,
                $COLUMN_REP INTEGER NOT NULL,
                $COLUMN_TIME_SEC INTEGER NOT NULL,
                $COLUMN_DELAY_SEC INTEGER NOT NULL,
                $COLUMN_REPETITIONS TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PERSONAL_BEST (
                $COLUMN_EXERCISE_KEY TEXT PRIMARY KEY,
                $COLUMN_MAX_WEIGHT_KG INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_EXERCISE_DEFAULTS (
                $COLUMN_EXERCISE_KEY TEXT PRIMARY KEY,
                $COLUMN_DEFAULT_REP INTEGER NOT NULL,
                $COLUMN_DEFAULT_DELAY_SEC INTEGER NOT NULL,
                $COLUMN_DEFAULT_TIME_SEC INTEGER NOT NULL,
                $COLUMN_DEFAULT_WEIGHT_KG INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never drop existing tables here - that would wipe the user's saved history,
        // exercise defaults, and personal bests on every schema bump. Ensure tables
        // exist and add any future column/table migrations guarded by oldVersion checks.
        onCreate(db)
    }

    fun insertWorkout(
        sessionId: Long,
        bodyArea: String?,
        muscle: String?,
        exercise: String?,
        weightKg: Int,
        rep: Int,
        timeSec: Int,
        delaySec: Int,
        repetitions: String,
        createdAt: Long,
    ) {
        val values = ContentValues().apply {
            put(COLUMN_SESSION_ID, sessionId)
            put(COLUMN_BODY_AREA, bodyArea)
            put(COLUMN_MUSCLE, muscle)
            put(COLUMN_EXERCISE, exercise)
            put(COLUMN_WEIGHT_KG, weightKg)
            put(COLUMN_REP, rep)
            put(COLUMN_TIME_SEC, timeSec)
            put(COLUMN_DELAY_SEC, delaySec)
            put(COLUMN_REPETITIONS, repetitions)
            put(COLUMN_CREATED_AT, createdAt)
        }
        writableDatabase.insert(TABLE_WORKOUTS, null, values)
    }

    fun updateMaxWeightIfHigher(exerciseKey: String, weightKg: Int) {
        val db = writableDatabase
        val currentMax = getMaxWeight(exerciseKey)

        when {
            currentMax == null -> {
                val values = ContentValues().apply {
                    put(COLUMN_EXERCISE_KEY, exerciseKey)
                    put(COLUMN_MAX_WEIGHT_KG, weightKg)
                }
                db.insert(TABLE_PERSONAL_BEST, null, values)
            }
            weightKg > currentMax -> {
                val values = ContentValues().apply { put(COLUMN_MAX_WEIGHT_KG, weightKg) }
                db.update(TABLE_PERSONAL_BEST, values, "$COLUMN_EXERCISE_KEY = ?", arrayOf(exerciseKey))
            }
        }
    }

    fun getMaxWeight(exerciseKey: String): Int? {
        return readableDatabase.query(
            TABLE_PERSONAL_BEST,
            arrayOf(COLUMN_MAX_WEIGHT_KG),
            "$COLUMN_EXERCISE_KEY = ?",
            arrayOf(exerciseKey),
            null,
            null,
            null,
        ).use { if (it.moveToFirst()) it.getInt(0) else null }
    }

    fun saveExerciseDefaults(exerciseKey: String, rep: Int, delaySec: Int, timeSec: Int, weightKg: Int) {
        val values = ContentValues().apply {
            put(COLUMN_EXERCISE_KEY, exerciseKey)
            put(COLUMN_DEFAULT_REP, rep)
            put(COLUMN_DEFAULT_DELAY_SEC, delaySec)
            put(COLUMN_DEFAULT_TIME_SEC, timeSec)
            put(COLUMN_DEFAULT_WEIGHT_KG, weightKg)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_EXERCISE_DEFAULTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getExerciseDefaults(exerciseKey: String): ExerciseDefaults? {
        return readableDatabase.query(
            TABLE_EXERCISE_DEFAULTS,
            arrayOf(COLUMN_DEFAULT_REP, COLUMN_DEFAULT_DELAY_SEC, COLUMN_DEFAULT_TIME_SEC, COLUMN_DEFAULT_WEIGHT_KG),
            "$COLUMN_EXERCISE_KEY = ?",
            arrayOf(exerciseKey),
            null,
            null,
            null,
        ).use {
            if (it.moveToFirst()) {
                ExerciseDefaults(rep = it.getInt(0), delaySec = it.getInt(1), timeSec = it.getInt(2), weightKg = it.getInt(3))
            } else {
                null
            }
        }
    }

    fun getSessions(dateFilter: String? = null): List<WorkoutSession> {
        val sessions = mutableListOf<WorkoutSession>()
        val whereClause = if (dateFilter != null) {
            "WHERE date($COLUMN_CREATED_AT / 1000, 'unixepoch', 'localtime') = ?"
        } else {
            ""
        }
        val cursor = readableDatabase.rawQuery(
            """
            SELECT $COLUMN_SESSION_ID,
                   MIN($COLUMN_CREATED_AT) AS first_time,
                   GROUP_CONCAT(DISTINCT COALESCE($COLUMN_EXERCISE, $COLUMN_MUSCLE, $COLUMN_BODY_AREA)) AS exercises
            FROM $TABLE_WORKOUTS
            $whereClause
            GROUP BY $COLUMN_SESSION_ID
            ORDER BY first_time DESC
            """.trimIndent(),
            if (dateFilter != null) arrayOf(dateFilter) else null,
        )
        cursor.use {
            while (it.moveToNext()) {
                sessions.add(
                    WorkoutSession(
                        sessionId = it.getLong(0),
                        createdAt = it.getLong(1),
                        exercises = it.getString(2) ?: "",
                    ),
                )
            }
        }
        return sessions
    }

    fun getWorkoutDates(): Set<String> {
        val dates = mutableSetOf<String>()
        val cursor = readableDatabase.rawQuery(
            "SELECT DISTINCT date($COLUMN_CREATED_AT / 1000, 'unixepoch', 'localtime') FROM $TABLE_WORKOUTS",
            null,
        )
        cursor.use {
            while (it.moveToNext()) {
                dates.add(it.getString(0))
            }
        }
        return dates
    }

    fun deleteSession(sessionId: Long) {
        writableDatabase.delete(TABLE_WORKOUTS, "$COLUMN_SESSION_ID = ?", arrayOf(sessionId.toString()))
    }

    fun getSessionDetails(sessionId: Long): List<WorkoutCycleRecord> {
        val records = mutableListOf<WorkoutCycleRecord>()
        val cursor = readableDatabase.query(
            TABLE_WORKOUTS,
            arrayOf(
                COLUMN_BODY_AREA, COLUMN_MUSCLE, COLUMN_EXERCISE, COLUMN_WEIGHT_KG,
                COLUMN_REP, COLUMN_TIME_SEC, COLUMN_DELAY_SEC, COLUMN_REPETITIONS, COLUMN_CREATED_AT,
            ),
            "$COLUMN_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            "$COLUMN_CREATED_AT ASC",
        )
        cursor.use {
            while (it.moveToNext()) {
                records.add(
                    WorkoutCycleRecord(
                        bodyArea = it.getString(0),
                        muscle = it.getString(1),
                        exercise = it.getString(2),
                        weightKg = it.getInt(3),
                        rep = it.getInt(4),
                        timeSec = it.getInt(5),
                        delaySec = it.getInt(6),
                        repetitions = it.getString(7) ?: "",
                        createdAt = it.getLong(8),
                    ),
                )
            }
        }
        return records
    }

    companion object {
        private const val DATABASE_NAME = "workouts.db"
        private const val DATABASE_VERSION = 8
        private const val TABLE_WORKOUTS = "workouts"
        private const val TABLE_PERSONAL_BEST = "personal_best"
        private const val TABLE_EXERCISE_DEFAULTS = "exercise_defaults"
        private const val COLUMN_EXERCISE_KEY = "exercise_key"
        private const val COLUMN_MAX_WEIGHT_KG = "max_weight_kg"
        private const val COLUMN_DEFAULT_REP = "default_rep"
        private const val COLUMN_DEFAULT_DELAY_SEC = "default_delay_sec"
        private const val COLUMN_DEFAULT_TIME_SEC = "default_time_sec"
        private const val COLUMN_DEFAULT_WEIGHT_KG = "default_weight_kg"
        private const val COLUMN_ID = "id"
        private const val COLUMN_SESSION_ID = "session_id"
        private const val COLUMN_BODY_AREA = "body_area"
        private const val COLUMN_MUSCLE = "muscle"
        private const val COLUMN_EXERCISE = "exercise"
        private const val COLUMN_WEIGHT_KG = "weight_kg"
        private const val COLUMN_REP = "rep"
        private const val COLUMN_TIME_SEC = "time_sec"
        private const val COLUMN_DELAY_SEC = "delay_sec"
        private const val COLUMN_REPETITIONS = "repetitions"
        private const val COLUMN_CREATED_AT = "created_at"
    }
}
