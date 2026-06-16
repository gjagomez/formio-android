package com.genesis.formio.engine

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class TextFieldBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density

        if (component.disabled) return buildDisabledField(component, formData, dp)

        val engine = ValidationEngine(context)
        val isRequired = component.validate?.required == true
        val validateOnBlur = component.validateOn == "blur"

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        container.addView(FieldLabelBuilder.build(context, component.label, isRequired))

        val r = 12 * dp

        val til = TextInputLayout(
            context, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            tag = component.key
            hint = component.placeholder?.takeIf { it.isNotBlank() } ?: ""
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

        val resolvedInputType = when {
            component.type == "email" ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            component.type == "phoneNumber" ->
                InputType.TYPE_CLASS_PHONE
            component.type == "url" ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            component.inputType == "number" ->
                InputType.TYPE_CLASS_NUMBER
            else ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        // Resolve initial value — never display "null" or "undefined"
        val initialValue = safeStr(formData[component.key])
            ?: safeStr(component.defaultValue)
            ?: ""

        val editText = TextInputEditText(til.context).apply {
            inputType = resolvedInputType
            setText(initialValue)
            setTextColor(context.getColor(R.color.text_primary))
            setHintTextColor(context.getColor(R.color.text_hint))
            textSize = 15f
            isEnabled = !component.disabled

            // Placeholder text shown inside the field (below the floating label)
            component.placeholder?.takeIf { it.isNotBlank() }?.let { hint = it }
        }

        val filters = mutableListOf<InputFilter>()
        if (component.case == "uppercase") filters.add(InputFilter.AllCaps())
        component.validate?.maxLength?.takeIf { it > 0 }?.let {
            filters.add(InputFilter.LengthFilter(it))
        }
        if (filters.isNotEmpty()) editText.filters = filters.toTypedArray()

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString() ?: ""
                formData[component.key] = value
                onChange(component.key, value)

                if (!validateOnBlur) {
                    // Validate on every change
                    til.error = engine.validateComponent(component, value)
                } else {
                    // On blur mode: only clear error if the field is now valid
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

        // Seed formData with the initial value so other fields / calculations can depend on it
        if (initialValue.isNotBlank()) formData[component.key] = initialValue

        til.addView(editText)
        container.addView(til)
        return container
    }

    // ── Campo desactivado: layout de solo lectura, sin chrome de input ─────────

    private fun buildDisabledField(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        dp: Float
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        // Label apagado
        container.addView(FieldLabelBuilder.build(context, component.label, required = false, disabled = true))

        // Caja de solo lectura
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.bg_card))
                setStroke((1 * dp).toInt(), context.getColor(R.color.stroke))
                cornerRadius = 12 * dp
            }
            setPadding(
                (14 * dp).toInt(), (14 * dp).toInt(),
                (14 * dp).toInt(), (14 * dp).toInt()
            )
        }

        // Ícono candado
        box.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_lock)
            setColorFilter(context.getColor(R.color.text_hint))
            alpha = 0.7f
            layoutParams = LinearLayout.LayoutParams(
                (16 * dp).toInt(), (16 * dp).toInt()
            ).apply { marginEnd = (10 * dp).toInt() }
        })

        // Valor o placeholder apagado
        val value = safeStr(formData[component.key]) ?: safeStr(component.defaultValue) ?: ""
        box.addView(TextView(context).apply {
            text = value.ifBlank { component.placeholder ?: "" }
            textSize = 15f
            setTextColor(context.getColor(
                if (value.isNotBlank()) R.color.text_secondary else R.color.text_hint
            ))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        })

        container.addView(box)
        return container
    }

    companion object {
        /** Returns null for blank, "null", or "undefined" raw values. */
        fun safeStr(raw: Any?): String? {
            if (raw == null) return null
            val s = raw.toString()
            return if (s.isBlank() || s == "null" || s == "undefined") null else s
        }
    }
}
