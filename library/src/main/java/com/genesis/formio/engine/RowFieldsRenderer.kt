package com.genesis.formio.engine

import android.view.View
import android.widget.LinearLayout
import com.genesis.formio.model.FormComponent

/**
 * Renders the child fields of a row container (datagrid / editgrid) honoring
 * `refreshOn` / `clearOnRefresh` between sibling fields.
 *
 * Inside a grid row, Form.io expresses the trigger as `"gridKey.fieldKey"`
 * (e.g. `refreshOn: "cars.make"`); only the last segment matters here because
 * siblings share the row map. When the trigger field changes, each dependent
 * sibling is rebuilt so its data source re-resolves `{{ row.x }}` placeholders
 * against the new row values.
 */
object RowFieldsRenderer {

    fun render(
        fieldFactory: FieldFactory,
        children: List<FormComponent>,
        rowMap: MutableMap<String, Any?>,
        fieldsLayout: LinearLayout,
        onRowChange: (key: String, value: Any?) -> Unit
    ) {
        // trigger field key → sibling keys to rebuild when it changes
        val refreshDeps = mutableMapOf<String, MutableList<String>>()
        children.forEach { child ->
            child.refreshOn?.takeIf { it.isNotBlank() }?.let { trigger ->
                refreshDeps.getOrPut(trigger.substringAfterLast('.')) { mutableListOf() }
                    .add(child.key)
            }
        }

        val childViews = mutableMapOf<String, View>()

        fun buildChild(child: FormComponent): View? =
            fieldFactory.createView(child, rowMap) { key, value ->
                rowMap[key] = value
                onRowChange(key, value)
                refreshDeps[key]?.forEach { depKey ->
                    val depChild = children.find { it.key == depKey } ?: return@forEach
                    if (depChild.clearOnRefresh && rowMap.containsKey(depKey)) {
                        rowMap.remove(depKey)
                        onRowChange(depKey, null)
                    }
                    val oldView = childViews[depKey] ?: return@forEach
                    val index = fieldsLayout.indexOfChild(oldView)
                    if (index < 0) return@forEach
                    fieldsLayout.removeView(oldView)
                    buildChild(depChild)?.let { fresh ->
                        childViews[depKey] = fresh
                        fieldsLayout.addView(fresh, index)
                    }
                }
            }

        children.forEach { child ->
            if (child.type != "hidden" && child.type != "button") {
                buildChild(child)?.let { view ->
                    childViews[child.key] = view
                    fieldsLayout.addView(view)
                }
            }
        }
    }
}
