package com.yy.perfectfloatwindow.network

interface StreamingCallback {
    fun onChunk(text: String)
    fun onComplete()
    fun onError(error: Exception)
}
