package com.genesis.formio.engine

import android.content.Context
import android.os.Build
import android.text.Html
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.card.MaterialCardView

class HtmlElementBuilder(private val context: Context) {

    fun build(component: FormComponent): View {
        val content = component.content ?: return LinearLayout(context)

        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, context.resources.getDimensionPixelSize(R.dimen.spacing_sm))
        }

        val isHeading = component.tag?.lowercase() in listOf("h4", "h5", "h6", "h3")

        if (isHeading) {
            val textSize = when (component.tag?.lowercase()) {
                "h3" -> 18f
                "h4" -> 16f
                "h5" -> 15f
                else -> 14f
            }
            val tv = TextView(context).apply {
                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT)
                else @Suppress("DEPRECATION") Html.fromHtml(content)
                this.textSize = textSize
                setTextColor(context.getColor(R.color.accent_green))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, context.resources.getDimensionPixelSize(R.dimen.spacing_xs), 0, 0)
            }
            wrapper.addView(tv)
        } else {
            val card = MaterialCardView(context).apply {
                setCardBackgroundColor(context.getColor(R.color.bg_elevated))
                radius = context.resources.getDimension(R.dimen.corner_card)
                cardElevation = 0f
                strokeColor = context.getColor(R.color.stroke)
                strokeWidth = 1
            }
            val tv = TextView(context).apply {
                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT)
                else @Suppress("DEPRECATION") Html.fromHtml(content)
                textSize = 13f
                setTextColor(context.getColor(R.color.text_secondary))
                val pad = context.resources.getDimensionPixelSize(R.dimen.spacing_md)
                setPadding(pad, pad, pad, pad)
            }
            card.addView(tv)
            wrapper.addView(card)
        }

        return wrapper
    }
}
