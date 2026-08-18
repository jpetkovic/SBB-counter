package com.example.aiapp

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel

internal enum class Phase { COUNTDOWN, DELAY }

internal data class WorkoutCycle(
    val bodyArea: String,
    val muscle: String?,
    val exercise: String?,
    val weightKg: Int,
    val rep: Int,
    val time: Int,
    val delay: Int,
    val startedAt: Long,
) {
    val totalSeconds: Int
        get() = rep * time + maxOf(rep - 1, 0) * delay
}

internal data class CompletedCycle(
    val sessionId: Long,
    val bodyArea: String,
    val muscle: String?,
    val exercise: String?,
    val weightKg: Int,
    val completedRep: Int,
    val time: Int,
    val delay: Int,
    val repetitions: String,
    val startedAt: Long,
)

const val WEIGHT_MIN_KG = 5
const val WEIGHT_MAX_KG = 250
const val FINAL_REPETITIONS_WINDOW_MS = 10_000L

/**
 * Owns the countdown state and its ticking loop independently of any Fragment view,
 * so the countdown keeps running across tab switches, locale changes, and other
 * events that destroy/recreate the TimerFragment's view.
 */
internal class TimerViewModel(application: Application) : AndroidViewModel(application) {

    val handler = Handler(Looper.getMainLooper())

    private val audioManager = application.getSystemService(Application.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    var isRunning = false
    var phase = Phase.COUNTDOWN
    var isInitialDelay = false
    var isAwaitingFinalRepetitions = false

    var repValue = 5
    var timeValue = 30
    var delayValue = 5
    var repetitionsValue = 10

    var originalRep = 0
    var originalTime = 0
    var originalDelay = 0
    var completedSets = 0
    val completedRepetitions = mutableListOf<Int>()
    val completedWeights = mutableListOf<Int>()

    var weightKg = WEIGHT_MIN_KG
    var trainingStartTime: Long? = null

    var selectedBodyAreaTag: String? = null
    var selectedMuscleTag: String? = null
    var selectedExerciseName: String? = null
    val sessionCycles = mutableListOf<WorkoutCycle>()
    val pendingCompletedCycles = mutableListOf<CompletedCycle>()

    var soundThresholdProvider: () -> Int = { 5 }
    var onRefresh: (() -> Unit)? = null
    var onLongBeep: (() -> Unit)? = null
    var onBeep: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onSetCompleted: (() -> Unit)? = null
    var onElapsedTick: (() -> Unit)? = null
    var onFinalRepetitionsWindowEnded: (() -> Unit)? = null

    private val elapsedRunnable = object : Runnable {
        override fun run() {
            onElapsedTick?.invoke()
            handler.postDelayed(this, 1000)
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            val soundThreshold = soundThresholdProvider()
            when (phase) {
                Phase.COUNTDOWN -> {
                    if (timeValue > 0) {
                        timeValue -= 1
                        if (timeValue in 1..soundThreshold) {
                            onBeep?.invoke()
                        }
                    }

                    if (timeValue == 0) {
                        onLongBeep?.invoke()
                        completedSets += 1
                        completedRepetitions.add(repetitionsValue)
                        completedWeights.add(weightKg)
                        onSetCompleted?.invoke()

                        val remainingRep = repValue - 1
                        repValue = remainingRep

                        if (remainingRep <= 0) {
                            stop()
                            onFinished?.invoke()
                            return
                        }

                        timeValue = originalTime
                        phase = Phase.DELAY
                    }
                }
                Phase.DELAY -> {
                    if (delayValue > 0) {
                        delayValue -= 1
                        if (delayValue in 1..soundThreshold) {
                            onBeep?.invoke()
                        }
                    }

                    if (delayValue == 0) {
                        onLongBeep?.invoke()
                        delayValue = originalDelay
                        phase = Phase.COUNTDOWN
                        isInitialDelay = false
                    }
                }
            }
            onRefresh?.invoke()
            handler.postDelayed(this, 1000)
        }
    }

    private val finalRepetitionsRunnable = Runnable {
        isAwaitingFinalRepetitions = false
        onFinalRepetitionsWindowEnded?.invoke()
    }

    fun startFinalRepetitionsWindow() {
        handler.removeCallbacks(finalRepetitionsRunnable)
        isAwaitingFinalRepetitions = true
        handler.postDelayed(finalRepetitionsRunnable, FINAL_REPETITIONS_WINDOW_MS)
    }

    fun cancelFinalRepetitionsWindow() {
        handler.removeCallbacks(finalRepetitionsRunnable)
        isAwaitingFinalRepetitions = false
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        handler.post(tickRunnable)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(tickRunnable)
    }

    fun skipCurrentSet() {
        if (!isRunning) return
        handler.removeCallbacks(tickRunnable)
        when (phase) {
            Phase.COUNTDOWN -> timeValue = 0
            Phase.DELAY -> delayValue = 0
        }
        tickRunnable.run()
    }

    fun startElapsedTicker() {
        handler.removeCallbacks(elapsedRunnable)
        handler.post(elapsedRunnable)
    }

    fun stopElapsedTicker() {
        handler.removeCallbacks(elapsedRunnable)
    }

    fun resetToFreshStartDefaults() {
        repValue = 5
        timeValue = 30
        delayValue = 5
        repetitionsValue = 10
        weightKg = WEIGHT_MIN_KG
    }

    fun requestDuckAudioFocus() {
        if (!DuckSoundSettings.isDuckSoundEnabled(getApplication())) return
        if (audioFocusRequest != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .build()
        audioManager.requestAudioFocus(request)
        audioFocusRequest = request
    }

    fun abandonDuckAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    override fun onCleared() {
        handler.removeCallbacksAndMessages(null)
        abandonDuckAudioFocus()
    }
}
