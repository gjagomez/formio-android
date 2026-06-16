package com.genesis.formio.engine

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Form.io `day` component: separate day / month / year numeric inputs.
 * Stores the value as `MM/DD/YYYY` (or `DD/MM/YYYY` with `dayFirst`), using
 * `00` / `0000` for hidden parts — same convention as formio.js.
 */
class DayFieldBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density
        val isRequired = component.validate?.required == true
        val fields = component.dayFields

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        container.addView(FieldLabelBuilder.build(context, component.label, isRequired))

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Restore existing value: "MM/DD/YYYY" or "DD/MM/YYYY" with dayFirst
        val parts = (formData[component.key]?.toString() ?: "").split("/")
        var initialDay = ""
        var initialMonth = ""
        var initialYear = ""
        if (parts.size == 3) {
            initialYear = parts[2].takeIf { it != "0000" } ?: ""
            initialMonth = (if (component.dayFirst) parts[1] else parts[0]).takeIf { it != "00" } ?: ""
            initialDay = (if (component.dayFirst) parts[0] else parts[1]).takeIf { it != "00" } ?: ""
        }

        var dayValue = initialDay
        var monthValue = initialMonth
        var yearValue = initialYear

        fun sync() {
            val d = dayValue.padStart(2, '0').ifBlank { "00" }
            val m = monthValue.padStart(2, '0').ifBlank { "00" }
            val y = yearValue.padStart(4, '0').ifBlank { "0000" }
            val allBlank = dayValue.isBlank() && monthValue.isBlank() && yearValue.isBlank()
            val value = if (allBlank) "" else if (component.dayFirst) "$d/$m/$y" else "$m/$d/$y"
            formData[component.key] = value
            onChange(component.key, value)
        }

        fun buildPart(
            hintRes: Int,
            initial: String,
            maxLen: Int,
            weight: Float,
            onText: (String) -> Unit
        ): TextInputLayout {
            val r = 12 * dp
            val til = TextInputLayout(
                context, null,
                com.google.android.material.R.attr.textInputOutlinedStyle
            ).apply {
                hint = context.getString(hintRes)
                setBoxBackgroundColorResource(R.color.bg_elevated)
                setBoxCornerRadii(r, r, r, r)
                boxStrokeWidth = (1.5f * dp).toInt()
                boxStrokeColor = context.getColor(R.color.input_border)
                hintTextColor = context.getColorStateList(R.color.text_hint_selector)
                isEnabled = !component.disabled
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                    .apply { marginEnd = (8 * dp).toInt() }
            }
            val edit = TextInputEditText(til.context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(maxLen))
                setText(initial)
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 15f
                isEnabled = !component.disabled
            }
            edit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    onText(s?.toString() ?: "")
                    sync()
                }
            })
            til.addView(edit)
            return til
        }

        val dayPart = if (fields?.hideDay != true)
            buildPart(R.string.formio_day_dia, initialDay, 2, 1f) { dayValue = it } else null
        val monthPart = if (fields?.hideMonth != true)
            buildPart(R.string.formio_day_mes, initialMonth, 2, 1f) { monthValue = it } else null
        val yearPart = if (fields?.hideYear != true)
            buildPart(R.string.formio_day_anio, initialYear, 4, 1.4f) { yearValue = it } else null

        if (component.dayFirst) {
            dayPart?.let { row.addView(it) }
            monthPart?.let { row.addView(it) }
        } else {
            monthPart?.let { row.addView(it) }
            dayPart?.let { row.addView(it) }
        }
        yearPart?.let { row.addView(it) }
        container.addView(row)

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
