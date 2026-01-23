package com.yy.perfectfloatwindow.data

import android.content.Context
import android.content.SharedPreferences

object AISettings {
    private const val PREFS_NAME = "ai_settings"

    // API Key
    private const val KEY_API_KEY = "api_key"

    // OCR AI
    private const val KEY_OCR_BASE_URL = "ocr_base_url"
    private const val KEY_OCR_MODEL_ID = "ocr_model_id"

    // Fast Mode AI
    private const val KEY_FAST_BASE_URL = "fast_base_url"
    private const val KEY_FAST_MODEL_ID = "fast_model_id"

    // Deep Mode AI
    private const val KEY_DEEP_BASE_URL = "deep_base_url"
    private const val KEY_DEEP_MODEL_ID = "deep_model_id"

    // Screenshot Settings
    private const val KEY_AUTO_DELETE_SCREENSHOT = "auto_delete_screenshot"

    // Default values
    private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    private const val DEFAULT_OCR_MODEL = "gpt-4o"
    private const val DEFAULT_FAST_MODEL = "gpt-4o-mini"
    private const val DEFAULT_DEEP_MODEL = "gpt-4o"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // API Key
    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
    }

    // OCR Config
    fun getOCRConfig(context: Context): AIConfig {
        val prefs = getPrefs(context)
        return AIConfig(
            baseUrl = prefs.getString(KEY_OCR_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            modelId = prefs.getString(KEY_OCR_MODEL_ID, DEFAULT_OCR_MODEL) ?: DEFAULT_OCR_MODEL,
            apiKey = getApiKey(context)
        )
    }

    fun saveOCRConfig(context: Context, baseUrl: String, modelId: String) {
        getPrefs(context).edit()
            .putString(KEY_OCR_BASE_URL, baseUrl)
            .putString(KEY_OCR_MODEL_ID, modelId)
            .apply()
    }

    // Fast Mode Config
    fun getFastConfig(context: Context): AIConfig {
        val prefs = getPrefs(context)
        return AIConfig(
            baseUrl = prefs.getString(KEY_FAST_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            modelId = prefs.getString(KEY_FAST_MODEL_ID, DEFAULT_FAST_MODEL) ?: DEFAULT_FAST_MODEL,
            apiKey = getApiKey(context)
        )
    }

    fun saveFastConfig(context: Context, baseUrl: String, modelId: String) {
        getPrefs(context).edit()
            .putString(KEY_FAST_BASE_URL, baseUrl)
            .putString(KEY_FAST_MODEL_ID, modelId)
            .apply()
    }

    // Deep Mode Config
    fun getDeepConfig(context: Context): AIConfig {
        val prefs = getPrefs(context)
        return AIConfig(
            baseUrl = prefs.getString(KEY_DEEP_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            modelId = prefs.getString(KEY_DEEP_MODEL_ID, DEFAULT_DEEP_MODEL) ?: DEFAULT_DEEP_MODEL,
            apiKey = getApiKey(context)
        )
    }

    fun saveDeepConfig(context: Context, baseUrl: String, modelId: String) {
        getPrefs(context).edit()
            .putString(KEY_DEEP_BASE_URL, baseUrl)
            .putString(KEY_DEEP_MODEL_ID, modelId)
            .apply()
    }

    // Auto Delete Screenshot (default: true)
    fun isAutoDeleteScreenshot(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_DELETE_SCREENSHOT, true)
    }

    fun setAutoDeleteScreenshot(context: Context, autoDelete: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_DELETE_SCREENSHOT, autoDelete).apply()
    }
}
