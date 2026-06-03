package com.genesis.formio.engine

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.card.MaterialCardView
import org.json.JSONObject

class SelectBoxesBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        val labelText = if (component.validate?.required == true) "${component.label} *" else component.label
        container.addView(TextView(context).apply {
            text = labelText
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (10 * dp).toInt())
        })

        val options = component.values ?: component.data?.values ?: emptyList()
        val currentData = try {
            JSONObject(formData[component.key]?.toString() ?: "{}")
        } catch (_: Exception) { JSONObject() }

        options.forEach { option ->
            val isChecked = currentData.optBoolean(option.value, false)
            val card = buildCheckCard(option.label, isChecked, dp)
            card.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * dp).toInt() }

            card.setOnClickListener {
                val newChecked = !currentData.optBoolean(option.value, false)
                currentData.put(option.value, newChecked)
                val value = currentData.toString()
                formData[component.key] = value
                onChange(component.key, value)
                val inner = card.getChildAt(0) as? LinearLayout ?: return@setOnClickListener
                val iconView = inner.getChildAt(0) as? ImageView ?: return@setOnClickListener
                val textView = inner.getChildAt(1) as? TextView ?: return@setOnClickListener
                applyCheckState(card, iconView, textView, newChecked)
            }

            container.addView(card)
        }

        return container
    }

    private fun buildCheckCard(label: String, checked: Boolean, dp: Float): MaterialCardView {
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
            applyCheckState(this, iconView, textView, checked)
        }
    }

    private fun applyCheckState(
        card: MaterialCardView,
        iconView: ImageView,
        textView: TextView,
        checked: Boolean
    ) {
        if (checked) {
            card.setCardBackgroundColor(context.getColor(R.color.radio_selected_bg))
            card.strokeColor = context.getColor(R.color.accent_green)
            iconView.setImageResource(R.drawable.ic_checkbox_checked)
            textView.setTextColor(context.getColor(R.color.accent_green))
            textView.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            card.setCardBackgroundColor(context.getColor(R.color.bg_elevated))
            card.strokeColor = context.getColor(R.color.input_border)
            iconView.setImageResource(R.drawable.ic_checkbox_unchecked)
            textView.setTextColor(context.getColor(R.color.text_secondary))
            textView.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }
}
