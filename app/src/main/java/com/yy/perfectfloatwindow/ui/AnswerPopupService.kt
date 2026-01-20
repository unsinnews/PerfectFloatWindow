package com.yy.perfectfloatwindow.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.yy.perfectfloatwindow.R
import com.yy.perfectfloatwindow.data.AISettings
import com.yy.perfectfloatwindow.data.Answer
import com.yy.perfectfloatwindow.data.Question
import com.yy.perfectfloatwindow.network.ChatAPI
import com.yy.perfectfloatwindow.network.StreamingCallback
import com.yy.perfectfloatwindow.network.VisionAPI
import com.yy.perfectfloatwindow.screenshot.ScreenshotService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnswerPopupService : Service() {

    private lateinit var windowManager: WindowManager
    private var popupView: View? = null
    private lateinit var windowParams: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)

    private var currentBitmap: Bitmap? = null
    private var currentQuestions: List<Question> = emptyList()
    private var answers: MutableMap<Int, Answer> = mutableMapOf()
    private var answerViews: MutableMap<Int, View> = mutableMapOf()
    private var isFastMode = true
    private var isPopupShowing = false

    private var initialTouchY = 0f
    private var initialHeight = 0

    companion object {
        private const val CHANNEL_ID = "answer_popup_channel"
        private const val NOTIFICATION_ID = 2001

        private var pendingBitmap: Bitmap? = null
        private var isServiceRunning = false

        fun show(context: Context, bitmap: Bitmap) {
            pendingBitmap = bitmap
            val intent = Intent(context, AnswerPopupService::class.java)
            intent.putExtra("action", "show")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun dismiss(context: Context) {
            val intent = Intent(context, AnswerPopupService::class.java)
            intent.putExtra("action", "dismiss")
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground immediately for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        when (intent?.getStringExtra("action")) {
            "show" -> {
                if (!isPopupShowing) {
                    setupPopupView()
                }
                pendingBitmap?.let {
                    processBitmap(it)
                    pendingBitmap = null
                }
            }
            "dismiss" -> {
                dismissPopup()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Answer Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows AI answer popup"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("AI Question Solver")
        .setContentText("Processing your question...")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun setupPopupView() {
        if (isPopupShowing) return

        try {
            popupView = LayoutInflater.from(this).inflate(R.layout.layout_answer_popup, null)

            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val popupHeight = (screenHeight * 2 / 3)

            windowParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                gravity = Gravity.BOTTOM
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = popupHeight
            }

            windowManager.addView(popupView, windowParams)
            isPopupShowing = true

            setupDragHandle()
            setupTabs()
            setupRetakeButton()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to show popup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupDragHandle() {
        val view = popupView ?: return
        val dragHandle = view.findViewById<View>(R.id.dragHandle)
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchY = event.rawY
                    initialHeight = windowParams.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialTouchY - event.rawY
                    val newHeight = (initialHeight + deltaY).toInt()
                    val screenHeight = resources.displayMetrics.heightPixels
                    val minHeight = screenHeight / 3
                    val maxHeight = (screenHeight * 0.9).toInt()

                    windowParams.height = newHeight.coerceIn(minHeight, maxHeight)
                    try {
                        windowManager.updateViewLayout(popupView, windowParams)
                    } catch (e: Exception) {
                        // View might be detached
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTabs() {
        val view = popupView ?: return
        val tabFast = view.findViewById<TextView>(R.id.tabFast)
        val tabDeep = view.findViewById<TextView>(R.id.tabDeep)

        tabFast.setOnClickListener {
            if (!isFastMode) {
                isFastMode = true
                tabFast.setBackgroundResource(R.drawable.bg_tab_selected)
                tabFast.setTextColor(0xFFFFFFFF.toInt())
                tabDeep.setBackgroundResource(R.drawable.bg_tab_unselected)
                tabDeep.setTextColor(0xFF757575.toInt())

                if (currentQuestions.isNotEmpty()) {
                    clearAnswers()
                    startSolving(currentQuestions)
                }
            }
        }

        tabDeep.setOnClickListener {
            if (isFastMode) {
                isFastMode = false
                tabDeep.setBackgroundResource(R.drawable.bg_tab_selected)
                tabDeep.setTextColor(0xFFFFFFFF.toInt())
                tabFast.setBackgroundResource(R.drawable.bg_tab_unselected)
                tabFast.setTextColor(0xFF757575.toInt())

                if (currentQuestions.isNotEmpty()) {
                    clearAnswers()
                    startSolving(currentQuestions)
                }
            }
        }
    }

    private fun setupRetakeButton() {
        val view = popupView ?: return
        view.findViewById<View>(R.id.btnRetake).setOnClickListener {
            if (ScreenshotService.isServiceRunning) {
                dismissPopup()
                handler.postDelayed({
                    ScreenshotService.requestScreenshot()
                }, 300)
            } else {
                Toast.makeText(this, "Please enable screenshot first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun processBitmap(bitmap: Bitmap) {
        currentBitmap = bitmap
        showLoading("正在识别题目...")

        coroutineScope.launch {
            try {
                val config = AISettings.getOCRConfig(this@AnswerPopupService)
                if (!config.isValid() || config.apiKey.isBlank()) {
                    showError("请先在设置中配置API")
                    return@launch
                }

                val visionAPI = VisionAPI(config)
                val questions = visionAPI.extractQuestions(bitmap)

                withContext(Dispatchers.Main) {
                    if (questions.isEmpty()) {
                        showError("未能识别到题目")
                    } else {
                        currentQuestions = questions
                        displayQuestions(questions)
                        startSolving(questions)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("OCR失败: ${e.message}")
                }
            }
        }
    }

    private fun showLoading(text: String) {
        handler.post {
            val view = popupView ?: return@post
            view.findViewById<View>(R.id.loadingView)?.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvLoadingText)?.text = text
            view.findViewById<LinearLayout>(R.id.answersContainer)?.visibility = View.GONE
        }
    }

    private fun hideLoading() {
        handler.post {
            val view = popupView ?: return@post
            view.findViewById<View>(R.id.loadingView)?.visibility = View.GONE
            view.findViewById<LinearLayout>(R.id.answersContainer)?.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        handler.post {
            hideLoading()
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun displayQuestions(questions: List<Question>) {
        hideLoading()
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        container.removeAllViews()
        answerViews.clear()

        questions.forEachIndexed { index, question ->
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_question_answer, container, false)

            itemView.findViewById<TextView>(R.id.tvQuestionTitle).text = "问题${index + 1}"
            itemView.findViewById<TextView>(R.id.tvQuestionText).text = question.text
            itemView.findViewById<TextView>(R.id.tvAnswerText).text = ""

            container.addView(itemView)
            answerViews[question.id] = itemView
        }
    }

    private fun clearAnswers() {
        answers.clear()
        answerViews.forEach { (_, view) ->
            view.findViewById<TextView>(R.id.tvAnswerText)?.text = ""
        }
    }

    private fun startSolving(questions: List<Question>) {
        showLoading("正在解答...")

        val config = if (isFastMode) {
            AISettings.getFastConfig(this)
        } else {
            AISettings.getDeepConfig(this)
        }

        if (!config.isValid() || config.apiKey.isBlank()) {
            showError("请先在设置中配置API")
            return
        }

        val chatAPI = ChatAPI(config)

        questions.forEachIndexed { index, question ->
            answers[question.id] = Answer(question.id)

            handler.postDelayed({
                if (index == 0) {
                    hideLoading()
                }

                chatAPI.solveQuestion(question, object : StreamingCallback {
                    override fun onChunk(text: String) {
                        handler.post {
                            val answer = answers[question.id]
                            if (answer != null) {
                                answer.text += text
                                updateAnswerText(question.id, answer.text)
                            }
                        }
                    }

                    override fun onComplete() {
                        handler.post {
                            answers[question.id]?.isComplete = true
                        }
                    }

                    override fun onError(error: Exception) {
                        handler.post {
                            answers[question.id]?.error = error.message
                            updateAnswerText(question.id, "错误: ${error.message}")
                        }
                    }
                })
            }, index * 500L)
        }
    }

    private fun updateAnswerText(questionId: Int, text: String) {
        answerViews[questionId]?.let { view ->
            view.findViewById<TextView>(R.id.tvAnswerText)?.text = text
        }
    }

    private fun dismissPopup() {
        isPopupShowing = false
        try {
            popupView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            // View might not be attached
        }
        popupView = null
        currentBitmap?.recycle()
        currentBitmap = null
        job.cancel()
        stopForeground(true)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isPopupShowing = false
        try {
            popupView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            // Already removed
        }
        currentBitmap?.recycle()
        job.cancel()
    }
}
