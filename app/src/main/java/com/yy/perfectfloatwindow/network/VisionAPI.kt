package com.yy.perfectfloatwindow.network

import android.graphics.Bitmap
import com.yy.perfectfloatwindow.data.AIConfig
import com.yy.perfectfloatwindow.data.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface OCRStreamingCallback {
    fun onChunk(text: String, currentQuestionIndex: Int)
    fun onQuestionReady(question: Question)
    fun onComplete()
    fun onError(error: Exception)
}

class VisionAPI(private val config: AIConfig) {

    companion object {
        val OCR_SYSTEM_PROMPT = """# 角色
你是一位拥有20年经验的资深教育专家和OCR专家，擅长精准识别各类试卷题目并理解题目结构。

# 任务
分析图片中的试卷/题目，完整提取每道题目的内容。

# 思考步骤（请在脑中完成，不要输出思考过程）
1. 【全局扫描】先浏览整张图片，识别有多少道独立的大题
2. 【结构分析】分析每道大题是否包含小题、材料、图表
3. 【归属判断】判断哪些内容属于同一道题（⚠️最关键的一步）
4. 【逐题提取】按顺序提取每道完整题目
5. 【格式检查】确认换行符使用正确

# ⚠️ 小题归属判断（最重要！错误会导致解题失败）

## 黄金法则
> 宁可错误合并，也绝不错误拆分！不确定时，合并为一道题。

## 必须合并的情况 ✓
| 情况 | 示例 |
|------|------|
| 主题号+子题号 | 1.(1)(2)(3) 或 一、1.2.3. |
| 材料+问题 | 阅读下文...问：/答：|
| 图表+问题 | [图表] + 根据图表回答... |
| 共同条件 | 已知...求(1)...(2)... |
| 递进关系 | 求A...再求B...由此得C |

## 合并信号词
"根据材料"、"根据上文"、"由此可知"、"结合上述"、"综上"、"根据图表"、"看图回答"

## 绝对禁止 ✗
- ✗ 把(1)(2)(3)拆成3道独立题
- ✗ 把材料和问题分开
- ✗ 把图表和相关问题分开
- ✗ 把"已知...求..."类题目的小问分开

# 输出格式

## 题目内容要求
- 题型标识：放最前面，如（单选题）、（多选题）、（填空题）
- 题号：保留原始编号
- 选项：完整包含A/B/C/D所有选项
- 填空：用____表示空格
- 公式：使用LaTeX格式
- 表格：使用Markdown代码框
```表格名称
| 列1 | 列2 |
|-----|-----|
| 值1 | 值2 |
```

## 换行规则（严格遵守！）
- 同一题内部：单个换行\n
- 不同题之间：两个换行\n\n
- ⚠️ 大题和小题之间只用单个换行，绝不能用两个换行！

# 输出前自检
□ 是否有小题被错误拆分？
□ 材料和问题是否在一起？
□ 换行符使用是否正确？

# 开始
只输出题目内容，不要输出任何思考过程或解释。""".trimIndent()
    }

    fun extractQuestionsStreaming(bitmap: Bitmap, callback: OCRStreamingCallback) {
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
                        "text" to "请识别图片中的所有题目。"
                    )
                )
            )
        )

        val accumulatedText = StringBuilder()
        var displayedLength = 0  // 已经通过onChunk发送显示的长度
        var questionIndex = 1

        client.streamChatCompletion(messages, object : StreamingCallback {
            override fun onChunk(text: String) {
                accumulatedText.append(text)

                val content = accumulatedText.toString()

                // 循环检查是否有分隔符（可能一次chunk中有多个分隔符）
                while (true) {
                    val separatorIndex = content.indexOf("\n\n", displayedLength)

                    if (separatorIndex == -1) {
                        // 没有分隔符，发送所有未显示的内容到当前题目
                        val newContent = content.substring(displayedLength)
                        if (newContent.isNotEmpty()) {
                            callback.onChunk(newContent, questionIndex)
                            displayedLength = content.length
                        }
                        break
                    }

                    // 有分隔符
                    // 1. 发送分隔符之前未显示的内容到当前题目
                    val beforeSeparator = content.substring(displayedLength, separatorIndex)
                    if (beforeSeparator.isNotEmpty()) {
                        callback.onChunk(beforeSeparator, questionIndex)
                    }

                    // 2. 完成当前题目
                    val questionText = content.substring(0, separatorIndex).trim()
                    if (questionText.isNotBlank()) {
                        val question = Question(id = questionIndex, text = questionText)
                        callback.onQuestionReady(question)
                        questionIndex++
                    }

                    // 3. 更新已显示位置到分隔符之后（跳过\n\n）
                    displayedLength = separatorIndex + 2

                    // 4. 继续循环检查是否还有更多分隔符
                }
            }

            override fun onComplete() {
                // 处理最后剩余的内容
                val content = accumulatedText.toString()
                val remainingText = content.substring(
                    content.lastIndexOf("\n\n").let { if (it == -1) 0 else it + 2 }
                ).trim()
                if (remainingText.isNotBlank()) {
                    val question = Question(id = questionIndex, text = remainingText)
                    callback.onQuestionReady(question)
                }
                callback.onComplete()
            }

            override fun onError(error: Exception) {
                callback.onError(error)
            }
        })
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
                        "text" to "请识别图片中的所有题目。"
                    )
                )
            )
        )

        val response = client.chatCompletion(messages, stream = false)
        val content = OpenAIClient.parseNonStreamingResponse(response)
        parseQuestionsFromText(content)
    }

    /**
     * 按两个连续换行符分割文本，解析为题目列表
     */
    private fun parseQuestionsFromText(text: String): List<Question> {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            return emptyList()
        }

        // 按两个或更多连续换行符分割题目
        val questionTexts = trimmedText.split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (questionTexts.isEmpty()) {
            listOf(Question(id = 1, text = trimmedText))
        } else {
            questionTexts.mapIndexed { index, questionText ->
                Question(id = index + 1, text = questionText)
            }
        }
    }
}
