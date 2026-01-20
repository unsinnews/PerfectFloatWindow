package com.yy.perfectfloatwindow.network

import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.yy.perfectfloatwindow.data.AIConfig
import com.yy.perfectfloatwindow.data.Question
import com.yy.perfectfloatwindow.data.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VisionAPI(private val config: AIConfig) {

    private val gson = Gson()

    companion object {
        val OCR_SYSTEM_PROMPT = """你是一个专业的题目识别助手。请分析图片中的内容，提取所有题目信息。

要求：
1. 识别图片中的所有题目，包括选择题、填空题、计算题、问答题等
2. 如果有数学公式，请转换为LaTeX格式
3. 保留题目的原始编号（如"1."、"第一题"等）
4. 如果有选项，保留完整的选项内容

请以JSON格式返回，格式如下：
{
  "questions": [
    {
      "id": 1,
      "text": "完整的题目文本内容，包括选项",
      "latex": "如果有数学公式则填写LaTeX表达式，否则为null",
      "type": "TEXT"
    }
  ]
}

type可选值：TEXT（普通文字题）、MATH（数学题）、MULTIPLE_CHOICE（选择题）、FILL_BLANK（填空题）

如果无法识别任何题目，返回：{"questions": []}
只返回JSON，不要有其他文字。""".trimIndent()
    }

    suspend fun extractQuestions(bitmap: Bitmap): List<Question> = withContext(Dispatchers.IO) {
        val base64Image = BitmapUtils.toDataUrl(bitmap)
        val client = OpenAIClient(config)

        val messages = listOf(
            mapOf("role" to "system", "content" to OCR_SYSTEM_PROMPT),
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to base64Image)
                    ),
                    mapOf(
                        "type" to "text",
                        "text" to "请识别图片中的所有题目，按JSON格式返回。"
                    )
                )
            )
        )

        val response = client.chatCompletion(messages, stream = false)
        val content = OpenAIClient.parseNonStreamingResponse(response)
        parseQuestionsFromJson(content)
    }

    private fun parseQuestionsFromJson(json: String): List<Question> {
        return try {
            // Try to extract JSON from the response (in case there's extra text)
            val jsonStart = json.indexOf('{')
            val jsonEnd = json.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) {
                return listOf(createFallbackQuestion(json))
            }

            val cleanJson = json.substring(jsonStart, jsonEnd + 1)
            val jsonObject = JsonParser.parseString(cleanJson).asJsonObject
            val questionsArray = jsonObject.getAsJsonArray("questions")

            if (questionsArray == null || questionsArray.size() == 0) {
                return emptyList()
            }

            questionsArray.mapIndexed { index, element ->
                val obj = element.asJsonObject
                Question(
                    id = obj.get("id")?.asInt ?: (index + 1),
                    text = obj.get("text")?.asString ?: "",
                    latex = obj.get("latex")?.let { if (it.isJsonNull) null else it.asString },
                    type = parseQuestionType(obj.get("type")?.asString)
                )
            }.filter { it.text.isNotBlank() }
        } catch (e: Exception) {
            // If parsing fails, return the whole response as a single question
            listOf(createFallbackQuestion(json))
        }
    }

    private fun parseQuestionType(type: String?): QuestionType {
        return when (type?.uppercase()) {
            "MATH" -> QuestionType.MATH
            "MULTIPLE_CHOICE" -> QuestionType.MULTIPLE_CHOICE
            "FILL_BLANK" -> QuestionType.FILL_BLANK
            else -> QuestionType.TEXT
        }
    }

    private fun createFallbackQuestion(text: String): Question {
        return Question(
            id = 1,
            text = text.trim(),
            type = QuestionType.TEXT
        )
    }
}
