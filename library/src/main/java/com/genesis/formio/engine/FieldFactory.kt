package com.genesis.formio.engine

import android.content.Context
import android.view.View
import com.genesis.formio.model.FormComponent

class FieldFactory(
    private val context: Context,
    private val onButtonClick: ((key: String, action: String?, custom: String?) -> Unit)? = null
) {

    fun createView(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (key: String, value: Any?) -> Unit
    ): View? {
        return when (component.type) {
            "textfield", "email", "phoneNumber", "url" ->
                TextFieldBuilder(context).build(component, formData, onChange)
            "checkbox" ->
                CheckboxBuilder(context).build(component, formData, onChange)
            "day" ->
                DayFieldBuilder(context).build(component, formData, onChange)
            "survey" ->
                SurveyBuilder(context).build(component, formData, onChange)
            "datamap" ->
                DataMapBuilder(context).build(component, formData, onChange)
            "address" ->
                AddressFieldBuilder(context).build(component, formData, onChange)
            "number", "currency" ->
                NumberFieldBuilder(context).build(component, formData, onChange)
            "tags" ->
                TagsFieldBuilder(context).build(component, formData, onChange)
            "textarea" ->
                TextAreaBuilder(context).build(component, formData, onChange)
            "select" ->
                SelectFieldBuilder(context).build(component, formData, onChange)
            "radio" ->
                RadioFieldBuilder(context).build(component, formData, onChange)
            "selectboxes" ->
                SelectBoxesBuilder(context).build(component, formData, onChange)
            "datetime" ->
                DateTimeFieldBuilder(context).build(component, formData, onChange)
            "file" ->
                FileFieldBuilder(context).build(component, formData, onChange)
            "signature" ->
                SignatureFieldBuilder(context).build(component, formData, onChange)
            "map", "location" ->
                LocationFieldBuilder(context).build(component, formData, onChange)
            "htmlelement" ->
                HtmlElementBuilder(context).build(component)
            "panel" ->
                PanelBuilder(context, onButtonClick).build(component, formData, onChange)
            "tabs" ->
                TabsFieldBuilder(context, onButtonClick).build(component, formData, onChange)
            "datagrid" ->
                DatagridBuilder(context).build(component, formData, onChange)
            "editgrid" ->
                EditGridBuilder(context).build(component, formData, onChange)
            "columns" ->
                ColumnsBuilder(context).build(component, formData, onChange)
            "button" ->
                ButtonBuilder(context).build(component, onButtonClick)
            "table" ->
                TableBuilder(context, onButtonClick).build(component, formData, onChange)
            "hidden" -> null
            else -> {
                // Typeless containers: render their children in a plain column
                if (component.type.isBlank() && !component.components.isNullOrEmpty()) {
                    val column = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                    }
                    component.components.forEach { child ->
                        if (child.type != "hidden") {
                            createView(child, formData, onChange)?.let { column.addView(it) }
                        }
                    }
                    column
                } else null
            }
        }
    }
}
