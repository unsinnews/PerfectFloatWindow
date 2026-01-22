package com.yy.perfectfloatwindow.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.util.Log
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import ru.noties.jlatexmath.JLatexMathDrawable
import java.util.concurrent.Executors

/**
 * Utility class for rendering Markdown and LaTeX in TextViews.
 */
object MarkdownRenderer {

    private const val TAG = "MarkdownRenderer"

    @Volatile
    private var basicMarkwon: Markwon? = null

    @Volatile
    private var latexMarkwon: Markwon? = null

    @Volatile
    private var latexFailed = false

    private val executor = Executors.newSingleThreadExecutor()

    private fun getBasicMarkwon(context: Context): Markwon {
        return basicMarkwon ?: synchronized(this) {
            basicMarkwon ?: Markwon.builder(context.applicationContext)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(HtmlPlugin.create())
                .build()
                .also {
                    basicMarkwon = it
                    Log.d(TAG, "Basic Markwon created")
                }
        }
    }

    private fun getLatexMarkwon(context: Context): Markwon? {
        if (latexFailed) return null

        return latexMarkwon ?: synchronized(this) {
            if (latexFailed) return null

            try {
                val textSize = 18f * context.resources.displayMetrics.scaledDensity

                Markwon.builder(context.applicationContext)
                    .usePlugin(MarkwonInlineParserPlugin.create())
                    .usePlugin(JLatexMathPlugin.create(textSize) { builder ->
                        builder.inlinesEnabled(true)
                        builder.blocksEnabled(true)
                        builder.executorService(executor)
                    })
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(context))
                    .usePlugin(HtmlPlugin.create())
                    .build()
                    .also {
                        latexMarkwon = it
                        Log.d(TAG, "LaTeX Markwon created successfully")
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create LaTeX Markwon", e)
                latexFailed = true
                null
            }
        }
    }

    /**
     * Render a LaTeX formula to a Bitmap for inline display.
     */
    private fun renderLatexToBitmap(context: Context, latex: String, textSize: Float): Bitmap? {
        return try {
            val drawable = JLatexMathDrawable.builder(latex)
                .textSize(textSize)
                .color(Color.BLACK)
                .build()

            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight

            if (width <= 0 || height <= 0) return null

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render LaTeX: $latex", e)
            null
        }
    }

    /**
     * Convert \[...\] to $$...$$ and \(...\) to $...$
     */
    private fun normalizeDelimiters(text: String): String {
        return text
            .replace("\\[", "$$")
            .replace("\\]", "$$")
            .replace("\\(", "$")
            .replace("\\)", "$")
    }

    /**
     * Process text with inline LaTeX rendering.
     * Block math ($$...$$) is handled by Markwon.
     * Inline math ($...$) is rendered manually as ImageSpan.
     */
    private fun processInlineLatex(context: Context, textView: TextView, text: String) {
        val normalized = normalizeDelimiters(text)
        val textSize = textView.textSize

        // First, let Markwon handle the basic markdown and block LaTeX
        val markwon = getLatexMarkwon(context) ?: getBasicMarkwon(context)

        // Find all inline math $...$ (not $$)
        val inlinePattern = Regex("""(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)""")
        val matches = inlinePattern.findAll(normalized).toList()

        if (matches.isEmpty()) {
            // No inline math, just use Markwon directly
            markwon.setMarkdown(textView, normalized)
            return
        }

        // Replace inline math with placeholders, render block math with Markwon
        var processedText = normalized
        val placeholders = mutableMapOf<String, String>()

        matches.forEachIndexed { index, match ->
            val placeholder = "%%LATEX_INLINE_${index}%%"
            placeholders[placeholder] = match.groupValues[1]
            processedText = processedText.replaceFirst(match.value, placeholder)
        }

        // Render markdown (with block LaTeX) first
        markwon.setMarkdown(textView, processedText)

        // Now replace placeholders with rendered LaTeX images
        val spannable = SpannableStringBuilder(textView.text)

        placeholders.forEach { (placeholder, latex) ->
            val start = spannable.indexOf(placeholder)
            if (start >= 0) {
                val bitmap = renderLatexToBitmap(context, latex, textSize * 0.9f)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(context.resources, bitmap)
                    drawable.setBounds(0, 0, bitmap.width, bitmap.height)

                    val imageSpan = ImageSpan(drawable, ImageSpan.ALIGN_BASELINE)
                    spannable.setSpan(
                        imageSpan,
                        start,
                        start + placeholder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.replace(start, start + placeholder.length, "\uFFFC")
                } else {
                    // Fallback: just show the LaTeX source
                    spannable.replace(start, start + placeholder.length, latex)
                }
            }
        }

        textView.text = spannable
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
            processInlineLatex(context, textView, text)
        } catch (e: Throwable) {
            Log.e(TAG, "Render error", e)
            // Fallback to basic rendering
            try {
                getBasicMarkwon(context).setMarkdown(textView, text)
            } catch (e2: Throwable) {
                textView.text = text
            }
        }
    }
}
