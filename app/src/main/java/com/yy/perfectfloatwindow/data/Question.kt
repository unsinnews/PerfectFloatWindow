package com.yy.perfectfloatwindow.data

data class Question(
    val id: Int,
    val text: String,
    val latex: String? = null,
    val type: QuestionType = QuestionType.TEXT
)

enum class QuestionType {
    TEXT,
    MATH,
    MULTIPLE_CHOICE,
    FILL_BLANK
}
