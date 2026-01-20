package com.yy.perfectfloatwindow.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yy.perfectfloatwindow.R
import com.yy.perfectfloatwindow.data.AIConfig
import com.yy.perfectfloatwindow.data.AISettings
import com.yy.perfectfloatwindow.network.OpenAIClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var etOcrBaseUrl: EditText
    private lateinit var etOcrModelId: EditText
    private lateinit var etFastBaseUrl: EditText
    private lateinit var etFastModelId: EditText
    private lateinit var etDeepBaseUrl: EditText
    private lateinit var etDeepModelId: EditText
    private lateinit var tvTestResult: TextView
    private lateinit var btnTest: Button

    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadSettings()
        setupButtons()
    }

    private fun initViews() {
        etApiKey = findViewById(R.id.etApiKey)
        etOcrBaseUrl = findViewById(R.id.etOcrBaseUrl)
        etOcrModelId = findViewById(R.id.etOcrModelId)
        etFastBaseUrl = findViewById(R.id.etFastBaseUrl)
        etFastModelId = findViewById(R.id.etFastModelId)
        etDeepBaseUrl = findViewById(R.id.etDeepBaseUrl)
        etDeepModelId = findViewById(R.id.etDeepModelId)
        tvTestResult = findViewById(R.id.tvTestResult)
        btnTest = findViewById(R.id.btnTest)
    }

    private fun loadSettings() {
        // API Key
        etApiKey.setText(AISettings.getApiKey(this))

        // OCR Config
        val ocrConfig = AISettings.getOCRConfig(this)
        etOcrBaseUrl.setText(ocrConfig.baseUrl)
        etOcrModelId.setText(ocrConfig.modelId)

        // Fast Config
        val fastConfig = AISettings.getFastConfig(this)
        etFastBaseUrl.setText(fastConfig.baseUrl)
        etFastModelId.setText(fastConfig.modelId)

        // Deep Config
        val deepConfig = AISettings.getDeepConfig(this)
        etDeepBaseUrl.setText(deepConfig.baseUrl)
        etDeepModelId.setText(deepConfig.modelId)
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveSettings()
        }

        btnTest.setOnClickListener {
            testApi()
        }
    }

    private fun saveSettings() {
        saveSettingsWithoutFinish()
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun saveSettingsWithoutFinish() {
        // Save API Key
        AISettings.saveApiKey(this, etApiKey.text.toString().trim())

        // Save OCR Config
        AISettings.saveOCRConfig(
            this,
            etOcrBaseUrl.text.toString().trim(),
            etOcrModelId.text.toString().trim()
        )

        // Save Fast Config
        AISettings.saveFastConfig(
            this,
            etFastBaseUrl.text.toString().trim(),
            etFastModelId.text.toString().trim()
        )

        // Save Deep Config
        AISettings.saveDeepConfig(
            this,
            etDeepBaseUrl.text.toString().trim(),
            etDeepModelId.text.toString().trim()
        )
    }

    private fun testApi() {
        val apiKey = etApiKey.text.toString().trim()
        val baseUrl = etFastBaseUrl.text.toString().trim()
        val modelId = etFastModelId.text.toString().trim()

        if (apiKey.isBlank()) {
            showTestResult("Error: API Key is required", false)
            return
        }

        if (baseUrl.isBlank()) {
            showTestResult("Error: Base URL is required", false)
            return
        }

        if (modelId.isBlank()) {
            showTestResult("Error: Model ID is required", false)
            return
        }

        btnTest.isEnabled = false
        btnTest.text = "Testing..."
        showTestResult("Testing API connection...", null)

        coroutineScope.launch {
            try {
                val config = AIConfig(baseUrl, modelId, apiKey)
                val client = OpenAIClient(config)

                val messages = listOf(
                    mapOf("role" to "user", "content" to "Say 'API connection successful!' in exactly those words.")
                )

                val response = withContext(Dispatchers.IO) {
                    client.chatCompletion(messages, stream = false)
                }

                if (response.isSuccessful) {
                    val content = OpenAIClient.parseNonStreamingResponse(response)
                    // Auto-save settings on successful test
                    withContext(Dispatchers.Main) {
                        saveSettingsWithoutFinish()
                        showTestResult("API测试成功！设置已自动保存\n\nModel: $modelId\nResponse: $content", true)
                    }
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    showTestResult("API测试失败！\n\nStatus: ${response.code}\nError: $errorBody", false)
                }
            } catch (e: Exception) {
                showTestResult("API Test Failed!\n\nError: ${e.message}", false)
            } finally {
                withContext(Dispatchers.Main) {
                    btnTest.isEnabled = true
                    btnTest.text = "Test API"
                }
            }
        }
    }

    private fun showTestResult(message: String, success: Boolean?) {
        tvTestResult.visibility = View.VISIBLE
        tvTestResult.text = message
        tvTestResult.setTextColor(
            when (success) {
                true -> 0xFF4CAF50.toInt()  // Green
                false -> 0xFFF44336.toInt() // Red
                null -> 0xFF757575.toInt()  // Gray
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
