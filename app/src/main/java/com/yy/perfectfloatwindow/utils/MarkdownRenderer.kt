package com.yy.perfectfloatwindow.utils

import android.content.Context
import android.util.Log
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.util.concurrent.Executors

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

    private val latexExecutor = Executors.newSingleThreadExecutor()

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
        if (latexAvailable == false) return null

        return markwonWithLatex ?: synchronized(this) {
            if (latexAvailable == false) return null

            try {
                markwonWithLatex ?: createLatexMarkwon(context.applicationContext).also {
                    markwonWithLatex = it
                    latexAvailable = true
                    Log.d(TAG, "LaTeX Markwon instance created")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create LaTeX Markwon: ${e.message}", e)
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
        val textSize = 16f * context.resources.displayMetrics.scaledDensity

        // Use reflection to create JLatexMathPlugin with builder configuration
        val pluginClass = Class.forName("io.noties.markwon.ext.latex.JLatexMathPlugin")
        val builderConfigClass = Class.forName("io.noties.markwon.ext.latex.JLatexMathPlugin\$BuilderConfigure")

        // Create a dynamic proxy for BuilderConfigure
        val builderConfig = java.lang.reflect.Proxy.newProxyInstance(
            pluginClass.classLoader,
            arrayOf(builderConfigClass)
        ) { _, method, args ->
            if (method.name == "configureBuilder" && args != null && args.isNotEmpty()) {
                val builder = args[0]
                val builderClass = builder.javaClass

                // Enable inline math: $...$
                builderClass.getMethod("inlinesEnabled", Boolean::class.java)
                    .invoke(builder, true)

                // Enable block math: $$...$$
                builderClass.getMethod("blocksEnabled", Boolean::class.java)
                    .invoke(builder, true)

                // Set executor for background rendering
                try {
                    builderClass.getMethod("executorService", java.util.concurrent.ExecutorService::class.java)
                        .invoke(builder, latexExecutor)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set executor service: ${e.message}")
                }
            }
            null
        }

        // Call JLatexMathPlugin.create(textSize, builderConfig)
        val createMethod = pluginClass.getMethod("create", Float::class.java, builderConfigClass)
        val latexPlugin = createMethod.invoke(null, textSize, builderConfig) as io.noties.markwon.MarkwonPlugin

        return Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(latexPlugin)
            .build()
    }

    /**
     * Preprocess text to normalize LaTeX delimiters.
     * Only converts non-standard formats to standard $...$ and $$...$$ format.
     */
    private fun preprocessLatex(text: String): String {
        var processed = text

        // Convert \[ ... \] to $$ ... $$ (display math)
        // Be careful not to affect already existing $$
        processed = Regex("""(?<!\$)\\\[([\s\S]*?)\\](?!\$)""").replace(processed) {
            "\$\$${it.groupValues[1]}\$\$"
        }

        // Convert \( ... \) to $ ... $ (inline math)
        processed = Regex("""(?<!\$)\\\(([\s\S]*?)\\\)(?!\$)""").replace(processed) {
            "\$${it.groupValues[1]}\$"
        }

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
            val latexMarkwon = getLatexInstance(context)
            if (latexMarkwon != null) {
                val processed = preprocessLatex(text)
                latexMarkwon.setMarkdown(textView, processed)
                return
            }
        } catch (e: Throwable) {
            Log.e(TAG, "LaTeX rendering failed: ${e.message}", e)
            latexAvailable = false
            markwonWithLatex = null
        }

        // Fallback to basic Markdown
        try {
            val basicMarkwon = getBasicInstance(context)
            basicMarkwon.setMarkdown(textView, text)
        } catch (e: Throwable) {
            Log.e(TAG, "Basic Markdown rendering failed: ${e.message}", e)
            textView.text = text
        }
    }
}
