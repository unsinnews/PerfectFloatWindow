package com.yy.perfectfloatwindow.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.yy.floatserver.FloatClient
import com.yy.floatserver.FloatHelper
import com.yy.floatserver.IFloatClickListener
import com.yy.floatserver.IFloatPermissionCallback
import com.yy.floatserver.utils.SettingsCompat
import com.yy.perfectfloatwindow.R
import com.yy.perfectfloatwindow.SecondActivity
import com.yy.perfectfloatwindow.data.AISettings
import com.yy.perfectfloatwindow.screenshot.ScreenshotService

class HomeFragment : Fragment() {

    private var floatHelper: FloatHelper? = null
    private var isFloatShowing = false
    private lateinit var floatView: View
    private var currentSize = 36
    private lateinit var switchFloat: SwitchCompat
    private lateinit var tvStatusText: TextView

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startScreenshotService(result.resultCode, result.data!!)
            Toast.makeText(requireContext(), "Screenshot ready! Tap float window to capture.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Screenshot permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchFloat = view.findViewById(R.id.switchFloat)
        tvStatusText = view.findViewById(R.id.tvStatusText)
        floatView = View.inflate(requireContext(), R.layout.float_view, null)

        floatHelper = FloatClient.Builder()
            .with(requireActivity())
            .addView(floatView)
            .enableDefaultPermissionDialog(true)
            .setClickListener(object : IFloatClickListener {
                override fun onFloatClick() {
                    takeScreenshot()
                }
            })
            .addPermissionCallback(object : IFloatPermissionCallback {
                override fun onPermissionResult(granted: Boolean) {
                    if (granted) {
                        requestScreenshotPermission()
                    } else {
                        floatHelper?.requestPermission()
                    }
                }
            })
            .build()

        setupSwitch()
        setupButtons(view)
        setupSizeAdjustment(view)
    }

    override fun onResume() {
        super.onResume()
        if (switchFloat.isChecked && !SettingsCompat.canDrawOverlays(requireContext())) {
            switchFloat.isChecked = false
            tvStatusText.text = "Tap toggle to enable"
            isFloatShowing = false
        }
    }

    private fun requestScreenshotPermission() {
        if (!ScreenshotService.isServiceRunning) {
            val projectionManager = requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    private fun startScreenshotService(resultCode: Int, data: Intent) {
        ScreenshotService.setScreenshotCallback(object : ScreenshotService.Companion.ScreenshotCallback {
            override fun onScreenshotCaptured(bitmap: Bitmap) {
                AnswerPopupService.show(requireContext(), bitmap)
            }

            override fun onScreenshotFailed(error: String) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                }
            }
        })

        val serviceIntent = Intent(requireContext(), ScreenshotService::class.java).apply {
            putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenshotService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }
    }

    private fun takeScreenshot() {
        val apiKey = AISettings.getApiKey(requireContext())
        if (apiKey.isBlank()) {
            Toast.makeText(requireContext(), "Please configure API Key in Settings first", Toast.LENGTH_LONG).show()
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
            return
        }

        if (ScreenshotService.isServiceRunning) {
            ScreenshotService.requestScreenshot()
        } else {
            Toast.makeText(requireContext(), "Please enable float window first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwitch() {
        switchFloat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                floatHelper?.show()
                tvStatusText.text = "Tap float window to solve questions"
            } else {
                floatHelper?.dismiss()
                tvStatusText.text = "Tap toggle to enable"
            }
            isFloatShowing = isChecked
        }
    }

    private fun setupButtons(view: View) {
        view.findViewById<Button>(R.id.btnShow).setOnClickListener {
            floatHelper?.show()
            switchFloat.isChecked = true
            tvStatusText.text = "Tap float window to solve questions"
            isFloatShowing = true
        }

        view.findViewById<Button>(R.id.btnClose).setOnClickListener {
            floatHelper?.dismiss()
            switchFloat.isChecked = false
            tvStatusText.text = "Tap toggle to enable"
            isFloatShowing = false
        }

        view.findViewById<Button>(R.id.btnJump).setOnClickListener {
            startActivity(Intent(requireContext(), SecondActivity::class.java))
        }
    }

    private fun setupSizeAdjustment(view: View) {
        val seekBar = view.findViewById<SeekBar>(R.id.seekBarSize)
        val tvSizeValue = view.findViewById<TextView>(R.id.tvSizeValue)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentSize = progress
                tvSizeValue.text = "${progress}dp"
                updateFloatSize(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateFloatSize(sizeDp: Int) {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val iconSizePx = (sizeDp * 0.7 * density).toInt()

        val container = floatView.findViewById<FrameLayout>(R.id.llContainer)
        val icon = floatView.findViewById<ImageView>(R.id.ivIcon)

        container?.layoutParams?.let {
            it.width = sizePx
            it.height = sizePx
            container.layoutParams = it
        }

        icon?.layoutParams?.let {
            it.width = iconSizePx
            it.height = iconSizePx
            icon.layoutParams = it
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        floatHelper?.release()
    }
}
