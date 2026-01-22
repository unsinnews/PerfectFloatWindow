package com.yy.perfectfloatwindow.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
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
 *
 * Supports:
 * - Standard Markdown syntax (headers, bold, italic, lists, code blocks, etc.)
 * - LaTeX math formulas (inline $...$ and block $$...$$)
 * - Tables
 * - Strikethrough
 * - HTML tags
 * - Auto-linking URLs
 */
object MarkdownRenderer {

    private const val TAG = "MarkdownRenderer"

    @Volatile
    private var markwonWithLatex: Markwon? = null

    @Volatile
    private var markwonBasic: Markwon? = null

    @Volatile
    private var isLatexAvailable = true

    @Volatile
    private var isInitializing = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Initialize Markwon in the background. Call this early (e.g., in Application.onCreate)
     * to avoid initialization delay on first render.
     */
    fun init(context: Context) {
        if (markwonBasic != null) return

        val appContext = context.applicationContext
        executor.execute {
            try {
                // Initialize basic Markwon first (fast)
                getBasicInstance(appContext)

                // Then try to initialize LaTeX support (slower, may fail)
                getLatexInstance(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Markwon", e)
            }
        }
    }

    /**
     * Get basic Markwon instance without LaTeX (fast, reliable).
     */
    private fun getBasicInstance(context: Context): Markwon {
        return markwonBasic ?: synchronized(this) {
            markwonBasic ?: createBasicMarkwon(context.applicationContext).also { markwonBasic = it }
        }
    }

    /**
     * Get Markwon instance with LaTeX support (may fail on some devices).
     */
    private fun getLatexInstance(context: Context): Markwon? {
        if (!isLatexAvailable) return null

        return markwonWithLatex ?: synchronized(this) {
            if (!isLatexAvailable) return null
            markwonWithLatex ?: try {
                createLatexMarkwon(context.applicationContext).also { markwonWithLatex = it }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create LaTeX Markwon, falling back to basic", e)
                isLatexAvailable = false
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
        val textSize = getTextSizeForLatex(context)
        return Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(JLatexMathPlugin.create(textSize) { builder ->
                builder.inlinesEnabled(true)
                builder.blocksEnabled(true)
            })
            .build()
    }

    /**
     * Calculate appropriate text size for LaTeX formulas based on context.
     */
    private fun getTextSizeForLatex(context: Context): Float {
        val density = context.resources.displayMetrics.scaledDensity
        return 14f * density
    }

    /**
     * Render markdown text to a TextView with fallback support.
     */
    fun render(context: Context, textView: TextView, markdown: String) {
        if (markdown.isEmpty()) {
            textView.text = ""
            return
        }

        try {
            // Try LaTeX-enabled Markwon first
            val latexMarkwon = getLatexInstance(context)
            if (latexMarkwon != null) {
                latexMarkwon.setMarkdown(textView, markdown)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "LaTeX rendering failed, falling back to basic", e)
            isLatexAvailable = false
            markwonWithLatex = null
        }

        // Fallback to basic Markwon
        try {
            val basicMarkwon = getBasicInstance(context)
            basicMarkwon.setMarkdown(textView, markdown)
        } catch (e: Exception) {
            Log.e(TAG, "Basic Markwon rendering failed, using plain text", e)
            textView.text = markdown
        }
    }

    /**
     * Render markdown and return the styled CharSequence.
     */
    fun toMarkdown(context: Context, markdown: String): CharSequence {
        if (markdown.isEmpty()) return ""

        try {
            val latexMarkwon = getLatexInstance(context)
            if (latexMarkwon != null) {
                return latexMarkwon.toMarkdown(markdown)
            }
        } catch (e: Exception) {
            Log.e(TAG, "LaTeX toMarkdown failed", e)
            isLatexAvailable = false
        }

        return try {
            getBasicInstance(context).toMarkdown(markdown)
        } catch (e: Exception) {
            Log.e(TAG, "Basic toMarkdown failed", e)
            markdown
        }
    }

    /**
     * Preprocess AI response text to handle common formatting issues.
     */
    fun preprocessAIResponse(text: String): String {
        if (text.isEmpty()) return text

        var processed = text

        // Convert \[ \] to $$ $$ for block math (common in AI responses)
        processed = processed.replace("\\[", "$$")
        processed = processed.replace("\\]", "$$")

        // Convert \( \) to $ $ for inline math (common in AI responses)
        processed = processed.replace("\\(", "$")
        processed = processed.replace("\\)", "$")

        return processed
    }

    /**
     * Render AI response with preprocessing and error handling.
     */
    fun renderAIResponse(context: Context, textView: TextView, text: String) {
        if (text.isEmpty()) {
            textView.text = ""
            return
        }

        try {
            val processed = preprocessAIResponse(text)
            render(context, textView, processed)
        } catch (e: Exception) {
            Log.e(TAG, "renderAIResponse failed, using plain text", e)
            textView.text = text
        }
    }
}
