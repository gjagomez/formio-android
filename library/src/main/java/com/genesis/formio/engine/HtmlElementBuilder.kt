package com.genesis.formio.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import org.json.JSONArray

class HtmlElementBuilder(private val context: Context) {

    fun build(component: FormComponent): View {
        val dp = context.resources.displayMetrics.density

        if (component.tag?.lowercase() == "img") {
            val src = parseAttr(component.attrsJson, "src")
            if (!src.isNullOrBlank()) return buildImageView(src, dp)
        }

        val content = component.content?.takeIf { it.isNotBlank() }
            ?: return LinearLayout(context)

        // Rich HTML with inline CSS — render in a WebView so styles are respected
        if (content.contains("style=", ignoreCase = true) ||
            content.contains("<div", ignoreCase = true) ||
            content.contains("<table", ignoreCase = true)) {
            return buildWebView(content, dp)
        }

        val tag = component.tag?.lowercase()?.takeIf { it.isNotBlank() } ?: "p"
        return when (tag) {
            "h1", "h2", "h3" -> buildMainHeading(content, tag, dp)
            "h4", "h5", "h6" -> buildSectionHeading(content, tag, dp)
            else              -> buildParagraph(content, dp)
        }
    }

    // ── Rich HTML rendered via WebView ─────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(content: String, dp: Float): View {
        val wv = WebView(context)
        wv.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        wv.isScrollContainer = false
        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        wv.setBackgroundColor(Color.TRANSPARENT)
        // Start with a generous height; updated after the page finishes loading
        wv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (300 * dp).toInt()
        )

        val html = """<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  * { box-sizing: border-box; -webkit-text-size-adjust: 100%; }
  body { margin: 0; padding: 0; background: transparent; }
  img { max-width: 100%; height: auto; }
</style>
</head><body>$content</body></html>"""

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view ?: return
                // Measure true content height via JS and resize the WebView to fit
                view.evaluateJavascript(
                    "(function(){" +
                        "var b=document.body,h=document.documentElement;" +
                        "return Math.max(b.scrollHeight,b.offsetHeight,h.scrollHeight,h.offsetHeight);" +
                    "})()"
                ) { result ->
                    val cssHeight = result?.trim()?.replace("\"", "")?.toFloatOrNull()
                        ?: return@evaluateJavascript
                    val px = (cssHeight * dp).toInt() + (24 * dp).toInt()
                    view.post {
                        val lp = view.layoutParams as? LinearLayout.LayoutParams ?: return@post
                        lp.height = px
                        view.layoutParams = lp
                    }
                }
            }
        }

        wv.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
        return wv
    }

    // ── Heading grande (h1-h3): texto + línea verde inferior ──────────────────

    private fun buildMainHeading(content: String, tag: String, dp: Float): View {
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
        }
        wrapper.addView(TextView(context).apply {
            text = fromHtml(content)
            textSize = when (tag) { "h1" -> 22f; "h2" -> 20f; else -> 18f }
            setTextColor(context.getColor(R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
        })
        wrapper.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (3 * dp).toInt()).apply {
                topMargin = (6 * dp).toInt()
            }
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.accent_green))
                cornerRadius = 2 * dp
            }
        })
        return wrapper
    }

    // ── Heading de sección (h4-h6): barra verde izquierda + texto bold ────────

    private fun buildSectionHeading(content: String, tag: String, dp: Float): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
        }
        row.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (3 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = (10 * dp).toInt() }
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.accent_green))
                cornerRadius = 2 * dp
            }
        })
        row.addView(TextView(context).apply {
            text = fromHtml(content)
            textSize = when (tag) { "h4" -> 15f; "h5" -> 14f; else -> 13f }
            setTextColor(context.getColor(R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        return row
    }

    // ── Párrafo / span: texto secundario sin tarjeta ──────────────────────────

    private fun buildParagraph(content: String, dp: Float): View {
        return TextView(context).apply {
            text = fromHtml(content)
            textSize = 13f
            setTextColor(context.getColor(R.color.text_secondary))
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
    }

    // ── Imagen ─────────────────────────────────────────────────────────────────

    private fun buildImageView(src: String, dp: Float): View {
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        val imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(context.getColor(R.color.bg_elevated))
        }
        wrapper.addView(imageView)
        Thread {
            try {
                val bitmap = java.net.URL(src).openStream().use { BitmapFactory.decodeStream(it) }
                Handler(Looper.getMainLooper()).post {
                    imageView.setImageBitmap(bitmap)
                    imageView.background = null
                }
            } catch (_: Exception) {}
        }.start()
        return wrapper
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun parseAttr(attrsJson: String?, attrName: String): String? {
        if (attrsJson.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(attrsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optString("attr").equals(attrName, ignoreCase = true)) {
                    return obj.optString("value").takeIf { it.isNotBlank() }
                }
            }
            null
        } catch (_: Exception) { null }
    }

    @Suppress("DEPRECATION")
    private fun fromHtml(html: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        else Html.fromHtml(html)
}
