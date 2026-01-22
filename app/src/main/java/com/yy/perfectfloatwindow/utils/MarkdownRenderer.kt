package com.yy.perfectfloatwindow.utils

import android.content.Context
import android.util.Log
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Utility class for rendering Markdown in TextViews.
 */
object MarkdownRenderer {

    private const val TAG = "MarkdownRenderer"

    @Volatile
    private var markwon: Markwon? = null

    @Volatile
    private var markwonWithLatex: Markwon? = null

    @Volatile
    private var latexAvailable: Boolean? = null

    /**
     * Get basic Markwon instance (always works).
     */
    private fun getBasicInstance(context: Context): Markwon {
        return markwon ?: synchronized(this) {
            markwon ?: createBasicMarkwon(context.applicationContext).also {
                markwon = it
                Log.d(TAG, "Basic Markwon instance created")
            }
        }
    }

    /**
     * Try to get Markwon with LaTeX support.
     */
    private fun getLatexInstance(context: Context): Markwon? {
        // If we already know LaTeX is not available, skip
        if (latexAvailable == false) return null

        return markwonWithLatex ?: synchronized(this) {
            if (latexAvailable == false) return null

            try {
                markwonWithLatex ?: createLatexMarkwon(context.applicationContext).also {
                    markwonWithLatex = it
                    latexAvailable = true
                    Log.d(TAG, "LaTeX Markwon instance created")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create LaTeX Markwon: ${e.message}")
                latexAvailable = false
                null
            }
        }
    }

    private fun createBasicMarkwon(context: Context): Markwon {
        return Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    private fun createLatexMarkwon(context: Context): Markwon {
        // Dynamically load LaTeX plugin to avoid crash if not available
        val latexPluginClass = Class.forName("io.noties.markwon.ext.latex.JLatexMathPlugin")
        val createMethod = latexPluginClass.getMethod("create", Float::class.java)

        val textSize = 16f * context.resources.displayMetrics.scaledDensity
        val latexPlugin = createMethod.invoke(null, textSize)

        return Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(latexPlugin as io.noties.markwon.MarkwonPlugin)
            .build()
    }

    /**
     * Preprocess text to convert various LaTeX delimiter formats to standard $...$ and $$...$$ format.
     *
     * Supported input formats:
     * - \[...\] → $$...$$  (display/block math)
     * - \(...\) → $...$    (inline math)
     * - \\[...\\] → $$...$$ (escaped in JSON)
     * - \\(...\\) → $...$   (escaped in JSON)
     * - ```math...``` → $$...$$ (code block)
     * - ```latex...``` → $$...$$ (code block)
     * - \begin{equation}...\end{equation} → $$...$$
     * - \begin{align}...\end{align} → $$...$$
     * - \begin{aligned}...\end{aligned} → $$...$$
     */
    private fun preprocessLatex(text: String): String {
        var processed = text

        // Handle double-escaped (JSON) format first: \\[ \\] \\( \\)
        processed = processed.replace("\\\\[", "$$")
        processed = processed.replace("\\\\]", "$$")
        processed = processed.replace("\\\\(", "$")
        processed = processed.replace("\\\\)", "$")

        // Handle single-escaped format: \[ \] \( \)
        processed = processed.replace("\\[", "$$")
        processed = processed.replace("\\]", "$$")
        processed = processed.replace("\\(", "$")
        processed = processed.replace("\\)", "$")

        // Handle code block math: ```math ... ``` or ```latex ... ```
        processed = processed.replace(Regex("```math\\s*\\n?"), "\n$$")
        processed = processed.replace(Regex("```latex\\s*\\n?"), "\n$$")
        processed = processed.replace(Regex("\\n?```(?=\\s*\n|\\s*$)"), "$$\n")

        // Handle \begin{equation} ... \end{equation}
        processed = processed.replace(Regex("\\\\begin\\{equation\\*?\\}"), "$$")
        processed = processed.replace(Regex("\\\\end\\{equation\\*?\\}"), "$$")

        // Handle \begin{align} ... \end{align}
        processed = processed.replace(Regex("\\\\begin\\{align\\*?\\}"), "$$")
        processed = processed.replace(Regex("\\\\end\\{align\\*?\\}"), "$$")

        // Handle \begin{aligned} ... \end{aligned} (usually inside $$)
        processed = processed.replace(Regex("\\\\begin\\{aligned\\}"), "")
        processed = processed.replace(Regex("\\\\end\\{aligned\\}"), "")

        // Handle \begin{gather} ... \end{gather}
        processed = processed.replace(Regex("\\\\begin\\{gather\\*?\\}"), "$$")
        processed = processed.replace(Regex("\\\\end\\{gather\\*?\\}"), "$$")

        // Handle \begin{math} ... \end{math}
        processed = processed.replace(Regex("\\\\begin\\{math\\}"), "$")
        processed = processed.replace(Regex("\\\\end\\{math\\}"), "$")

        // Handle \begin{displaymath} ... \end{displaymath}
        processed = processed.replace(Regex("\\\\begin\\{displaymath\\}"), "$$")
        processed = processed.replace(Regex("\\\\end\\{displaymath\\}"), "$$")

        // Clean up multiple consecutive $$ (can happen after replacements)
        processed = processed.replace(Regex("\\$\\$\\s*\\$\\$"), "$$")

        return processed
    }

    /**
     * Render AI response with Markdown and optional LaTeX support.
     */
    fun renderAIResponse(context: Context, textView: TextView, text: String) {
        if (text.isEmpty()) {
            textView.text = ""
            return
        }

        try {
            // Try LaTeX-enabled rendering first
            val latexMarkwon = getLatexInstance(context)
            if (latexMarkwon != null) {
                val processed = preprocessLatex(text)
                latexMarkwon.setMarkdown(textView, processed)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "LaTeX rendering failed: ${e.message}")
            latexAvailable = false
            markwonWithLatex = null
        }

        // Fallback to basic Markdown
        try {
            val basicMarkwon = getBasicInstance(context)
            basicMarkwon.setMarkdown(textView, text)
        } catch (e: Exception) {
            Log.e(TAG, "Basic Markdown rendering failed: ${e.message}")
            textView.text = text
        }
    }
}
