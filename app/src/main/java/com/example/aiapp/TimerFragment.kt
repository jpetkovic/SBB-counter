package com.example.aiapp

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.Editable
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.ChipGroup

private fun formatMinutesSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private const val REPEAT_INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_START_MS = 350L
private const val REPEAT_INTERVAL_MIN_MS = 40L
private const val REPEAT_INTERVAL_STEP_MS = 25L
private const val BODY_AREA_UPPER = "upper"
private const val BODY_AREA_CORE = "core"
private const val BODY_AREA_LOWER = "lower"

private data class CalendarEventData(
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
)

private class RepeatingClickListener(private val action: () -> Unit) : View.OnTouchListener {
    private val handler = Handler(Looper.getMainLooper())
    private var repeatCount = 0

    private val repeatRunnable = object : Runnable {
        override fun run() {
            action()
            val interval = (REPEAT_INTERVAL_START_MS - repeatCount * REPEAT_INTERVAL_STEP_MS)
                .coerceAtLeast(REPEAT_INTERVAL_MIN_MS)
            repeatCount++
            handler.postDelayed(this, interval)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.isPressed = true
                repeatCount = 0
                action()
                handler.postDelayed(repeatRunnable, REPEAT_INITIAL_DELAY_MS)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.isPressed = false
                handler.removeCallbacks(repeatRunnable)
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
            }
        }
        return true
    }
}

private class SpinField(
    private val display: TextView,
    private val buttonUp: Button,
    private val buttonDown: Button,
    private val minValue: Int,
    private val maxValue: Int,
    initialValue: Int,
    private val displayFormatter: (Int) -> String = { it.toString() },
    onChanged: (Int) -> Unit = {},
) {
    var value: Int = initialValue
        set(newValue) {
            field = newValue.coerceIn(minValue, maxValue)
            display.text = displayFormatter(field)
        }

    fun clearDisplay() {
        display.text = ""
    }

    init {
        display.text = displayFormatter(value)
        buttonUp.setOnTouchListener(
            RepeatingClickListener {
                value++
                onChanged(value)
            },
        )
        buttonDown.setOnTouchListener(
            RepeatingClickListener {
                value--
                onChanged(value)
            },
        )
    }
}

class TimerFragment : Fragment(R.layout.fragment_timer) {

    private val viewModel: TimerViewModel by activityViewModels()

