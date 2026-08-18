package com.example.aiapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private val handler = Handler(Looper.getMainLooper())
    private val monthYearFormat = SimpleDateFormat("LLLL yyyy", Locale.getDefault())
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dialogTitleFormat = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
    private val sessionDateFormat = SimpleDateFormat("d.M.yyyy HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private lateinit var dbHelper: WorkoutDbHelper
    private lateinit var textMonthYear: TextView
    private lateinit var weekdayHeader: GridLayout
    private lateinit var dayGrid: GridLayout
    private lateinit var listHistory: ListView
    private lateinit var textEmpty: TextView
    private val displayedMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var workoutDates: Set<String> = emptySet()
    private var sessions: List<WorkoutSession> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbHelper = WorkoutDbHelper(requireContext())
        textMonthYear = view.findViewById(R.id.textMonthYear)
        weekdayHeader = view.findViewById(R.id.weekdayHeader)
        dayGrid = view.findViewById(R.id.dayGrid)
        listHistory = view.findViewById(R.id.listHistory)
        textEmpty = view.findViewById(R.id.textHistoryEmpty)

        view.findViewById<View>(R.id.buttonPrevMonth).setOnClickListener {
            displayedMonth.add(Calendar.MONTH, -1)
            renderMonth()
        }
        view.findViewById<View>(R.id.buttonNextMonth).setOnClickListener {
            displayedMonth.add(Calendar.MONTH, 1)
            renderMonth()
        }

        listHistory.setOnItemClickListener { _, _, position, _ ->
            showSessionDetail(sessions[position])
        }
        listHistory.setOnItemLongClickListener { _, _, position, _ ->
            confirmDeleteSession(sessions[position])
            true
        }

        setupWeekdayHeader()
    }

    override fun onResume() {
        super.onResume()
        loadWorkoutDates()
        loadSessions()
    }

    private fun setupWeekdayHeader() {
        val symbols = java.text.DateFormatSymbols(Locale.getDefault()).shortWeekdays
        val mondayFirstOrder = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
        )
        weekdayHeader.removeAllViews()
        mondayFirstOrder.forEach { dayOfWeek ->
            weekdayHeader.addView(
                TextView(requireContext()).apply {
                    text = symbols[dayOfWeek].take(2)
                    gravity = Gravity.CENTER
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = dpToPx(40)
                        height = dpToPx(24)
                        setMargins(dpToPx(2), 0, dpToPx(2), 0)
                    }
                },
            )
        }
    }

    private fun loadWorkoutDates() {
        Thread {
            val dates = dbHelper.getWorkoutDates()
            handler.post {
                if (!isAdded) return@post
                workoutDates = dates
                renderMonth()
            }
        }.start()
    }

    private fun loadSessions() {
        Thread {
            val loaded = dbHelper.getSessions()
            handler.post {
                if (!isAdded) return@post
                sessions = loaded
                textEmpty.visibility = if (loaded.isEmpty()) View.VISIBLE else View.GONE
                listHistory.adapter = object : ArrayAdapter<String>(
                    requireContext(),
                    android.R.layout.simple_list_item_2,
                    android.R.id.text1,
                    loaded.map { sessionDateFormat.format(Date(it.createdAt)) },
                ) {
                    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                        val rowView = super.getView(position, convertView, parent)
                        rowView.findViewById<TextView>(android.R.id.text2).text = loaded[position].exercises
                        return rowView
                    }
                }
            }
        }.start()
    }

    private fun showSessionDetail(session: WorkoutSession) {
        Thread {
            val records = dbHelper.getSessionDetails(session.sessionId)
            handler.post {
                if (!isAdded) return@post
                val message = buildSessionDetailMessage(requireContext(), records)
                AlertDialog.Builder(requireContext())
                    .setTitle(sessionDateFormat.format(Date(session.createdAt)))
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }.start()
    }

    private fun confirmDeleteSession(session: WorkoutSession) {
        AlertDialog.Builder(requireContext())
            .setTitle(sessionDateFormat.format(Date(session.createdAt)))
            .setMessage(R.string.history_delete_confirm)
            .setPositiveButton(R.string.history_delete) { _, _ -> deleteSession(session) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteSession(session: WorkoutSession) {
        Thread {
            dbHelper.deleteSession(session.sessionId)
            handler.post {
                if (!isAdded) return@post
                loadSessions()
                loadWorkoutDates()
            }
        }.start()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun renderMonth() {
        textMonthYear.text = monthYearFormat.format(displayedMonth.time)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        dayGrid.removeAllViews()

        val monthCal = displayedMonth.clone() as Calendar
        monthCal.set(Calendar.DAY_OF_MONTH, 1)
        val firstWeekday = monthCal.get(Calendar.DAY_OF_WEEK)
        val mondayIndex = (firstWeekday + 5) % 7
        val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        repeat(mondayIndex) {
            dayGrid.addView(
                TextView(requireContext()).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = dpToPx(40)
                        height = dpToPx(40)
                        setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                    }
                },
            )
        }

        for (day in 1..daysInMonth) {
            monthCal.set(Calendar.DAY_OF_MONTH, day)
            val dateKey = dateKeyFormat.format(monthCal.time)
            val hasWorkout = dateKey in workoutDates
            dayGrid.addView(createDayCell(day, hasWorkout, dateKey))
        }
    }

    private fun createDayCell(day: Int, hasWorkout: Boolean, dateKey: String): View {
        return TextView(requireContext()).apply {
            text = day.toString()
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(
                ContextCompat.getColor(requireContext(), if (hasWorkout) R.color.white else R.color.black),
            )
            background = ContextCompat.getDrawable(
                requireContext(),
                if (hasWorkout) R.drawable.bg_calendar_day_workout else R.drawable.bg_calendar_day,
            )
            layoutParams = GridLayout.LayoutParams().apply {
                width = dpToPx(40)
                height = dpToPx(40)
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            if (hasWorkout) {
                isClickable = true
                isFocusable = true
                setOnClickListener { showDayDetail(dateKey) }
            }
        }
    }

    private fun showDayDetail(dateKey: String) {
        Thread {
            val daySessions = dbHelper.getSessions(dateFilter = dateKey)
            val message = buildString {
                daySessions.forEachIndexed { index, session ->
                    val records = dbHelper.getSessionDetails(session.sessionId)
                    append(timeFormat.format(Date(session.createdAt))).append("\n")
                    append(buildSessionDetailMessage(requireContext(), records))
                    if (index != daySessions.lastIndex) append("\n\n")
                }
            }
            handler.post {
                if (!isAdded) return@post
                val parsedDate = dateKeyFormat.parse(dateKey) ?: Date()
                AlertDialog.Builder(requireContext())
                    .setTitle(dialogTitleFormat.format(parsedDate))
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dbHelper.close()
    }
}
