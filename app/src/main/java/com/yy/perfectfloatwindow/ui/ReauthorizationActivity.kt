package com.yy.perfectfloatwindow.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.yy.perfectfloatwindow.screenshot.ScreenshotService

class ReauthorizationActivity : AppCompatActivity() {

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startScreenshotService(result.resultCode, result.data!!)
            Toast.makeText(this, "截屏权限已恢复", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "截屏权限被拒绝", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is a transparent activity, no layout needed
        requestScreenshotPermission()
    }

    private fun requestScreenshotPermission() {
        // Stop old service first
        stopService(Intent(this, ScreenshotService::class.java))

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startScreenshotService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
            putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenshotService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, ReauthorizationActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
