package com.yy.perfectfloatwindow.utils

import android.content.Context
import android.util.Log
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.util.concurrent.Executors

/**
 * Utility class for rendering Markdown and LaTeX math formulas in TextViews.
 */
object MarkdownRenderer {

    private const val TAG = "MarkdownRenderer"

    @Volatile
    private var markwon: Markwon? = null

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Get or create the Markwon instance.
     */
    private fun getInstance(context: Context): Markwon {
        return markwon ?: synchronized(this) {
            markwon ?: createMarkwon(context.applicationContext).also {
                markwon = it
                Log.d(TAG, "Markwon instance created")
            }
        }
    }

    private fun createMarkwon(context: Context): Markwon {
        val textSize = 16f * context.resources.displayMetrics.scaledDensity

        return Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(JLatexMathPlugin.create(textSize) { builder ->
                builder.inlinesEnabled(true)
                builder.blocksEnabled(true)
                builder.executorService(executor)
            })
            .build()
    }

    /**
     * Preprocess AI response text to handle common formatting issues.
     */
    private fun preprocessAIResponse(text: String): String {
        if (text.isEmpty()) return text

        var processed = text

        // Convert \[ \] to $$ $$ for block math
        processed = processed.replace("\\[", "$$")
        processed = processed.replace("\\]", "$$")

        // Convert \( \) to $ $ for inline math
        processed = processed.replace("\\(", "$")
        processed = processed.replace("\\)", "$")

        return processed
    }

    /**
     * Render AI response with Markdown and LaTeX support.
     */
    fun renderAIResponse(context: Context, textView: TextView, text: String) {
        if (text.isEmpty()) {
            textView.text = ""
            return
        }

        try {
            val processed = preprocessAIResponse(text)
            val markwonInstance = getInstance(context)
            markwonInstance.setMarkdown(textView, processed)
        } catch (e: Exception) {
            Log.e(TAG, "Markdown rendering failed", e)
            // Fallback to plain text
            textView.text = text
        }
    }
}
