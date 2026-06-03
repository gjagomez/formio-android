package com.genesis.formio.engine

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.genesis.formio.util.GeolocationHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LocationFieldBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, context.resources.getDimensionPixelSize(R.dimen.spacing_sm))
        }

        val labelText = if (component.validate?.required == true) "${component.label} *" else component.label
        val label = TextView(context).apply {
            text = labelText
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, context.resources.getDimensionPixelSize(R.dimen.spacing_xs))
        }
        container.addView(label)

        val tilLat = buildReadonlyTil("Latitud")
        val etLat = TextInputEditText(tilLat.context).apply {
            isFocusable = false
            setTextColor(context.getColor(R.color.text_primary))
        }
        tilLat.addView(etLat)

        val tilLng = buildReadonlyTil("Longitud")
        val etLng = TextInputEditText(tilLng.context).apply {
            isFocusable = false
            setTextColor(context.getColor(R.color.text_primary))
        }
        tilLng.addView(etLng)

        // Pre-fill from saved data
        val saved = formData[component.key]?.toString() ?: ""
        if (saved.contains(",")) {
            val parts = saved.split(",")
            etLat.setText(parts.getOrNull(0)?.trim() ?: "")
            etLng.setText(parts.getOrNull(1)?.trim() ?: "")
        }

        val btnGet = MaterialButton(
            context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = context.getString(R.string.formio_btn_obtener_coords)
            setTextColor(context.getColor(R.color.accent_green))
            strokeColor = context.getColorStateList(R.color.accent_green_selector)
        }

        btnGet.setOnClickListener {
            btnGet.isEnabled = false
            btnGet.text = "Obteniendo…"
            GeolocationHelper(context).getCurrentLocation(
                onResult = { lat, lng ->
                    val value = "%.6f,%.6f".format(lat, lng)
                    etLat.setText("%.6f".format(lat))
                    etLng.setText("%.6f".format(lng))
                    formData[component.key] = value
                    onChange(component.key, value)
                    btnGet.isEnabled = true
                    btnGet.text = context.getString(R.string.formio_btn_obtener_coords)
                },
                onError = { msg ->
                    btnGet.isEnabled = true
                    btnGet.text = context.getString(R.string.formio_btn_obtener_coords)
                    label.text = "⚠ $msg"
                    label.setTextColor(context.getColor(R.color.error_color))
                }
            )
        }

        val coordRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val half = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val halfEnd = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = context.resources.getDimensionPixelSize(R.dimen.spacing_sm)
        }
        tilLat.layoutParams = half
        tilLng.layoutParams = halfEnd
        coordRow.addView(tilLat)
        coordRow.addView(tilLng)

        container.addView(coordRow)
        container.addView(btnGet)
        return container
    }

    private fun buildReadonlyTil(hint: String) = TextInputLayout(
        context, null,
        com.google.android.material.R.attr.textInputOutlinedStyle
    ).apply {
        this.hint = hint
        setBoxBackgroundColorResource(R.color.bg_elevated)
        setBoxCornerRadii(8f, 8f, 8f, 8f)
        isEnabled = false
    }
}
