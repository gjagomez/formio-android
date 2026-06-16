package com.genesis.formio.engine

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.genesis.formio.model.FormComponent

class ColumnsBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = (component.columns?.sumOf { it.width } ?: 12).toFloat()
        }

        val fieldFactory = FieldFactory(context)
        component.columns?.forEach { col ->
            val colContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    col.width.toFloat()
                )
            }
            col.components.forEach { child ->
                if (child.type != "hidden") {
                    fieldFactory.createView(child, formData, onChange)?.let { colContainer.addView(it) }
                }
            }
            row.addView(colContainer)
        }
        return row
    }
}
