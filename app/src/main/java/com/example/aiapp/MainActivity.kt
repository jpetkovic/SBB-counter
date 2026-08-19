package com.example.aiapp

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (DisplaySettings.isKeepScreenOnEnabled(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val tabLayoutBasePaddingTop = tabLayout.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(tabLayout) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInset.top + tabLayoutBasePaddingTop, view.paddingRight, view.paddingBottom)
            insets
        }

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabTitles = listOf(
            getString(R.string.tab_timer),
            getString(R.string.tab_calendar),
            getString(R.string.tab_settings),
            getString(R.string.tab_about),
        )

        viewPager.adapter = MainPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }
}
