package com.genesis.formio.engine

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class TextAreaBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density
        val engine = ValidationEngine(context)
        val isRequired = component.validate?.required == true
        val validateOnBlur = component.validateOn == "blur"

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        val r = 12 * dp

        val hintText: CharSequence = if (isRequired) {
            SpannableString("${component.label} *").also {
                it.setSpan(
                    ForegroundColorSpan(context.getColor(R.color.error_color)),
                    it.length - 1, it.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else {
            component.label
        }

        val til = TextInputLayout(
            context, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            tag = component.key
            hint = hintText
            setBoxBackgroundColorResource(R.color.bg_elevated)
            setBoxCornerRadii(r, r, r, r)
            boxStrokeWidth = (1.5f * dp).toInt()
            boxStrokeWidthFocused = (2f * dp).toInt()
            boxStrokeColor = context.getColor(R.color.input_border)
            hintTextColor = context.getColorStateList(R.color.text_hint_selector)
            isEnabled = !component.disabled
            isErrorEnabled = true

            if (component.showCharCount || component.showWordCount) {
                isCounterEnabled = true
                component.validate?.maxLength?.let { counterMaxLength = it }
            }
        }

        val minLines = component.rows?.takeIf { it > 0 } ?: 3

        val initialValue = TextFieldBuilder.safeStr(formData[component.key])
            ?: TextFieldBuilder.safeStr(component.defaultValue)
            ?: ""

        val editText = TextInputEditText(til.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            this.minLines = minLines
            maxLines = if (component.autoExpand) Int.MAX_VALUE else 10
            gravity = Gravity.TOP or Gravity.START
            setText(initialValue)
            setTextColor(context.getColor(R.color.text_primary))
            isEnabled = !component.disabled

            component.placeholder?.takeIf { it.isNotBlank() }?.let { hint = it }
        }

        val filters = mutableListOf<InputFilter>()
        if (component.case == "uppercase") filters.add(InputFilter.AllCaps())
        component.validate?.maxLength?.takeIf { it > 0 }?.let { filters.add(InputFilter.LengthFilter(it)) }
        if (filters.isNotEmpty()) editText.filters = filters.toTypedArray()

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString() ?: ""
                formData[component.key] = value
                onChange(component.key, value)

                if (!validateOnBlur) {
                    til.error = engine.validateComponent(component, value)
                } else {
                    if (til.error != null && engine.validateComponent(component, value) == null) {
                        til.error = null
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editText.setOnFocusChangeListener { _, hasFocus ->
            til.boxStrokeColor = context.getColor(
                if (hasFocus) R.color.accent_green else R.color.input_border
            )
            if (!hasFocus) {
                til.error = engine.validateComponent(component, editText.text?.toString() ?: "")
            }
        }

        if (initialValue.isNotBlank()) formData[component.key] = initialValue

        til.addView(editText)
        container.addView(til)
        return container
    }
}
