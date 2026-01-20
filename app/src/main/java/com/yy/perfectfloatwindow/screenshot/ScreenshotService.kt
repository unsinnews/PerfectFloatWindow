package com.yy.perfectfloatwindow.screenshot

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.yy.perfectfloatwindow.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenshotService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val CHANNEL_ID = "screenshot_channel"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        var isServiceRunning = false
            private set

        private var pendingScreenshot = false

        fun requestScreenshot() {
            pendingScreenshot = true
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())

        setupMediaProjection(resultCode, resultData)

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screenshot Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screenshot capture service"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Screenshot Ready")
        .setContentText("Tap the float window to capture screen")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun setupMediaProjection(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )

        // Start checking for screenshot requests
        startScreenshotCheck()
    }

    private fun startScreenshotCheck() {
        handler.post(object : Runnable {
            override fun run() {
                if (pendingScreenshot) {
                    pendingScreenshot = false
                    captureScreen()
                }
                if (isServiceRunning) {
                    handler.postDelayed(this, 100)
                }
            }
        })
    }

    private fun captureScreen() {
        handler.postDelayed({
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                val bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                // Crop to actual screen size
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                if (bitmap != croppedBitmap) {
                    bitmap.recycle()
                }

                saveBitmap(croppedBitmap)
            } else {
                handler.post {
                    Toast.makeText(this, "Failed to capture screen", Toast.LENGTH_SHORT).show()
                }
            }
        }, 100)
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "Screenshot_${dateFormat.format(Date())}.png"

        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val screenshotDir = File(picturesDir, "Screenshots")
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs()
        }

        val file = File(screenshotDir, fileName)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            handler.post {
                Toast.makeText(this, "Screenshot saved: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            handler.post {
                Toast.makeText(this, "Failed to save screenshot", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}