    private val handler = Handler(Looper.getMainLooper())
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)

    private var pendingCalendarEvent: CalendarEventData? = null
    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val event = pendingCalendarEvent
        pendingCalendarEvent = null
        if (event != null) {
            if (results[Manifest.permission.WRITE_CALENDAR] == true) {
                insertCalendarEvent(event)
            } else {
                Toast.makeText(requireContext(), getString(R.string.calendar_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private lateinit var repField: SpinField
    private lateinit var timeField: SpinField
    private lateinit var delayField: SpinField
    private lateinit var repetitionsField: SpinField
    private lateinit var buttonStart: Button
    private lateinit var buttonSkipSet: Button
    private lateinit var buttonStop: Button
    private lateinit var buttonEndWorkout: Button
    private lateinit var editWeightValue: EditText
    private lateinit var textMaxWeight: TextView
    private var currentMaxWeight: Int? = null
    private lateinit var textTrainingElapsed: TextView
    private lateinit var textMuscleExercise: TextView
    private lateinit var groupRepetitions: View
    private lateinit var textRepetitionsLabel: TextView
    private lateinit var rootView: View
    private lateinit var dbHelper: WorkoutDbHelper
    private lateinit var exerciseChipContainer: LinearLayout
    private lateinit var chipExerciseAdd: CheckableChip

    private val exercisesByMuscle: Map<String, List<String>> by lazy {
        mapOf(
            getString(R.string.muscle_back) to listOf(
                getString(R.string.exercise_back_1),
                getString(R.string.exercise_back_2),
                getString(R.string.exercise_back_3),
                getString(R.string.exercise_back_4),
                getString(R.string.exercise_back_5),
            ),
            getString(R.string.muscle_chest) to listOf(
                getString(R.string.exercise_chest_1),
                getString(R.string.exercise_chest_2),
                getString(R.string.exercise_chest_3),
                getString(R.string.exercise_chest_4),
                getString(R.string.exercise_chest_5),
            ),
            getString(R.string.muscle_biceps) to listOf(
                getString(R.string.exercise_biceps_1),
                getString(R.string.exercise_biceps_2),
                getString(R.string.exercise_biceps_3),
                getString(R.string.exercise_biceps_4),
                getString(R.string.exercise_biceps_5),
            ),
            getString(R.string.muscle_triceps) to listOf(
                getString(R.string.exercise_triceps_1),
                getString(R.string.exercise_triceps_2),
                getString(R.string.exercise_triceps_3),
                getString(R.string.exercise_triceps_4),
                getString(R.string.exercise_triceps_5),
                getString(R.string.exercise_triceps_6),
            ),
            getString(R.string.muscle_forearms) to listOf(
                getString(R.string.exercise_forearms_1),
                getString(R.string.exercise_forearms_2),
            ),
        )
    }

    private val musclesByBodyArea: Map<String, List<String>> by lazy {
        mapOf(
            BODY_AREA_UPPER to listOf(
                getString(R.string.muscle_back),
                getString(R.string.muscle_shoulders),
                getString(R.string.muscle_traps),
                getString(R.string.muscle_chest),
                getString(R.string.muscle_biceps),
                getString(R.string.muscle_triceps),
                getString(R.string.muscle_forearms),
            ),
            BODY_AREA_CORE to listOf(
                getString(R.string.muscle_abs),
                getString(R.string.muscle_obliques),
                getString(R.string.muscle_lower_back),
            ),
            BODY_AREA_LOWER to listOf(
                getString(R.string.muscle_hips),
                getString(R.string.muscle_thighs),
                getString(R.string.muscle_calves),
                getString(R.string.muscle_glutes),
            ),
        )
    }

    // Stable, locale-independent keys used only for CustomItemsSettings persistence,
    // so custom/hidden items keep working after the app's language changes.
    private val muscleStorageKeys: Map<String, String> by lazy {
        mapOf(
            getString(R.string.muscle_back) to "muscle_back",
            getString(R.string.muscle_shoulders) to "muscle_shoulders",
            getString(R.string.muscle_traps) to "muscle_traps",
            getString(R.string.muscle_chest) to "muscle_chest",
            getString(R.string.muscle_biceps) to "muscle_biceps",
            getString(R.string.muscle_triceps) to "muscle_triceps",
            getString(R.string.muscle_forearms) to "muscle_forearms",
            getString(R.string.muscle_abs) to "muscle_abs",
            getString(R.string.muscle_obliques) to "muscle_obliques",
            getString(R.string.muscle_lower_back) to "muscle_lower_back",
            getString(R.string.muscle_hips) to "muscle_hips",
            getString(R.string.muscle_thighs) to "muscle_thighs",
            getString(R.string.muscle_calves) to "muscle_calves",
            getString(R.string.muscle_glutes) to "muscle_glutes",
        )
    }

    private val exerciseStorageKeys: Map<String, String> by lazy {
        mapOf(
            getString(R.string.exercise_back_1) to "exercise_back_1",
            getString(R.string.exercise_back_2) to "exercise_back_2",
            getString(R.string.exercise_back_3) to "exercise_back_3",
            getString(R.string.exercise_back_4) to "exercise_back_4",
            getString(R.string.exercise_back_5) to "exercise_back_5",
            getString(R.string.exercise_chest_1) to "exercise_chest_1",
            getString(R.string.exercise_chest_2) to "exercise_chest_2",
            getString(R.string.exercise_chest_3) to "exercise_chest_3",
            getString(R.string.exercise_chest_4) to "exercise_chest_4",
            getString(R.string.exercise_chest_5) to "exercise_chest_5",
            getString(R.string.exercise_biceps_1) to "exercise_biceps_1",
            getString(R.string.exercise_biceps_2) to "exercise_biceps_2",
            getString(R.string.exercise_biceps_3) to "exercise_biceps_3",
            getString(R.string.exercise_biceps_4) to "exercise_biceps_4",
            getString(R.string.exercise_biceps_5) to "exercise_biceps_5",
            getString(R.string.exercise_triceps_1) to "exercise_triceps_1",
            getString(R.string.exercise_triceps_2) to "exercise_triceps_2",
            getString(R.string.exercise_triceps_3) to "exercise_triceps_3",
            getString(R.string.exercise_triceps_4) to "exercise_triceps_4",
            getString(R.string.exercise_triceps_5) to "exercise_triceps_5",
            getString(R.string.exercise_triceps_6) to "exercise_triceps_6",
            getString(R.string.exercise_forearms_1) to "exercise_forearms_1",
            getString(R.string.exercise_forearms_2) to "exercise_forearms_2",
        )
    }

    private fun muscleStorageKey(name: String): String = muscleStorageKeys[name] ?: name

    private fun exerciseStorageKey(name: String): String = exerciseStorageKeys[name] ?: name

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rootView = view
        dbHelper = WorkoutDbHelper(requireContext())

        repField = SpinField(
            display = view.findViewById(R.id.textRepValue),
            buttonUp = view.findViewById(R.id.buttonRepUp),
            buttonDown = view.findViewById(R.id.buttonRepDown),
            minValue = 0,
            maxValue = 99,
            initialValue = viewModel.repValue,
            onChanged = { viewModel.repValue = it },
        )

        timeField = SpinField(
            display = view.findViewById(R.id.textTimeValue),
            buttonUp = view.findViewById(R.id.buttonTimeUp),
            buttonDown = view.findViewById(R.id.buttonTimeDown),
            minValue = 0,
            maxValue = 300,
            initialValue = viewModel.timeValue,
            displayFormatter = ::formatMinutesSeconds,
            onChanged = { viewModel.timeValue = it },
        )

        delayField = SpinField(
            display = view.findViewById(R.id.textDelayValue),
            buttonUp = view.findViewById(R.id.buttonDelayUp),
            buttonDown = view.findViewById(R.id.buttonDelayDown),
            minValue = 0,
            maxValue = 300,
            initialValue = viewModel.delayValue,
            displayFormatter = ::formatMinutesSeconds,
            onChanged = { viewModel.delayValue = it },
        )

        groupRepetitions = view.findViewById(R.id.groupRepetitions)
        textRepetitionsLabel = view.findViewById(R.id.textRepetitionsLabel)

        repetitionsField = SpinField(
            display = view.findViewById(R.id.textRepetitionsValue),
            buttonUp = view.findViewById(R.id.buttonRepetitionsUp),
            buttonDown = view.findViewById(R.id.buttonRepetitionsDown),
            minValue = 0,
            maxValue = 99,
            initialValue = viewModel.repetitionsValue,
            onChanged = {
                viewModel.repetitionsValue = it
                val canEditLastEntry = viewModel.isAwaitingFinalRepetitions ||
                    (viewModel.isRunning && viewModel.phase == Phase.DELAY && !viewModel.isInitialDelay)
                if (canEditLastEntry && viewModel.completedRepetitions.isNotEmpty()) {
                    viewModel.completedRepetitions[viewModel.completedRepetitions.lastIndex] = it
                }
            },
        )

        buttonStart = view.findViewById(R.id.buttonStart)
        buttonStart.setOnClickListener {
            startTimer()
        }

        buttonSkipSet = view.findViewById(R.id.buttonSkipSet)
        buttonSkipSet.setOnClickListener {
            viewModel.skipCurrentSet()
        }

        buttonStop = view.findViewById(R.id.buttonStop)
        buttonStop.setOnClickListener {
            stopTimer()
        }

        buttonEndWorkout = view.findViewById(R.id.buttonEndWorkout)
        buttonEndWorkout.setOnClickListener {
            addWorkoutToCalendar()
        }
        buttonEndWorkout.setOnLongClickListener {
            confirmCancelWorkout()
            true
        }

        textTrainingElapsed = view.findViewById(R.id.textTrainingElapsed)
        textMuscleExercise = view.findViewById(R.id.textMuscleExercise)
        textMuscleExercise.setOnClickListener { showSelectedExerciseOptionsDialog() }
        exerciseChipContainer = view.findViewById(R.id.exerciseChipContainer)
        chipExerciseAdd = view.findViewById(R.id.chipExerciseAdd)
        chipExerciseAdd.setOnClickListener { showAddExerciseDialog() }

        setupBodyAreaSwitches(view)
        setupWeightSlider(view)
        restoreMuscleSelection()
        updateExerciseButtons(restoreSelection = true)
        refreshMaxWeightDisplay(getExerciseKey())

        wireViewModelCallbacks()
        syncUiFromViewModel()
    }

    private fun wireViewModelCallbacks() {
        viewModel.soundThresholdProvider = { CountdownSoundSettings.getThreshold(requireContext()) }
        viewModel.onRefresh = { syncUiFromViewModel() }
        viewModel.onLongBeep = { playLongBeep(); viewModel.abandonDuckAudioFocus() }
        viewModel.onBeep = { viewModel.requestDuckAudioFocus(); playBeep() }
        viewModel.onFinished = { onCountdownFinished() }
        viewModel.onElapsedTick = { updateElapsedText() }
        viewModel.onFinalRepetitionsWindowEnded = { finalizeStop() }
        viewModel.onSetCompleted = { updateMaxWeightAfterSet() }
    }

    private fun syncUiFromViewModel() {
        repField.value = viewModel.repValue
        timeField.value = viewModel.timeValue
        delayField.value = viewModel.delayValue
        repetitionsField.value = viewModel.repetitionsValue
        val shouldShowRepetitions = viewModel.isAwaitingFinalRepetitions ||
            (viewModel.isRunning && viewModel.phase == Phase.DELAY && !viewModel.isInitialDelay)
        if (shouldShowRepetitions) {
            if (groupRepetitions.visibility != View.VISIBLE) {
                groupRepetitions.visibility = View.VISIBLE
                textRepetitionsLabel.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.blink))
            }
        } else {
            textRepetitionsLabel.clearAnimation()
            groupRepetitions.visibility = View.INVISIBLE
        }
        updateStartButtonState()
        buttonSkipSet.isEnabled = viewModel.isRunning
        buttonStop.isEnabled = viewModel.isRunning
        buttonEndWorkout.isEnabled = viewModel.trainingStartTime != null
        if (viewModel.trainingStartTime != null) {
            textTrainingElapsed.visibility = View.VISIBLE
            updateElapsedText()
        } else {
            textTrainingElapsed.visibility = View.GONE
        }
    }

    private fun updateStartButtonState() {
        buttonStart.isEnabled = !viewModel.isRunning &&
            !viewModel.isAwaitingFinalRepetitions &&
            getSelectedExercise() != null
    }

    private fun updateElapsedText() {
        val start = viewModel.trainingStartTime ?: return
        val elapsedSeconds = ((System.currentTimeMillis() - start) / 1000).toInt()
        textTrainingElapsed.text = getString(R.string.training_elapsed, formatWorkoutDuration(elapsedSeconds))
    }

    private fun refreshMaxWeightDisplay(exerciseKey: String) {
        if (getSelectedExercise() == null) {
            applyMaxWeightDisplay(null)
            return
        }
        Thread {
            val maxWeight = dbHelper.getMaxWeight(exerciseKey)
            handler.post {
                if (!isAdded) return@post
                applyMaxWeightDisplay(maxWeight)
            }
        }.start()
    }

    private fun applyMaxWeightDisplay(maxWeight: Int?) {
        if (maxWeight == null) {
            textMaxWeight.visibility = View.GONE
            currentMaxWeight = null
            return
        }
        val numberText = maxWeight.toString()
        val supText = "Max"
        textMaxWeight.text = SpannableStringBuilder(numberText + supText).apply {
            setSpan(SuperscriptSpan(), numberText.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(0.6f), numberText.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textMaxWeight.visibility = View.VISIBLE
        currentMaxWeight = maxWeight
    }

    private fun formatWeight(value: Int): String {
        return "$value ${WeightUnitSettings.getUnit(requireContext()).label}"
    }

    private fun updateWeightFieldHint() {
        editWeightValue.hint = WeightUnitSettings.getUnit(requireContext()).label
            .replaceFirstChar { it.uppercase() }
    }

    private fun setupWeightSlider(view: View) {
        textMaxWeight = view.findViewById(R.id.textMaxWeight)
        editWeightValue = view.findViewById(R.id.editWeightValue)
        updateWeightFieldHint()

        textMaxWeight.setOnLongClickListener {
            val maxWeight = currentMaxWeight
            if (textMaxWeight.visibility != View.VISIBLE || maxWeight == null) return@setOnLongClickListener false
            viewModel.weightKg = maxWeight.coerceIn(WEIGHT_MIN_KG, WEIGHT_MAX_KG)
            editWeightValue.setText(formatWeight(viewModel.weightKg))
            true
        }

        editWeightValue.setText(formatWeight(viewModel.weightKg))

        editWeightValue.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.toIntOrNull() ?: return
                viewModel.weightKg = value.coerceIn(WEIGHT_MIN_KG, WEIGHT_MAX_KG)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        editWeightValue.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editWeightValue.filters = arrayOf(InputFilter.LengthFilter(3))
                editWeightValue.setText("")
            } else {
                viewModel.weightKg = editWeightValue.text.toString().toIntOrNull()
                    ?.coerceIn(WEIGHT_MIN_KG, WEIGHT_MAX_KG)
                    ?: viewModel.weightKg
                editWeightValue.filters = arrayOf()
                editWeightValue.setText(formatWeight(viewModel.weightKg))
            }
        }

        editWeightValue.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus()
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    private fun setupBodyAreaSwitches(view: View) {
        val chipAreaUpper = view.findViewById<View>(R.id.chipAreaUpper)
        val chipAreaCore = view.findViewById<View>(R.id.chipAreaCore)
        val chipAreaLower = view.findViewById<View>(R.id.chipAreaLower)
        val areaChips = listOf(chipAreaUpper, chipAreaCore, chipAreaLower)

        val row2Upper = view.findViewById<View>(R.id.row2Upper)
        val row2Core = view.findViewById<View>(R.id.row2Core)
        val row2Lower = view.findViewById<View>(R.id.row2Lower)
        val row2Groups = listOf(row2Upper, row2Core, row2Lower)
        val areaRowPairs = listOf(chipAreaUpper to row2Upper, chipAreaCore to row2Core, chipAreaLower to row2Lower)

        val addChipUpper = view.findViewById<View>(R.id.chipMuscleAddUpper)
        val addChipCore = view.findViewById<View>(R.id.chipMuscleAddCore)
        val addChipLower = view.findViewById<View>(R.id.chipMuscleAddLower)
        val addChips = listOf(addChipUpper, addChipCore, addChipLower)

        fun selectArea(chip: View, row: View) {
            areaChips.forEach { it.isSelected = it === chip }
            row2Groups.forEachIndexed { index, r ->
                val isSelected = r === row
                r.visibility = if (isSelected) View.VISIBLE else View.GONE
                addChips[index].visibility = if (isSelected) View.VISIBLE else View.GONE
            }
            viewModel.selectedBodyAreaTag = chip.tag as? String
        }

        chipAreaUpper.setOnClickListener {
            selectArea(chipAreaUpper, row2Upper)
            updateExerciseButtons()
            refreshMaxWeightDisplay(getExerciseKey())
        }
        chipAreaCore.setOnClickListener {
            selectArea(chipAreaCore, row2Core)
            updateExerciseButtons()
            refreshMaxWeightDisplay(getExerciseKey())
        }
        chipAreaLower.setOnClickListener {
            selectArea(chipAreaLower, row2Lower)
            updateExerciseButtons()
            refreshMaxWeightDisplay(getExerciseKey())
        }

        val restoredArea = areaRowPairs.firstOrNull { (chip, _) -> (chip.tag as? String) == viewModel.selectedBodyAreaTag }
            ?: areaRowPairs.first()
        selectArea(restoredArea.first, restoredArea.second)

        val onMuscleSelected: (Int) -> Unit = {
            updateExerciseButtons()
            refreshMaxWeightDisplay(getExerciseKey())
        }
        setupMuscleRow(view, R.id.row2Upper, R.id.chipMuscleAddUpper, BODY_AREA_UPPER, onMuscleSelected)
        setupMuscleRow(view, R.id.row2Core, R.id.chipMuscleAddCore, BODY_AREA_CORE, onMuscleSelected)
        setupMuscleRow(view, R.id.row2Lower, R.id.chipMuscleAddLower, BODY_AREA_LOWER, onMuscleSelected)
    }

    private fun setupExclusiveGroupForChips(chips: List<CheckableChip>, onSelected: (Int) -> Unit = {}) {
        chips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                if (!chip.isChecked) {
                    chips.forEach { it.isChecked = false }
                    chip.isChecked = true
                }
                onSelected(index)
            }
        }
    }

    private fun setupMuscleRow(
        view: View,
        rowId: Int,
        addChipId: Int,
        bodyAreaKey: String,
        onSelected: (Int) -> Unit,
    ) {
        val row = view.findViewById<ChipGroup>(rowId)
        val addChip = view.findViewById<CheckableChip>(addChipId)

        fun refreshChips(reselectName: String?) {
            (0 until row.childCount).map { row.getChildAt(it) }
                .filter { it !== addChip }
                .forEach { row.removeView(it) }

            val hidden = CustomItemsSettings.getHiddenMuscles(requireContext(), bodyAreaKey)
            val builtIn = musclesByBodyArea[bodyAreaKey].orEmpty().filterNot { muscleStorageKey(it) in hidden }
            val custom = CustomItemsSettings.getCustomMuscles(requireContext(), bodyAreaKey)
            val names = builtIn + custom

            val chips = names.map { name ->
                val isCustom = name in custom
                val chip = createMuscleChip(name)
                if (name == reselectName) chip.isChecked = true
                chip.setOnLongClickListener {
                    showMuscleOptionsDialog(bodyAreaKey, name, isCustom) { newSelection ->
                        refreshChips(newSelection)
                        updateExerciseButtons()
                        refreshMaxWeightDisplay(getExerciseKey())
                    }
                    true
                }
                row.addView(chip, row.indexOfChild(addChip))
                chip
            }
            setupExclusiveGroupForChips(chips, onSelected)
        }

        refreshChips(reselectName = viewModel.selectedMuscleTag)

        addChip.setOnClickListener {
            showAddMuscleDialog(bodyAreaKey) {
                refreshChips(reselectName = getSelectedMuscle())
                updateExerciseButtons()
                refreshMaxWeightDisplay(getExerciseKey())
            }
        }
    }

    private fun showMuscleOptionsDialog(
        bodyAreaKey: String,
        name: String,
        isCustom: Boolean,
        onChanged: (String?) -> Unit,
    ) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(container)
            .create()

        container.addView(
            createMenuRow("✎", getString(R.string.action_rename), R.color.dark_blue) {
                dialog.dismiss()
                showRenameMuscleDialog(bodyAreaKey, name, isCustom, onChanged)
            },
        )
        container.addView(
            createMenuRow("✕", getString(R.string.action_remove), R.color.stop_red) {
                dialog.dismiss()
                showRemoveMuscleConfirm(bodyAreaKey, name, isCustom, onChanged)
            },
        )
        container.addView(
            createMenuRow("↩", getString(R.string.action_leave), R.color.blue_gray) {
                dialog.dismiss()
            },
        )

        dialog.show()
    }

    private fun createMenuRow(icon: String, text: String, colorRes: Int, onClick: () -> Unit): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ContextCompat.getColor(requireContext(), colorRes))
            val paddingH = dpToPx(24)
            val paddingV = dpToPx(16)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setOnClickListener { onClick() }

            addView(
                TextView(requireContext()).apply {
                    this.text = icon
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    textSize = 18f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { marginEnd = dpToPx(16) }
                },
            )
            addView(
                TextView(requireContext()).apply {
                    this.text = text
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                },
            )
        }
    }

    private fun showRenameMuscleDialog(
        bodyAreaKey: String,
        name: String,
        isCustom: Boolean,
        onChanged: (String?) -> Unit,
    ) {
        val input = createDialogInput(name)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_rename_title_muscle)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != name) {
                    val wasSelected = getSelectedMuscle() == name
                    if (isCustom) {
                        CustomItemsSettings.renameCustomMuscle(requireContext(), bodyAreaKey, name, newName)
                    } else {
                        CustomItemsSettings.hideMuscle(requireContext(), bodyAreaKey, muscleStorageKey(name))
                        CustomItemsSettings.addCustomMuscle(requireContext(), bodyAreaKey, newName)
                        CustomItemsSettings.migrateExercises(requireContext(), muscleStorageKey(name), newName)
                    }
                    if (wasSelected) viewModel.selectedMuscleTag = newName
                    onChanged(if (wasSelected) newName else getSelectedMuscle())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRemoveMuscleConfirm(
        bodyAreaKey: String,
        name: String,
        isCustom: Boolean,
        onChanged: (String?) -> Unit,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(name)
            .setMessage(R.string.confirm_remove_muscle)
            .setPositiveButton(R.string.action_remove) { _, _ ->
                val wasSelected = getSelectedMuscle() == name
                if (isCustom) {
                    CustomItemsSettings.deleteCustomMuscle(requireContext(), bodyAreaKey, name)
                } else {
                    CustomItemsSettings.hideMuscle(requireContext(), bodyAreaKey, muscleStorageKey(name))
                    CustomItemsSettings.clearExercises(requireContext(), muscleStorageKey(name))
                }
                if (wasSelected) viewModel.selectedMuscleTag = null
                onChanged(if (wasSelected) null else getSelectedMuscle())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun createDialogInput(prefill: String = ""): EditText {
        return EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(prefill)
            setSelection(prefill.length)
        }
    }

    private fun createMuscleChip(name: String): CheckableChip {
        val chip = CheckableChip(requireContext())
        chip.tag = name
        chip.orientation = LinearLayout.VERTICAL
        chip.gravity = Gravity.CENTER
        chip.isClickable = true
        chip.isFocusable = true
        chip.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_checkable_selector)
        chip.minimumWidth = dpToPx(82)
        chip.minimumHeight = dpToPx(34)
        val padding = dpToPx(8)
        chip.setPadding(padding, padding, padding, padding)
        chip.addView(
            TextView(requireContext()).apply {
                text = name
                textSize = 11f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            },
        )
        return chip
    }

    private fun createExerciseChip(name: String): CheckableChip {
        val chip = CheckableChip(requireContext())
        chip.tag = name
        chip.orientation = LinearLayout.VERTICAL
        chip.gravity = Gravity.CENTER
        chip.isClickable = true
        chip.isFocusable = true
        chip.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_exercise_selector)
        chip.minimumHeight = dpToPx(26)
        val padding = dpToPx(4)
        chip.setPadding(padding, padding, padding, padding)
        chip.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dpToPx(4) }
        chip.addView(
            TextView(requireContext()).apply {
                text = name
                textSize = 9f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dpToPx(2) }
            },
        )
        return chip
    }

    private fun showAddMuscleDialog(bodyAreaKey: String, onAdded: (String) -> Unit) {
        val input = createDialogInput()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_add_title_muscle)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    CustomItemsSettings.addCustomMuscle(requireContext(), bodyAreaKey, name)
                    onAdded(name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddExerciseDialog() {
        val muscle = getSelectedMuscle()
        if (muscle == null) {
            Toast.makeText(requireContext(), getString(R.string.select_muscle_first), Toast.LENGTH_SHORT).show()
            return
        }
        val input = createDialogInput()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_add_title_exercise)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    CustomItemsSettings.addCustomExercise(requireContext(), muscleStorageKey(muscle), name)
                    updateExerciseButtons()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun getSelectedBodyArea(): String? {
        return listOf(R.id.chipAreaUpper, R.id.chipAreaCore, R.id.chipAreaLower)
            .map { rootView.findViewById<View>(it) }
            .firstOrNull { it.isSelected }
            ?.tag as? String
    }

    private fun getSelectedMuscle(): String? {
        val visibleGroup = listOf(R.id.row2Upper, R.id.row2Core, R.id.row2Lower)
            .map { rootView.findViewById<ViewGroup>(it) }
            .firstOrNull { it.visibility == View.VISIBLE }
            ?: return null

        for (i in 0 until visibleGroup.childCount) {
            val chip = visibleGroup.getChildAt(i) as? CheckableChip
            if (chip != null && chip.isChecked) {
                return chip.tag as? String
            }
        }
        return null
    }

    private fun getSelectedExercise(): String? {
        for (i in 0 until exerciseChipContainer.childCount) {
            val chip = exerciseChipContainer.getChildAt(i) as? CheckableChip
            if (chip != null && chip.isChecked) {
                return chip.tag as? String
            }
        }
        return null
    }

    private fun getExerciseKey(): String {
        return getSelectedExercise() ?: getSelectedMuscle() ?: getSelectedBodyArea() ?: getString(R.string.tab_timer)
    }

    private fun persistSelectionState() {
        viewModel.selectedBodyAreaTag = getSelectedBodyArea()
        viewModel.selectedMuscleTag = getSelectedMuscle()
        viewModel.selectedExerciseName = getSelectedExercise()
    }

    private fun restoreMuscleSelection() {
        val targetTag = viewModel.selectedMuscleTag ?: return
        val visibleGroup = listOf(R.id.row2Upper, R.id.row2Core, R.id.row2Lower)
            .map { rootView.findViewById<ViewGroup>(it) }
            .firstOrNull { it.visibility == View.VISIBLE }
            ?: return

        for (i in 0 until visibleGroup.childCount) {
            val chip = visibleGroup.getChildAt(i) as? CheckableChip
            if (chip != null && chip.tag == targetTag) {
                chip.isChecked = true
                return
            }
        }
    }

    private fun saveExerciseDefaults(exerciseName: String) {
        val repValue = viewModel.repValue
        val delaySec = viewModel.delayValue
        val timeSec = viewModel.timeValue
        val fallbackWeightKg = viewModel.weightKg
        Thread {
            val existingWeightKg = dbHelper.getExerciseDefaults(exerciseName)?.weightKg ?: fallbackWeightKg
            dbHelper.saveExerciseDefaults(exerciseName, repValue, delaySec, timeSec, existingWeightKg)
        }.start()
        Toast.makeText(
            requireContext(),
            getString(R.string.exercise_defaults_saved, exerciseName),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun loadExerciseDefaults(exerciseName: String, applyWeight: Boolean = true, onLoaded: (() -> Unit)? = null) {
        Thread {
            val defaults = dbHelper.getExerciseDefaults(exerciseName)
            handler.post {
                if (!isAdded) return@post
                val timerAlreadyActive = viewModel.isRunning || viewModel.isAwaitingFinalRepetitions
                if (defaults != null && !timerAlreadyActive) {
                    viewModel.repValue = defaults.rep
                    viewModel.delayValue = defaults.delaySec
                    viewModel.timeValue = defaults.timeSec
                    repField.value = viewModel.repValue
                    delayField.value = viewModel.delayValue
                    timeField.value = viewModel.timeValue
                    if (applyWeight) {
                        viewModel.weightKg = defaults.weightKg
                        editWeightValue.setText(formatWeight(viewModel.weightKg))
                    }
                }
                onLoaded?.invoke()
            }
        }.start()
    }

    private fun applyExerciseDefaultsIfAvailable(onDone: () -> Unit) {
        val exercise = getSelectedExercise()
        if (exercise != null) {
            loadExerciseDefaults(exercise, applyWeight = false, onLoaded = onDone)
        } else {
            onDone()
        }
    }

    private fun updateExerciseButtons(restoreSelection: Boolean = false) {
        val muscle = getSelectedMuscle()
        val hidden = muscle?.let { CustomItemsSettings.getHiddenExercises(requireContext(), muscleStorageKey(it)) }.orEmpty()
        val builtIn = exercisesByMuscle[muscle].orEmpty().filterNot { exerciseStorageKey(it) in hidden }
        val custom = muscle?.let { CustomItemsSettings.getCustomExercises(requireContext(), muscleStorageKey(it)) }.orEmpty()
        val exercises = builtIn + custom

        chipExerciseAdd.visibility = if (muscle != null) View.VISIBLE else View.GONE

        exerciseChipContainer.removeAllViews()
        val chips = exercises.map { name ->
            createExerciseChip(name).also { chip ->
                chip.isChecked = restoreSelection && name == viewModel.selectedExerciseName
                exerciseChipContainer.addView(chip)
            }
        }
        chips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                if (!chip.isChecked) {
                    chips.forEach { it.isChecked = false }
                    chip.isChecked = true
                }
                persistSelectionState()
                refreshMaxWeightDisplay(getExerciseKey())
                updateMuscleExerciseLabel()
                updateStartButtonState()
                loadExerciseDefaults(exercises[index]) { editWeightValue.setText("") }
            }
            chip.setOnLongClickListener {
                val muscleKey = muscle?.let { muscleStorageKey(it) } ?: return@setOnLongClickListener true
                val name = exercises[index]
                showExerciseOptionsDialog(muscleKey, name, name in custom)
                true
            }
        }

        updateMuscleExerciseLabel()
        persistSelectionState()
        updateStartButtonState()
        clearWorkoutFieldsIfNoExerciseSelected()
    }

    private fun clearWorkoutFieldsIfNoExerciseSelected() {
        if (getSelectedExercise() != null) return
        viewModel.resetToFreshStartDefaults()
        repField.value = viewModel.repValue
        repField.clearDisplay()
        timeField.value = viewModel.timeValue
        timeField.clearDisplay()
        delayField.value = viewModel.delayValue
        delayField.clearDisplay()
        editWeightValue.setText("")
    }

    private fun showSelectedExerciseOptionsDialog() {
        val muscle = getSelectedMuscle() ?: return
        val exercise = getSelectedExercise() ?: return
        val muscleKey = muscleStorageKey(muscle)
        val isCustom = exercise in CustomItemsSettings.getCustomExercises(requireContext(), muscleKey)
        showExerciseOptionsDialog(muscleKey, exercise, isCustom)
    }

    private fun showExerciseOptionsDialog(muscleKey: String, name: String, isCustom: Boolean) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(container)
            .create()

        container.addView(
            createMenuRow("⏱", getString(R.string.action_copy_times), R.color.chip_gradient_blue) {
                dialog.dismiss()
                saveExerciseDefaults(name)
            },
        )
        container.addView(
            createMenuRow("✎", getString(R.string.action_rename), R.color.dark_blue) {
                dialog.dismiss()
                showRenameExerciseDialog(muscleKey, name, isCustom)
            },
        )
        container.addView(
            createMenuRow("✕", getString(R.string.action_remove), R.color.stop_red) {
                dialog.dismiss()
                showRemoveExerciseConfirm(muscleKey, name, isCustom)
            },
        )
        container.addView(
            createMenuRow("↩", getString(R.string.action_leave), R.color.blue_gray) {
                dialog.dismiss()
            },
        )

        dialog.show()
    }

    private fun showRenameExerciseDialog(muscleKey: String, name: String, isCustom: Boolean) {
        val input = createDialogInput(name)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_rename_title_exercise)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != name) {
                    if (isCustom) {
                        CustomItemsSettings.renameCustomExercise(requireContext(), muscleKey, name, newName)
                    } else {
                        CustomItemsSettings.hideExercise(requireContext(), muscleKey, exerciseStorageKey(name))
                        CustomItemsSettings.addCustomExercise(requireContext(), muscleKey, newName)
                    }
                    if (viewModel.selectedExerciseName == name) viewModel.selectedExerciseName = newName
                    updateExerciseButtons(restoreSelection = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRemoveExerciseConfirm(muscleKey: String, name: String, isCustom: Boolean) {
        AlertDialog.Builder(requireContext())
            .setTitle(name)
            .setMessage(R.string.confirm_remove_exercise)
            .setPositiveButton(R.string.action_remove) { _, _ ->
                if (isCustom) {
                    CustomItemsSettings.deleteCustomExercise(requireContext(), muscleKey, name)
                } else {
                    CustomItemsSettings.hideExercise(requireContext(), muscleKey, exerciseStorageKey(name))
                }
                if (viewModel.selectedExerciseName == name) viewModel.selectedExerciseName = null
                updateExerciseButtons(restoreSelection = true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateMuscleExerciseLabel() {
        val muscle = getSelectedMuscle()
        if (muscle == null) {
            textMuscleExercise.visibility = View.GONE
            return
        }
        val exercise = getSelectedExercise()
        textMuscleExercise.text = if (exercise != null) "$muscle - $exercise" else muscle
        textMuscleExercise.visibility = View.VISIBLE
        textMuscleExercise.isClickable = exercise != null
        textMuscleExercise.isFocusable = exercise != null
    }

    private fun clearSession() {
        viewModel.sessionCycles.clear()
        viewModel.trainingStartTime = null
        viewModel.stopElapsedTicker()
        textTrainingElapsed.visibility = View.GONE
    }

    private fun confirmCancelWorkout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.cancel_workout_title)
            .setMessage(R.string.cancel_workout_confirm)
            .setPositiveButton(R.string.action_cancel_workout) { _, _ -> cancelWorkout() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun cancelWorkout() {
        val wasInProgress = viewModel.trainingStartTime != null
        if (viewModel.isAwaitingFinalRepetitions) {
            viewModel.cancelFinalRepetitionsWindow()
        }
        viewModel.stop()
        viewModel.abandonDuckAudioFocus()
        viewModel.pendingCompletedCycles.clear()
        viewModel.completedRepetitions.clear()
        viewModel.completedWeights.clear()
        if (wasInProgress) {
            viewModel.repValue = viewModel.originalRep
            viewModel.timeValue = viewModel.originalTime
            viewModel.delayValue = viewModel.originalDelay
        }
        clearSession()
        syncUiFromViewModel()
        applyExerciseDefaultsIfAvailable {}
    }

    private fun saveHistoryToDatabase(cycles: List<CompletedCycle>) {
        if (cycles.isEmpty()) return
        Thread {
            cycles.forEach { cycle ->
                dbHelper.insertWorkout(
                    cycle.sessionId, cycle.bodyArea, cycle.muscle, cycle.exercise, cycle.weightKg,
                    cycle.completedRep, cycle.time, cycle.delay, cycle.repetitions, cycle.startedAt,
                )
            }
        }.start()
    }

    private fun addWorkoutToCalendar() {
        if (viewModel.isAwaitingFinalRepetitions) {
            viewModel.cancelFinalRepetitionsWindow()
            finalizeStop()
        } else if (viewModel.isRunning) {
            viewModel.stop()
            finalizeStop()
        }

        val completedCycles = viewModel.pendingCompletedCycles.toList()
        viewModel.pendingCompletedCycles.clear()
        saveHistoryToDatabase(completedCycles)

        if (!CalendarSettings.isSaveToCalendarEnabled(requireContext())) {
            clearSession()
            syncUiFromViewModel()
            return
        }

        val records = if (completedCycles.isNotEmpty()) {
            completedCycles.map { cycle ->
                WorkoutCycleRecord(
                    bodyArea = cycle.bodyArea,
                    muscle = cycle.muscle,
                    exercise = cycle.exercise,
                    weightKg = cycle.weightKg,
                    rep = cycle.completedRep,
                    timeSec = cycle.time,
                    delaySec = cycle.delay,
                    repetitions = cycle.repetitions,
                    createdAt = cycle.startedAt,
                )
            }
        } else {
            listOf(
                WorkoutCycleRecord(
                    bodyArea = getSelectedBodyArea() ?: getString(R.string.tab_timer),
                    muscle = getSelectedMuscle(),
                    exercise = getSelectedExercise(),
                    weightKg = viewModel.weightKg,
                    rep = 0,
                    timeSec = viewModel.timeValue,
                    delaySec = viewModel.delayValue,
                    repetitions = "",
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        val totalSeconds = records.sumOf { it.rep * it.timeSec + maxOf(it.rep - 1, 0) * it.delaySec }
        val exerciseNames = records.mapNotNull { it.exercise ?: it.muscle }.distinct()
        val title = if (exerciseNames.isNotEmpty()) {
            getString(R.string.calendar_title_with_exercise, exerciseNames.joinToString(", "))
        } else {
            getString(R.string.calendar_title_default)
        }

        val description = buildSessionDetailMessage(requireContext(), records)

        val startMillis = viewModel.trainingStartTime ?: System.currentTimeMillis()
        val endMillis = startMillis + totalSeconds * 1000L
        saveEventToCalendar(CalendarEventData(title, description, startMillis, endMillis))
        clearSession()
        syncUiFromViewModel()
    }

    private fun saveEventToCalendar(event: CalendarEventData) {
        if (hasCalendarWritePermission()) {
            insertCalendarEvent(event)
        } else {
            pendingCalendarEvent = event
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            )
        }
    }

    private fun hasCalendarWritePermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun insertCalendarEvent(event: CalendarEventData) {
        val context = requireContext().applicationContext
        Thread {
            val calendarId = getWritableCalendarId(context)
            if (calendarId == null) {
                handler.post {
                    if (!isAdded) return@post
                    Toast.makeText(requireContext(), getString(R.string.calendar_app_not_found), Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.DTSTART, event.startMillis)
                put(CalendarContract.Events.DTEND, event.endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                CalendarColorSettings.getSelected(context).colorKey?.let { colorKey ->
                    put(CalendarContract.Events.EVENT_COLOR_KEY, colorKey)
                }
            }
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            handler.post {
                if (!isAdded) return@post
                Toast.makeText(requireContext(), getString(R.string.calendar_event_saved), Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun getWritableCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            var fallbackId: Long? = null
            while (cursor.moveToNext()) {
                val accessLevel = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL))
                if (accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                val isPrimaryIndex = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val isPrimary = isPrimaryIndex >= 0 && cursor.getInt(isPrimaryIndex) != 0
                if (isPrimary) return id
                if (fallbackId == null) fallbackId = id
            }
            return fallbackId
        }
        return null
    }

    private fun startTimer() {
        if (viewModel.isRunning) return
        if (viewModel.isAwaitingFinalRepetitions) {
            viewModel.cancelFinalRepetitionsWindow()
        }
        viewModel.phase = Phase.DELAY
        viewModel.isInitialDelay = true
        viewModel.originalRep = viewModel.repValue
        viewModel.originalTime = viewModel.timeValue
        viewModel.originalDelay = viewModel.delayValue
        viewModel.completedSets = 0
        viewModel.completedRepetitions.clear()
        viewModel.completedWeights.clear()
        viewModel.delayValue = CountdownSoundSettings.getThreshold(requireContext())

        val bodyArea = getSelectedBodyArea() ?: getString(R.string.tab_timer)
        val muscle = getSelectedMuscle()
        val exercise = getSelectedExercise()
        val weightKg = viewModel.weightKg
        val originalRep = viewModel.originalRep
        val originalTime = viewModel.originalTime
        val originalDelay = viewModel.originalDelay

        viewModel.sessionCycles.add(
            WorkoutCycle(
                bodyArea, muscle, exercise, weightKg, originalRep, originalTime, originalDelay,
                startedAt = System.currentTimeMillis(),
            ),
        )

        val isNewTraining = viewModel.trainingStartTime == null
        if (isNewTraining) {
            viewModel.trainingStartTime = System.currentTimeMillis()
            viewModel.startElapsedTicker()
        }

        viewModel.start()
        syncUiFromViewModel()
    }

    private fun stopTimer() {
        viewModel.stop()
        finalizeStop()
    }

    private fun onCountdownFinished() {
        playTripleBeep()
        viewModel.startFinalRepetitionsWindow()
        syncUiFromViewModel()
    }

    private fun finalizeStop() {
        viewModel.abandonDuckAudioFocus()

        val cycle = viewModel.sessionCycles.lastOrNull()
        val sessionId = viewModel.trainingStartTime
        val completedRep = viewModel.completedSets
        if (cycle != null && sessionId != null && completedRep > 0) {
            val exerciseKey = cycle.exercise ?: cycle.muscle ?: cycle.bodyArea
            val weightUnit = WeightUnitSettings.getUnit(requireContext()).label
            val repetitions = viewModel.completedRepetitions.mapIndexed { index, rep ->
                val weight = viewModel.completedWeights.getOrNull(index)
                if (weight != null) "${rep}X$weight$weightUnit" else rep.toString()
            }.joinToString(",")
            viewModel.pendingCompletedCycles.add(
                CompletedCycle(
                    sessionId, cycle.bodyArea, cycle.muscle, cycle.exercise, cycle.weightKg,
                    completedRep, cycle.time, cycle.delay, repetitions, cycle.startedAt,
                ),
            )
            Thread {
                dbHelper.updateMaxWeightIfHigher(exerciseKey, cycle.weightKg)
            }.start()
            refreshMaxWeightDisplay(exerciseKey)
        }

        if (!viewModel.isRunning) {
            viewModel.repValue = viewModel.originalRep
            viewModel.timeValue = viewModel.originalTime
            viewModel.delayValue = viewModel.originalDelay
            syncUiFromViewModel()
            applyExerciseDefaultsIfAvailable {}
        }
    }

    private fun updateMaxWeightAfterSet() {
        val cycle = viewModel.sessionCycles.lastOrNull() ?: return
        val exerciseKey = cycle.exercise ?: cycle.muscle ?: cycle.bodyArea
        Thread {
            dbHelper.updateMaxWeightIfHigher(exerciseKey, cycle.weightKg)
            val maxWeight = dbHelper.getMaxWeight(exerciseKey)
            handler.post {
                if (!isAdded) return@post
                applyMaxWeightDisplay(maxWeight)
            }
        }.start()
    }

    private fun playBeep() {
        toneGenerator.startTone(SoundSettings.getSelected(requireContext()).toneType, 150)
    }

    private fun playLongBeep() {
        toneGenerator.startTone(ToneGenerator.TONE_DTMF_1, 1500)
    }

    private fun playTripleBeep() {
        handler.post { playBeep() }
        handler.postDelayed({ playBeep() }, 200)
        handler.postDelayed({ playBeep() }, 400)
    }

    override fun onResume() {
        super.onResume()
        updateWeightFieldHint()
        if (AppForegroundTracker.consumePendingReset() && !viewModel.isRunning && !viewModel.isAwaitingFinalRepetitions) {
            viewModel.resetToFreshStartDefaults()
            repField.value = viewModel.repValue
            repField.clearDisplay()
            timeField.value = viewModel.timeValue
            timeField.clearDisplay()
            delayField.value = viewModel.delayValue
            delayField.clearDisplay()
            repetitionsField.value = viewModel.repetitionsValue
            editWeightValue.setText("")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        viewModel.onRefresh = null
        viewModel.onLongBeep = null
        viewModel.onBeep = null
        viewModel.onFinished = null
        viewModel.onElapsedTick = null
        viewModel.onFinalRepetitionsWindowEnded = null
        toneGenerator.release()
        dbHelper.close()
    }
}
