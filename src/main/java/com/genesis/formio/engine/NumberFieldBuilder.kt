package com.genesis.formio.engine

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.LinearLayout
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class NumberFieldBuilder(private val context: Context) {

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

        val isRequired = component.validate?.required == true
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
            val currencySymbol = when {
                component.type == "currency" && component.currency != null -> {
                    try { java.util.Currency.getInstance(component.currency).symbol + " " }
                    catch (_: Exception) { component.currency + " " }
                }
                component.delimiter -> "Q "
                else -> null
            }
            currencySymbol?.let {
                prefixText = it
                setPrefixTextColor(context.getColorStateList(R.color.accent_green_selector))
            }
            isEnabled = !component.disabled
            isErrorEnabled = true
        }

        val initialValue = TextFieldBuilder.safeStr(formData[component.key])
            ?: TextFieldBuilder.safeStr(component.defaultValue)
            ?: ""

        val editText = TextInputEditText(til.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(initialValue)
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 15f
        }

        val engine = ValidationEngine(context)
        val validateOnBlur = component.validateOn == "blur"

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val raw = (s?.toString() ?: "").replace(",", "")
                formData[component.key] = raw.toDoubleOrNull() ?: raw
                onChange(component.key, formData[component.key])
                if (!validateOnBlur) til.error = engine.validateComponent(component, raw)
                else if (til.error != null && engine.validateComponent(component, raw) == null) til.error = null
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editText.setOnFocusChangeListener { _, hasFocus ->
            til.boxStrokeColor = context.getColor(
                if (hasFocus) R.color.accent_green else R.color.input_border
            )
            if (hasFocus) {
                // Al entrar: quitar formato para que el usuario edite el número limpio
                if (component.delimiter) {
                    val raw = editText.text?.toString()?.replace(",", "") ?: ""
                    editText.setText(raw)
                    editText.setSelection(raw.length)
                }
            } else {
                val rawValue = editText.text?.toString()?.replace(",", "") ?: ""
                til.error = engine.validateComponent(component, rawValue)
                // Al salir: aplicar separador de miles si corresponde
                if (component.delimiter) {
                    rawValue.toDoubleOrNull()?.let { num ->
                        val symbols = DecimalFormatSymbols(Locale.US)
                        val pattern = if (component.requireDecimal) "#,##0.00" else "#,##0.##"
                        val formatted = DecimalFormat(pattern, symbols).format(num)
                        editText.setText(formatted)
                    }
                }
            }
        }

        if (initialValue.isNotBlank()) {
            val clean = initialValue.replace(",", "")
            formData[component.key] = clean.toDoubleOrNull() ?: clean
        }

        til.addView(editText)
        container.addView(til)
        return container
    }
}
