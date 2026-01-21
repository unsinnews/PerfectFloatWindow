package com.yy.perfectfloatwindow.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.yy.perfectfloatwindow.R
import com.yy.perfectfloatwindow.data.AISettings

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenuItems(view)
        updateApiStatus(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { updateApiStatus(it) }
    }

    private fun setupMenuItems(view: View) {
        // API Settings
        view.findViewById<View>(R.id.menuApiSettings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // History (placeholder)
        view.findViewById<View>(R.id.menuHistory).setOnClickListener {
            Toast.makeText(requireContext(), "Coming soon", Toast.LENGTH_SHORT).show()
        }

        // Rate App
        view.findViewById<View>(R.id.menuRate).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${requireContext().packageName}")))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "App store not available", Toast.LENGTH_SHORT).show()
            }
        }

        // About
        view.findViewById<View>(R.id.menuAbout).setOnClickListener {
            showAboutDialog()
        }
    }

    private fun updateApiStatus(view: View) {
        val tvApiStatus = view.findViewById<TextView>(R.id.tvApiStatus)
        val apiKey = AISettings.getApiKey(requireContext())

        if (apiKey.isNotBlank()) {
            tvApiStatus.text = "Configured"
            tvApiStatus.setTextColor(0xFF4CAF50.toInt())
        } else {
            tvApiStatus.text = "Not configured"
            tvApiStatus.setTextColor(0xFFFF5252.toInt())
        }
    }

    private fun showAboutDialog() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("About")
        builder.setMessage(
            "PerfectFloatWindow\n\n" +
            "Version: 1.2.0\n\n" +
            "AI-powered question solver with floating window support.\n\n" +
            "Features:\n" +
            "- Screenshot capture\n" +
            "- OCR recognition\n" +
            "- AI-powered answers\n" +
            "- Fast & Deep solve modes"
        )
        builder.setPositiveButton("OK", null)
        builder.show()
    }
}
