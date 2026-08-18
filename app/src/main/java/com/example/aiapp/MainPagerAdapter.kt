package com.example.aiapp

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> TimerFragment()
        1 -> CalendarFragment()
        2 -> SettingsFragment()
        3 -> AboutFragment()
        else -> throw IllegalArgumentException("Invalid tab position: $position")
    }
}
