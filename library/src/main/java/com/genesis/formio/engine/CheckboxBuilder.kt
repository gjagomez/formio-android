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

/** Single boolean checkbox (Form.io `checkbox`). Stores true/false. */
class CheckboxBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        var checked = when (val raw = formData[component.key] ?: component.defaultValue) {
            is Boolean -> raw
            else -> raw?.toString() == "true"
        }
        formData[component.key] = checked

        val pad = (14 * dp).toInt()
        val card = MaterialCardView(context).apply {
            tag = "__card__${component.key}"
            radius = (12 * dp)
            cardElevation = 0f
            strokeWidth = (1.5f * dp).toInt()
            isClickable = !component.disabled
            isFocusable = !component.disabled
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

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

        val isRequired = component.validate?.required == true
        val textView = TextView(context).apply {
            text = if (isRequired) "${component.label} *" else component.label
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = (12 * dp).toInt() }
        }
        inner.addView(textView)
        card.addView(inner)
        container.addView(card)

        // Error label
        container.addView(TextView(context).apply {
            tag = "__err__${component.key}"
            setTextColor(context.getColor(R.color.error_color))
            textSize = 12f
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), 0, 0)
            visibility = View.GONE
        })

        fun applyState() {
            if (checked) {
                card.setCardBackgroundColor(context.getColor(R.color.radio_selected_bg))
                card.strokeColor = context.getColor(R.color.accent_green)
                iconView.setImageResource(R.drawable.ic_checkbox_checked)
                textView.setTextColor(context.getColor(R.color.accent_green))
            } else {
                card.setCardBackgroundColor(context.getColor(R.color.bg_elevated))
                card.strokeColor = context.getColor(R.color.input_border)
                iconView.setImageResource(R.drawable.ic_checkbox_unchecked)
                textView.setTextColor(context.getColor(R.color.text_secondary))
            }
        }
        applyState()

        if (!component.disabled) {
            card.setOnClickListener {
                checked = !checked
                formData[component.key] = checked
                onChange(component.key, checked)
                applyState()
                container.findViewWithTag<TextView>("__err__${component.key}")?.visibility = View.GONE
            }
        }

        return container
    }
}
