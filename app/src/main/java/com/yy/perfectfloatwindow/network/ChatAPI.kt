package com.yy.perfectfloatwindow.network

import com.yy.perfectfloatwindow.data.AIConfig
import com.yy.perfectfloatwindow.data.Question
import com.yy.perfectfloatwindow.data.QuestionType

class ChatAPI(private val config: AIConfig) {

    companion object {
        const val SOLVER_SYSTEM_PROMPT = """你是一个专业的解题助手，擅长解答各类学科题目。请按照以下要求回答问题：

1. 解题步骤清晰，逻辑严谨
2. 如果是数学题，请展示详细的计算过程，数学公式使用LaTeX格式（用$$包围）
3. 如果是选择题，先分析各选项，再给出正确答案
4. 如果是填空题，直接给出答案并简要说明理由
5. 使用中文回答
6. 答案要准确、完整

格式要求：
- 使用简洁清晰的语言
- 重要步骤或结论可以加粗显示
- 最终答案用【答案】标记"""
    }

    fun solveQuestion(question: Question, callback: StreamingCallback) {
        val client = OpenAIClient(config)

        val userPrompt = buildUserPrompt(question)

        val messages = listOf(
            mapOf("role" to "system", "content" to SOLVER_SYSTEM_PROMPT),
            mapOf("role" to "user", "content" to userPrompt)
        )

        client.streamChatCompletion(messages, callback)
    }

    fun solveQuestionSync(question: Question): String {
        val client = OpenAIClient(config)

        val userPrompt = buildUserPrompt(question)

        val messages = listOf(
            mapOf("role" to "system", "content" to SOLVER_SYSTEM_PROMPT),
            mapOf("role" to "user", "content" to userPrompt)
        )

        val response = client.chatCompletion(messages, stream = false)
        return OpenAIClient.parseNonStreamingResponse(response)
    }

    private fun buildUserPrompt(question: Question): String {
        val sb = StringBuilder()

        sb.append("请解答以下题目：\n\n")
        sb.append(question.text)

        if (!question.latex.isNullOrBlank()) {
            sb.append("\n\n数学公式：")
            sb.append(question.latex)
        }

        when (question.type) {
            QuestionType.MULTIPLE_CHOICE -> {
                sb.append("\n\n请分析各选项并给出正确答案。")
            }
            QuestionType.FILL_BLANK -> {
                sb.append("\n\n请给出填空的答案。")
            }
            QuestionType.MATH -> {
                sb.append("\n\n请展示详细的解题步骤和计算过程。")
            }
            else -> {}
        }

        return sb.toString()
    }
}
