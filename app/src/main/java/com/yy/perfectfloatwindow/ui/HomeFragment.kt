package com.yy.perfectfloatwindow.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.yy.perfectfloatwindow.data.AISettings
import com.yy.perfectfloatwindow.data.ThemeManager
import com.yy.perfectfloatwindow.screenshot.ScreenshotService

class HomeFragment : Fragment() {

    private var floatHelper: FloatHelper? = null
    private var isFloatShowing = false
    private lateinit var floatView: View
    private lateinit var switchFloat: SwitchCompat
    private lateinit var tvFloatStatus: TextView

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startScreenshotService(result.resultCode, result.data!!)
            Toast.makeText(requireContext(), "截图服务已就绪", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "截图权限被拒绝", Toast.LENGTH_SHORT).show()
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
        tvFloatStatus = view.findViewById(R.id.tvFloatStatus)
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
        applyTheme(view)
        updateFloatViewTheme()
    }

    override fun onResume() {
        super.onResume()
        view?.let { applyTheme(it) }
        updateFloatViewTheme()

        if (switchFloat.isChecked && !SettingsCompat.canDrawOverlays(requireContext())) {
            switchFloat.isChecked = false
            tvFloatStatus.text = "点击开启后，悬浮球将显示在屏幕上"
            isFloatShowing = false
        }
    }

    private fun applyTheme(view: View) {
        val isChatGPT = ThemeManager.isChatGPTTheme(requireContext())

        val rootLayout = view.findViewById<LinearLayout>(R.id.rootLayout)
        val iconContainer = view.findViewById<FrameLayout>(R.id.iconContainer)
        val tvAppName = view.findViewById<TextView>(R.id.tvAppName)
        val tvAppDesc = view.findViewById<TextView>(R.id.tvAppDesc)
        val cardFloatToggle = view.findViewById<LinearLayout>(R.id.cardFloatToggle)
        val ivFloatIcon = view.findViewById<ImageView>(R.id.ivFloatIcon)
        val tvFloatTitle = view.findViewById<TextView>(R.id.tvFloatTitle)
        val tipLayout = view.findViewById<LinearLayout>(R.id.tipLayout)
        val tvTipTitle = view.findViewById<TextView>(R.id.tvTipTitle)
        val tvTipContent = view.findViewById<TextView>(R.id.tvTipContent)

        if (isChatGPT) {
            // ChatGPT Theme
            rootLayout.setBackgroundColor(0xFFFFFFFF.toInt())
            iconContainer.setBackgroundResource(R.drawable.float_bg_chatgpt)
            tvAppName.setTextColor(0xFF202123.toInt())
            tvAppDesc.setTextColor(0xFF6E6E80.toInt())
            cardFloatToggle.setBackgroundResource(R.drawable.bg_card_chatgpt)
            ivFloatIcon.setBackgroundResource(R.drawable.bg_icon_circle)
            ivFloatIcon.setColorFilter(0xFF10A37F.toInt())
            tvFloatTitle.setTextColor(0xFF202123.toInt())
            tvFloatStatus.setTextColor(0xFF6E6E80.toInt())
            tipLayout.setBackgroundResource(R.drawable.bg_tip_chatgpt)
            tvTipTitle.setTextColor(0xFF10A37F.toInt())
            tvTipContent.setTextColor(0xFF6E6E80.toInt())
        } else {
            // Netflix Theme
            rootLayout.setBackgroundColor(0xFF141414.toInt())
            iconContainer.setBackgroundResource(R.drawable.float_bg_netflix)
            tvAppName.setTextColor(0xFFFFFFFF.toInt())
            tvAppDesc.setTextColor(0xFF808080.toInt())
            cardFloatToggle.setBackgroundResource(R.drawable.bg_card_netflix)
            ivFloatIcon.setBackgroundResource(R.drawable.bg_icon_circle_netflix)
            ivFloatIcon.setColorFilter(0xFFE50914.toInt())
            tvFloatTitle.setTextColor(0xFFFFFFFF.toInt())
            tvFloatStatus.setTextColor(0xFF808080.toInt())
            tipLayout.setBackgroundResource(R.drawable.bg_tip_netflix)
            tvTipTitle.setTextColor(0xFFE50914.toInt())
            tvTipContent.setTextColor(0xFF808080.toInt())
        }
    }

    private fun updateFloatViewTheme() {
        val isChatGPT = ThemeManager.isChatGPTTheme(requireContext())
        val container = floatView.findViewById<FrameLayout>(R.id.llContainer)

        if (isChatGPT) {
            container?.setBackgroundResource(R.drawable.float_bg_chatgpt)
        } else {
            container?.setBackgroundResource(R.drawable.float_bg_netflix)
        }

        // Update size
        val sizeDp = ThemeManager.getFloatSize(requireContext())
        updateFloatSize(sizeDp)
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
            Toast.makeText(requireContext(), "请先到「我的」页面配置 API", Toast.LENGTH_LONG).show()
            return
        }

        if (ScreenshotService.isServiceRunning) {
            ScreenshotService.requestScreenshot()
        } else {
            Toast.makeText(requireContext(), "请先开启悬浮球", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwitch() {
        switchFloat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                floatHelper?.show()
                tvFloatStatus.text = "悬浮球已开启，点击可截图解题"
            } else {
                floatHelper?.dismiss()
                tvFloatStatus.text = "点击开启后，悬浮球将显示在屏幕上"
            }
            isFloatShowing = isChecked
        }
    }

    private fun updateFloatSize(sizeDp: Int) {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val iconSizePx = (sizeDp * 0.6 * density).toInt()

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
