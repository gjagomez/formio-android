package com.genesis.formio.engine

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.*

class DateTimeFieldBuilder(private val context: Context) {

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

        val r = 12 * dp

        val til = TextInputLayout(
            context, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            tag = component.key
            hint = if (component.validate?.required == true) "${component.label} *" else component.label
            setBoxBackgroundColorResource(R.color.bg_elevated)
            setBoxCornerRadii(r, r, r, r)
            boxStrokeWidth = (1.5f * dp).toInt()
            boxStrokeWidthFocused = (2f * dp).toInt()
            boxStrokeColor = context.getColor(R.color.input_border)
            hintTextColor = context.getColorStateList(R.color.text_hint_selector)
            endIconMode = TextInputLayout.END_ICON_CUSTOM
            setEndIconDrawable(R.drawable.ic_calendar)
            setEndIconTintList(context.getColorStateList(R.color.accent_green_selector))
            isEnabled = !component.disabled
        }

        val displayFormat = component.format ?: "dd/MM/yyyy"
        // MaterialDatePicker returns UTC midnight — use UTC timezone to avoid off-by-one day
        val sdf = SimpleDateFormat(displayFormat, Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        val editText = TextInputEditText(til.context).apply {
            isFocusable = false
            isClickable = true
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 15f
            val currentValue = formData[component.key]?.toString()
            if (!currentValue.isNullOrBlank()) setText(currentValue)
        }

        val clickListener = View.OnClickListener {
            til.boxStrokeColor = context.getColor(R.color.accent_green)
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(component.label)
                .setTheme(com.google.android.material.R.style.ThemeOverlay_Material3_MaterialCalendar)
                .build()
            val fm = (context as? androidx.fragment.app.FragmentActivity)
                ?.supportFragmentManager ?: return@OnClickListener
            picker.show(fm, "DATE_${component.key}")
            picker.addOnPositiveButtonClickListener { millis ->
                val formatted = sdf.format(Date(millis))
                editText.setText(formatted)
                formData[component.key] = formatted
                onChange(component.key, formatted)
                til.boxStrokeColor = context.getColor(R.color.input_border)
            }
            picker.addOnNegativeButtonClickListener {
                til.boxStrokeColor = context.getColor(R.color.input_border)
            }
            picker.addOnCancelListener {
                til.boxStrokeColor = context.getColor(R.color.input_border)
            }
        }

        editText.setOnClickListener(clickListener)
        til.setEndIconOnClickListener(clickListener)
        til.addView(editText)
        container.addView(til)
        return container
    }
}
