package com.genesis.formio.engine

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.genesis.formio.model.FormComponent

/**
 * Form.io `table` layout component, used when a table appears inside a
 * grid row (top-level tables are flattened by FormEngine itself so their
 * children react to data changes).
 */
class TableBuilder(
    private val context: Context,
    private val onButtonClick: ((key: String, action: String?, custom: String?) -> Unit)? = null
) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density
        val fieldFactory = FieldFactory(context, onButtonClick)
        val table = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        component.tableRows?.forEach { cells ->
            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = cells.size.toFloat()
            }
            cells.forEach { cell ->
                val cellView = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).apply { marginEnd = (4 * dp).toInt() }
                }
                cell.components.forEach { child ->
                    if (child.type != "hidden") {
                        fieldFactory.createView(child, formData, onChange)?.let { cellView.addView(it) }
                    }
                }
                rowView.addView(cellView)
            }
            table.addView(rowView)
        }
        return table
    }
}
