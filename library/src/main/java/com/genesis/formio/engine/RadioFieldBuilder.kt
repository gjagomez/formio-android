package com.genesis.formio.engine

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.card.MaterialCardView

class RadioFieldBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = component.key
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        container.addView(TextView(context).apply {
            text = if (component.validate?.required == true) {
                SpannableString("${component.label} *").apply {
                    setSpan(
                        ForegroundColorSpan(context.getColor(R.color.error_color)),
                        length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else {
                SpannableString(component.label)
            }
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (10 * dp).toInt())
        })

        val options = component.values ?: component.data?.values ?: emptyList()
        var currentValue = formData[component.key]?.toString()
            ?: component.defaultValue?.toString() ?: ""

        val cards = mutableListOf<MaterialCardView>()
        val gap = (6 * dp).toInt()

        options.chunked(2).forEach { rowOptions ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = gap }
            }

            rowOptions.forEachIndexed { col, option ->
                val card = buildOptionCard(option.label, option.value == currentValue, dp)
                card.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply {
                        if (col == 0) marginEnd = gap / 2
                        else marginStart = gap / 2
                    }

                card.setOnClickListener {
                    currentValue = option.value
                    formData[component.key] = option.value
                    onChange(component.key, option.value)
                    cards.forEachIndexed { i, c ->
                        applyCardState(c, options.getOrNull(i)?.value == currentValue)
                    }
                }

                cards.add(card)
                row.addView(card)
            }

            // Si la fila tiene número impar de opciones, agregar espacio vacío
            if (rowOptions.size == 1) {
                row.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                        .apply { marginStart = gap / 2 }
                })
            }

            container.addView(row)
        }

        // Error label (hidden until validation fails)
        container.addView(TextView(context).apply {
            tag = "__err__${component.key}"
            setTextColor(context.getColor(R.color.error_color))
            textSize = 12f
            setPadding(0, (4 * dp).toInt(), 0, 0)
            visibility = View.GONE
        })

        return container
    }

    private fun buildOptionCard(label: String, selected: Boolean, dp: Float): MaterialCardView {
        val pad = (14 * dp).toInt()
        return MaterialCardView(context).apply {
            radius = (12 * dp)
            cardElevation = 0f
            strokeWidth = (1.5f * dp).toInt()
            isClickable = true
            isFocusable = true

            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(pad, pad, pad, pad)
            }

            val iconView = ImageView(context).apply {
                val size = (22 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
            }
            inner.addView(iconView)

            val textView = TextView(context).apply {
                text = label
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = (12 * dp).toInt() }
            }
            inner.addView(textView)
            addView(inner)
            applyCardState(this, selected)
        }
    }

    private fun applyCardState(card: MaterialCardView, selected: Boolean) {
        val inner = card.getChildAt(0) as? LinearLayout ?: return
        val iconView = inner.getChildAt(0) as? ImageView ?: return
        val textView = inner.getChildAt(1) as? TextView ?: return

        if (selected) {
            card.setCardBackgroundColor(context.getColor(R.color.radio_selected_bg))
            card.strokeColor = context.getColor(R.color.accent_green)
            iconView.setImageResource(R.drawable.ic_radio_checked)
            textView.setTextColor(context.getColor(R.color.accent_green))
            textView.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            card.setCardBackgroundColor(context.getColor(R.color.bg_elevated))
            card.strokeColor = context.getColor(R.color.input_border)
            iconView.setImageResource(R.drawable.ic_radio_unchecked)
            textView.setTextColor(context.getColor(R.color.text_secondary))
            textView.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }
}
