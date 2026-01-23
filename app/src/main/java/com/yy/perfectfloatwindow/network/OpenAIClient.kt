package com.yy.perfectfloatwindow.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yy.perfectfloatwindow.data.AIConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIClient(private val config: AIConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun chatCompletion(
        messages: List<Map<String, Any>>,
        stream: Boolean = false
    ): Response {
        val requestBody = buildChatRequest(messages, stream)
        val request = buildRequest("/chat/completions", requestBody)
        return client.newCall(request).execute()
    }

    fun streamChatCompletion(
        messages: List<Map<String, Any>>,
        callback: StreamingCallback
    ): Call {
        val requestBody = buildChatRequest(messages, stream = true)
        val request = buildRequest("/chat/completions", requestBody)

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    callback.onError(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (call.isCanceled()) return

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    callback.onError(IOException("API error ${response.code}: $errorBody"))
                    return
                }

                try {
                    response.body?.let { body ->
                        val reader = BufferedReader(body.charStream())
                        var line: String?
                        while (!call.isCanceled() && reader.readLine().also { line = it } != null) {
                            val currentLine = line ?: continue
                            if (currentLine.startsWith("data: ")) {
                                val data = currentLine.substring(6).trim()
                                if (data == "[DONE]") {
                                    if (!call.isCanceled()) {
                                        callback.onComplete()
                                    }
                                    break
                                }
                                try {
                                    val json = JsonParser.parseString(data).asJsonObject
                                    val choices = json.getAsJsonArray("choices")
                                    if (choices != null && choices.size() > 0) {
                                        val delta = choices[0].asJsonObject
                                            .getAsJsonObject("delta")
                                        val content = delta?.get("content")?.asString
                                        if (!content.isNullOrEmpty() && !call.isCanceled()) {
                                            callback.onChunk(content)
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Skip malformed chunks
                                }
                            }
                        }
                        reader.close()
                    }
                } catch (e: Exception) {
                    if (!call.isCanceled()) {
                        callback.onError(e)
                    }
                }
            }
        })
        return call
    }

    private fun buildChatRequest(messages: List<Map<String, Any>>, stream: Boolean): RequestBody {
        val body = mapOf(
            "model" to config.modelId,
            "messages" to messages,
            "stream" to stream
        )
        return gson.toJson(body).toRequestBody(jsonMediaType)
    }

    private fun buildRequest(endpoint: String, body: RequestBody): Request {
        val url = normalizeBaseUrl(config.baseUrl) + endpoint
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
    }

    private fun normalizeBaseUrl(url: String): String {
        var normalized = url.trim()
        if (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }
        // Remove trailing /v1 if present (we'll add it)
        if (normalized.endsWith("/v1")) {
            // Keep it as is
        } else if (!normalized.contains("/v1")) {
            // Add /v1 if not present
            normalized = "$normalized/v1"
        }
        return normalized
    }

    companion object {
        fun parseNonStreamingResponse(response: Response): String {
            val body = response.body?.string() ?: throw IOException("Empty response")
            val json = JsonParser.parseString(body).asJsonObject
            val choices = json.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices[0].asJsonObject.getAsJsonObject("message")
                return message?.get("content")?.asString ?: ""
            }
            throw IOException("Invalid response format: $body")
        }
    }
}
