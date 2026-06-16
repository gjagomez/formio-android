package com.genesis.formio.engine

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import org.json.JSONObject

/**
 * Form.io `datamap` component: dynamic key/value pairs.
 * Stores an object: `{"clave1": "valor1", "clave2": "valor2"}`.
 * The value column is rendered as a plain text input (the most common
 * `valueComponent` configuration).
 */
class DataMapBuilder(private val context: Context) {

    private class RowState(var key: String, var value: String)

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density
        val isRequired = component.validate?.required == true

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        container.addView(FieldLabelBuilder.build(context, component.label, isRequired))

        val rowsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(rowsContainer)

        val rows = mutableListOf<RowState>()

        fun sync() {
            val out = LinkedHashMap<String, String>()
            rows.forEach { row -> if (row.key.isNotBlank()) out[row.key] = row.value }
            formData[component.key] = out
            onChange(component.key, out)
        }

        fun makeInput(hintRes: Int, initial: String, weight: Float, onText: (String) -> Unit): EditText =
            EditText(context).apply {
                hint = context.getString(hintRes)
                setText(initial)
                setHintTextColor(context.getColor(R.color.text_hint))
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 14f
                isEnabled = !component.disabled
                background = GradientDrawable().apply {
                    setColor(context.getColor(R.color.bg_elevated))
                    cornerRadius = 10 * dp
                    setStroke((1.5f * dp).toInt(), context.getColor(R.color.input_border))
                }
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                    .apply { marginEnd = (8 * dp).toInt() }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        onText(s?.toString() ?: "")
                        sync()
                    }
                })
            }

        fun addRow(initialKey: String = "", initialValue: String = "") {
            val state = RowState(initialKey, initialValue)
            rows.add(state)

            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * dp).toInt() }
            }

            rowView.addView(makeInput(R.string.formio_datamap_clave, initialKey, 1f) { state.key = it })
            rowView.addView(makeInput(R.string.formio_datamap_valor, initialValue, 1f) { state.value = it })

            if (!component.disabled) {
                rowView.addView(ImageButton(context).apply {
                    setImageResource(R.drawable.ic_delete)
                    setColorFilter(context.getColor(R.color.stat_new))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(android.graphics.Color.argb(40, 232, 62, 140))
                    }
                    val size = (36 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    val p = (8 * dp).toInt(); setPadding(p, p, p, p)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setOnClickListener {
                        rows.remove(state)
                        rowsContainer.removeView(rowView)
                        sync()
                    }
                })
            }

            rowsContainer.addView(rowView)
        }

        // Restore existing entries from Map or JSON string
        when (val raw = formData[component.key]) {
            is Map<*, *> -> raw.forEach { (k, v) -> addRow(k?.toString() ?: "", v?.toString() ?: "") }
            is String -> try {
                val o = JSONObject(raw)
                o.keys().forEach { k -> addRow(k, o.optString(k, "")) }
            } catch (_: Exception) { /* not JSON */ }
        }

        // Add button
        if (!component.disabled) {
            container.addView(TextView(context).apply {
                text = context.getString(R.string.formio_btn_agregar_fila)
                setTextColor(context.getColor(R.color.accent_green))
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = 12 * dp
                    setStroke((1.5f * dp).toInt(), context.getColor(R.color.accent_green))
                }
                setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener { addRow() }
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
