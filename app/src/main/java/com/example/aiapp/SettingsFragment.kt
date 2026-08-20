package com.example.aiapp

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
    private var isInitializing = true
    private var isInitializingLanguage = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sounds = BeepSound.values()
        val spinnerSound = view.findViewById<Spinner>(R.id.spinnerSound)
        spinnerSound.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.beep_sound_names,
            android.R.layout.simple_spinner_item,
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        isInitializing = true
        spinnerSound.setSelection(sounds.indexOf(SoundSettings.getSelected(requireContext())))

        spinnerSound.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val sound = sounds[position]
                SoundSettings.setSelected(requireContext(), sound)
                if (isInitializing) {
                    isInitializing = false
                } else {
                    toneGenerator.startTone(sound.toneType, 150)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val languages = AppLanguage.values()
        val spinnerLanguage = view.findViewById<Spinner>(R.id.spinnerLanguage)
        spinnerLanguage.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            languages.map { it.displayName },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val currentLanguageTag = AppCompatDelegate.getApplicationLocales()
            .takeIf { !it.isEmpty }
            ?.get(0)
            ?.language
            ?: resources.configuration.locales[0].language
        val currentLanguageIndex = languages.indexOfFirst { it.tag == currentLanguageTag }.let { if (it >= 0) it else 0 }

        isInitializingLanguage = true
        spinnerLanguage.setSelection(currentLanguageIndex)

        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInitializingLanguage) {
                    isInitializingLanguage = false
                    return
                }
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languages[position].tag))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val thresholds = (SOUND_THRESHOLD_MIN..SOUND_THRESHOLD_MAX).toList()
        val spinnerSoundThreshold = view.findViewById<Spinner>(R.id.spinnerSoundThreshold)
        spinnerSoundThreshold.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            thresholds.map { it.toString() },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinnerSoundThreshold.setSelection(thresholds.indexOf(CountdownSoundSettings.getThreshold(requireContext())))

        spinnerSoundThreshold.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                CountdownSoundSettings.setThreshold(requireContext(), thresholds[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val weightUnits = WeightUnit.entries.toList()
        val spinnerWeightUnit = view.findViewById<Spinner>(R.id.spinnerWeightUnit)
        spinnerWeightUnit.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            weightUnits.map { it.label },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinnerWeightUnit.setSelection(weightUnits.indexOf(WeightUnitSettings.getUnit(requireContext())))

        spinnerWeightUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                WeightUnitSettings.setUnit(requireContext(), weightUnits[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val switchSaveToCalendar = view.findViewById<SwitchCompat>(R.id.switchSaveToCalendar)
        switchSaveToCalendar.isChecked = CalendarSettings.isSaveToCalendarEnabled(requireContext())
        switchSaveToCalendar.setOnCheckedChangeListener { _, isChecked ->
            CalendarSettings.setSaveToCalendarEnabled(requireContext(), isChecked)
        }

        val calendarColors = CalendarEventColor.entries.toList()
        val calendarColorNames = resources.getStringArray(R.array.calendar_color_names).toList()
        val spinnerCalendarColor = view.findViewById<Spinner>(R.id.spinnerCalendarColor)
        spinnerCalendarColor.adapter = CalendarColorSpinnerAdapter(requireContext(), calendarColors, calendarColorNames)

        spinnerCalendarColor.setSelection(calendarColors.indexOf(CalendarColorSettings.getSelected(requireContext())))

        spinnerCalendarColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                CalendarColorSettings.setSelected(requireContext(), calendarColors[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val switchDuckSound = view.findViewById<SwitchCompat>(R.id.switchDuckSound)
        switchDuckSound.isChecked = DuckSoundSettings.isDuckSoundEnabled(requireContext())
        switchDuckSound.setOnCheckedChangeListener { _, isChecked ->
            DuckSoundSettings.setDuckSoundEnabled(requireContext(), isChecked)
        }

        val switchKeepScreenOn = view.findViewById<SwitchCompat>(R.id.switchKeepScreenOn)
        switchKeepScreenOn.isChecked = DisplaySettings.isKeepScreenOnEnabled(requireContext())
        switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            DisplaySettings.setKeepScreenOnEnabled(requireContext(), isChecked)
            if (isChecked) {
                requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        toneGenerator.release()
    }
}
