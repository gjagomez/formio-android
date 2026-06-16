package com.genesis.formio.engine

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.card.MaterialCardView

class PanelBuilder(
    private val context: Context,
    private val onButtonClick: ((key: String, action: String?, custom: String?) -> Unit)? = null
) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val card = MaterialCardView(context).apply {
            setCardBackgroundColor(context.getColor(R.color.bg_card))
            radius = context.resources.getDimension(R.dimen.corner_card)
            cardElevation = 0f
            strokeColor = context.getColor(R.color.stroke)
            strokeWidth = 1
        }

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                context.resources.getDimensionPixelSize(R.dimen.spacing_md),
                context.resources.getDimensionPixelSize(R.dimen.spacing_md),
                context.resources.getDimensionPixelSize(R.dimen.spacing_md),
                context.resources.getDimensionPixelSize(R.dimen.spacing_md)
            )
        }

        if (!component.title.isNullOrBlank()) {
            val title = TextView(context).apply {
                text = component.title
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(context.getColor(R.color.text_primary))
                setPadding(0, 0, 0, context.resources.getDimensionPixelSize(R.dimen.spacing_sm))
            }
            inner.addView(title)
        }

        val fieldFactory = FieldFactory(context, onButtonClick)
        component.components?.forEach { child ->
            if (child.type != "hidden") {
                fieldFactory.createView(child, formData, onChange)?.let { inner.addView(it) }
            }
        }

        card.addView(inner)

        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, context.resources.getDimensionPixelSize(R.dimen.spacing_sm))
        }
        wrapper.addView(card)
        return wrapper
    }
}
