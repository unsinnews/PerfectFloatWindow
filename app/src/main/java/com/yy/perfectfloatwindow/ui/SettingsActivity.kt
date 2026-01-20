package com.yy.perfectfloatwindow.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yy.perfectfloatwindow.R
import com.yy.perfectfloatwindow.data.AISettings

class SettingsActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var etOcrBaseUrl: EditText
    private lateinit var etOcrModelId: EditText
    private lateinit var etFastBaseUrl: EditText
    private lateinit var etFastModelId: EditText
    private lateinit var etDeepBaseUrl: EditText
    private lateinit var etDeepModelId: EditText

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
    }

    private fun saveSettings() {
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

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
