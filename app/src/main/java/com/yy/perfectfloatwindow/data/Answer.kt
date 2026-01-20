package com.yy.perfectfloatwindow.data

data class Answer(
    val questionId: Int,
    var text: String = "",
    var isComplete: Boolean = false,
    var error: String? = null
)
