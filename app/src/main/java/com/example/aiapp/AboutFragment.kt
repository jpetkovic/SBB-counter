package com.example.aiapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val versionName = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
            .versionName

        view.findViewById<TextView>(R.id.textAppVersion).text =
            getString(R.string.about_version, versionName)
    }
}
