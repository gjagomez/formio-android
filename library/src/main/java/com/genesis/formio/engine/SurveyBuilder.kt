package com.genesis.formio.engine

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.card.MaterialCardView
import org.json.JSONObject

/**
 * Form.io `survey` component: a list of questions, each answered with one of
 * the shared values. Stores an object keyed by question value:
 * `{"nombre": "u", "ape": "wew"}`.
 */
class SurveyBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density
        val isRequired = component.validate?.required == true
        val questions = component.questions ?: emptyList()
        val values = component.values ?: emptyList()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        container.addView(FieldLabelBuilder.build(context, component.label, isRequired))

        // Restore answers from Map or JSON string
        val answers = linkedMapOf<String, String>()
        when (val raw = formData[component.key]) {
            is Map<*, *> -> raw.forEach { (k, v) -> if (k != null && v != null) answers[k.toString()] = v.toString() }
            is String -> try {
                val o = JSONObject(raw)
                o.keys().forEach { k -> answers[k] = o.optString(k, "") }
            } catch (_: Exception) { /* not JSON */ }
        }

        fun sync() {
            val value = LinkedHashMap(answers)
            formData[component.key] = value
            onChange(component.key, value)
        }

        questions.forEach { question ->
            container.addView(TextView(context).apply {
                text = question.label
                setTextColor(context.getColor(R.color.text_secondary))
                textSize = 13f
                setPadding(0, (8 * dp).toInt(), 0, (6 * dp).toInt())
            })

            val chipRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val chips = mutableListOf<Pair<MaterialCardView, TextView>>()

            fun refreshChips() {
                val selected = answers[question.value]
                chips.forEachIndexed { i, (card, label) ->
                    val isSelected = values[i].value == selected
                    card.setCardBackgroundColor(
                        context.getColor(if (isSelected) R.color.radio_selected_bg else R.color.bg_elevated)
                    )
                    card.strokeColor = context.getColor(
                        if (isSelected) R.color.accent_green else R.color.input_border
                    )
                    label.setTextColor(
                        context.getColor(if (isSelected) R.color.accent_green else R.color.text_secondary)
                    )
                    label.setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                }
            }

            values.forEach { option ->
                val label = TextView(context).apply {
                    text = option.label
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
                }
                val card = MaterialCardView(context).apply {
                    radius = (18 * dp)
                    cardElevation = 0f
                    strokeWidth = (1.5f * dp).toInt()
                    isClickable = !component.disabled
                    isFocusable = !component.disabled
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = (8 * dp).toInt() }
                    addView(label)
                }
                if (!component.disabled) {
                    card.setOnClickListener {
                        answers[question.value] = option.value
                        sync()
                        refreshChips()
                        container.findViewWithTag<TextView>("__err__${component.key}")?.visibility = View.GONE
                    }
                }
                chips.add(card to label)
                chipRow.addView(card)
            }
            refreshChips()

            container.addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(chipRow)
            })
        }

        // Error label
        container.addView(TextView(context).apply {
            tag = "__err__${component.key}"
            setTextColor(context.getColor(R.color.error_color))
            textSize = 12f
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), 0, 0)
            visibility = View.GONE
        })

        return container
    }
}
