package com.genesis.formio.engine

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout

class FormEngine(
    private val context: Context,
    private val container: LinearLayout,
    val formData: MutableMap<String, Any?>,
    private val onDataChange: (key: String, value: Any?) -> Unit,
    private val onButtonClick: ((key: String, action: String?, custom: String?) -> Unit)? = null
) {
    private val fieldFactory = FieldFactory(context, onButtonClick)
    private val conditionalEngine = ConditionalEngine()
    private val validationEngine = ValidationEngine(context)

    private val viewMap = mutableMapOf<String, View>()
    private val componentMap = mutableMapOf<String, FormComponent>()
    private var currentComponents: List<FormComponent> = emptyList()
    private var lastErroredKeys = emptySet<String>()   // keys that had errors in the last validate()
    private val parentMap = mutableMapOf<String, LinearLayout>()
    // triggerKey → list of component keys that should re-render when trigger changes
    private val refreshDeps = mutableMapOf<String, MutableList<String>>()
    // calculateValue: expression per field key, and dependency map
    private val calculateValueMap = mutableMapOf<String, String>()
    private val calculateDeps = mutableMapOf<String, MutableList<String>>()
    // Guard against circular calculateValue chains
    private val activeEvalKeys = mutableSetOf<String>()

    fun render(components: List<FormComponent>) {
        currentComponents = components
        container.removeAllViews()
        viewMap.clear()
        componentMap.clear()
        parentMap.clear()
        refreshDeps.clear()
        calculateValueMap.clear()
        calculateDeps.clear()

        buildCalculateDeps(components)

        components.forEach { component ->
            if (component.type == "hidden") {
                formData[component.key] = TextFieldBuilder.safeStr(component.defaultValue)
            } else {
                renderComponent(component, container)
            }
        }
        evaluateAllConditionals()
        evaluateAllCalculateValues()
    }

    private fun buildCalculateDeps(components: List<FormComponent>) {
        components.forEach { comp ->
            comp.calculateValue?.takeIf { it.isNotBlank() }?.let { expr ->
                calculateValueMap[comp.key] = expr
                CalculateValueEngine.extractDependencies(expr).forEach { dep ->
                    calculateDeps.getOrPut(dep) { mutableListOf() }.add(comp.key)
                }
            }
            comp.components?.let { buildCalculateDeps(it) }
            comp.columns?.forEach { col -> buildCalculateDeps(col.components) }
        }
    }

    private fun evaluateAllCalculateValues() {
        calculateValueMap.forEach { (key, expr) ->
            val result = CalculateValueEngine.evaluate(expr, formData) ?: return@forEach
            if (formData[key]?.toString() != result) {
                formData[key] = result
                rerenderField(key)
            }
        }
    }

    private fun renderComponent(component: FormComponent, parent: LinearLayout) {
        componentMap[component.key] = component
        parentMap[component.key] = parent

        component.refreshOn?.let { trigger ->
            refreshDeps.getOrPut(trigger) { mutableListOf() }.add(component.key)
        }

        val view = try {
            fieldFactory.createView(component, formData) { key, value ->
                formData[key] = value
                onDataChange(key, value)
                evaluateConditionalsDependingOn(key)
            }
        } catch (e: Exception) {
            android.util.Log.e("FormEngine", "Error rendering '${component.key}' (${component.type}): ${e.message}", e)
            null
        } ?: return
        viewMap[component.key] = view
        parent.addView(view)
    }

    fun collectData(): Map<String, Any?> = formData.toMap()

    /**
     * Validates all visible fields recursively (panels, columns, etc.).
     * Marks invalid fields in red inline and returns user-facing error messages.
     */
    fun validate(): List<String> {
        return try {
            clearFieldErrors()
            val fieldErrors = validateRecursive(currentComponents, formData)
            val newErroredKeys = mutableSetOf<String>()
            fieldErrors.forEach { (key, _, msg) ->
                newErroredKeys.add(key)
                val til = container.findViewWithTag<TextInputLayout>(key)
                if (til != null) {
                    til.isErrorEnabled = true
                    til.error = msg
                } else {
                    container.findViewWithTag<TextView>("__err__$key")?.let {
                        it.text = msg
                        it.visibility = View.VISIBLE
                    }
                    container.findViewWithTag<MaterialCardView>("__card__$key")
                        ?.strokeColor = context.getColor(R.color.error_color)
                }
            }
            lastErroredKeys = newErroredKeys
            fieldErrors.map { err -> "${err.label.ifBlank { err.key }}: ${err.message}" }
        } catch (e: Exception) {
            android.util.Log.e("FormEngine", "validate crashed: ${e.message}", e)
            emptyList()
        }
    }

    /** Clears only the views that were marked as errors in the last validate() call. */
    fun clearFieldErrors() {
        lastErroredKeys.forEach { key ->
            container.findViewWithTag<TextInputLayout>(key)?.let {
                it.error = null
                it.isErrorEnabled = false
            }
            container.findViewWithTag<TextView>("__err__$key")?.let {
                it.visibility = View.GONE
                it.text = ""
            }
            container.findViewWithTag<MaterialCardView>("__card__$key")
                ?.strokeColor = context.getColor(R.color.input_border)
        }
        lastErroredKeys = emptySet()
    }

    private data class FieldError(val key: String, val label: String, val message: String)

    private fun validateRecursive(
        components: List<FormComponent>,
        data: Map<String, Any?>
    ): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        for (comp in components) {
            // shouldShow can throw if a customConditional expression is malformed — skip silently
            val visible = try { conditionalEngine.shouldShow(comp, data) } catch (_: Exception) { true }
            if (!visible) continue
            when (comp.type) {
                "panel", "well" ->
                    errors += validateRecursive(comp.components ?: emptyList(), data)
                "columns" ->
                    comp.columns?.forEach { col ->
                        errors += validateRecursive(col.components, data)
                    }
                "htmlelement", "button", "hidden", "datagrid" -> { /* no validation */ }
                else -> if (comp.input) {
                    val value = data[comp.key]?.toString()?.takeIf { it != "null" } ?: ""
                    validationEngine.validateComponent(comp, value)?.let {
                        errors += FieldError(comp.key, comp.label, it)
                    }
                }
            }
        }
        return errors
    }

    fun setFieldValue(key: String, value: Any?) {
        formData[key] = value
        evaluateConditionalsDependingOn(key)
    }

    /**
     * Called from outside the engine (e.g. map picker, RENAP lookup) to push a value
     * into a field that was already rendered.
     *
     * Top-level fields are re-rendered via [rerenderField] so the whole View is replaced
     * with the fresh value from formData.
     *
     * Nested fields (inside panels / columns / datagrid) were rendered by their parent
     * builder's own FieldFactory, so they are not in [componentMap]. We locate them by
     * the `tag = component.key` that every TextInputLayout sets and update the EditText
     * directly — which also fires the TextWatcher so onChange + conditionals run.
     */
    fun externalUpdate(key: String, value: Any?) {
        formData[key] = value

        if (componentMap.containsKey(key)) {
            rerenderField(key)
            evaluateConditionalsDependingOn(key)
        } else {
            // Field is nested inside a panel / columns — find it by tag
            val valueStr = value?.toString() ?: ""
            val til = container.findViewWithTag<TextInputLayout>(key)
            if (til != null) {
                til.editText?.setText(valueStr)
                // TextWatcher fires automatically → formData updated + onChange called
            } else {
                android.util.Log.w("FormEngine", "externalUpdate: no view found for key='$key'")
            }
            evaluateConditionalsDependingOn(key)
        }
    }

    private fun evaluateAllConditionals() {
        componentMap.values.forEach { comp ->
            val view = viewMap[comp.key] ?: return@forEach
            view.visibility = if (conditionalEngine.shouldShow(comp, formData)) View.VISIBLE else View.GONE
        }
    }

    private fun evaluateConditionalsDependingOn(changedKey: String) {
        if (changedKey in activeEvalKeys) return
        activeEvalKeys.add(changedKey)
        try {
            componentMap.values.forEach { comp ->
                if (changedKey in conditionalEngine.dependsOn(comp)) {
                    val view = viewMap[comp.key] ?: return@forEach
                    view.visibility = if (conditionalEngine.shouldShow(comp, formData)) View.VISIBLE else View.GONE
                }
            }
            // Re-render cascade selects whose data source depends on changedKey
            refreshDeps[changedKey]?.toList()?.forEach { dependentKey ->
                rerenderField(dependentKey)
            }
            // Re-evaluate calculateValue expressions that depend on changedKey
            calculateDeps[changedKey]?.toList()?.forEach { depKey ->
                val expr = calculateValueMap[depKey] ?: return@forEach
                val result = CalculateValueEngine.evaluate(expr, formData) ?: return@forEach
                if (formData[depKey]?.toString() != result) {
                    formData[depKey] = result
                    rerenderField(depKey)
                    evaluateConditionalsDependingOn(depKey)
                }
            }
        } finally {
            activeEvalKeys.remove(changedKey)
        }
    }

    private fun rerenderField(key: String) {
        val component = componentMap[key] ?: return
        val parent = parentMap[key] ?: return
        val oldView = viewMap[key] ?: return
        val index = parent.indexOfChild(oldView)
        if (index < 0) return
        parent.removeView(oldView)
        val newView = try {
            fieldFactory.createView(component, formData) { k, v ->
                formData[k] = v
                onDataChange(k, v)
                evaluateConditionalsDependingOn(k)
            }
        } catch (e: Exception) {
            android.util.Log.e("FormEngine", "rerenderField failed for '$key': ${e.message}", e)
            parent.addView(oldView, index) // put the old view back to avoid blank gap
            return
        } ?: return
        viewMap[key] = newView
        val visible = conditionalEngine.shouldShow(component, formData)
        newView.visibility = if (visible) View.VISIBLE else View.GONE
        parent.addView(newView, index)
    }
}
