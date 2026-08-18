package com.example.aiapp

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (DisplaySettings.isKeepScreenOnEnabled(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val topBar = findViewById<LinearLayout>(R.id.topBar)
        val topBarBasePaddingTop = topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInset.top + topBarBasePaddingTop, view.paddingRight, view.paddingBottom)
            insets
        }

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val dotsContainer = findViewById<LinearLayout>(R.id.dotsContainer)
        val textCurrentTab = findViewById<TextView>(R.id.textCurrentTab)
        val tabTitles = listOf(
            getString(R.string.tab_timer),
            getString(R.string.tab_calendar),
            getString(R.string.tab_settings),
            getString(R.string.tab_about),
        )

        viewPager.adapter = MainPagerAdapter(this)

        val dotSizeActive = resources.getDimensionPixelSize(R.dimen.tab_dot_size_active)
        val dotSizeInactive = resources.getDimensionPixelSize(R.dimen.tab_dot_size_inactive)
        val dotMargin = resources.getDimensionPixelSize(R.dimen.tab_dot_margin)

        val dots = tabTitles.indices.map { index ->
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dotSizeInactive, dotSizeInactive).apply {
                    marginStart = dotMargin
                    marginEnd = dotMargin
                }
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.dot_inactive)
                setOnClickListener { viewPager.currentItem = index }
            }.also { dotsContainer.addView(it) }
        }

        fun updateIndicator(position: Int) {
            dots.forEachIndexed { index, dot ->
                val isActive = index == position
                val size = if (isActive) dotSizeActive else dotSizeInactive
                dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply {
                    width = size
                    height = size
                }
                dot.background = ContextCompat.getDrawable(
                    this,
                    if (isActive) R.drawable.dot_active else R.drawable.dot_inactive,
                )
            }
            textCurrentTab.text = tabTitles[position]
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
            }
        })

        updateIndicator(0)
    }
}
