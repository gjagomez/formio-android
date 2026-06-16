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
    // Advanced Logic: triggerKey → component keys with logic rules depending on it,
    // plus per-component property overrides and logic-driven hidden state
    private val logicDeps = mutableMapOf<String, MutableList<String>>()
    private val logicOverrides = mutableMapOf<String, FormComponent>()
    private val logicHidden = mutableSetOf<String>()
    // Guard against circular calculateValue chains
    private val activeEvalKeys = mutableSetOf<String>()
    // Collision-free registry keys for layout containers (duplicate/blank keys)
    private var anonKeyCounter = 0

    fun render(components: List<FormComponent>) {
        currentComponents = components
        container.removeAllViews()
        viewMap.clear()
        componentMap.clear()
        parentMap.clear()
        refreshDeps.clear()
        calculateValueMap.clear()
        calculateDeps.clear()
        logicDeps.clear()
        logicOverrides.clear()
        logicHidden.clear()

        buildCalculateDeps(components)

        components.forEach { component -> renderComponent(component, container) }
        evaluateAllConditionals()
        evaluateAllCalculateValues()
        componentMap.values.filter { it.logic != null }.forEach { evaluateLogicFor(it.key) }
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
            comp.tableRows?.forEach { row -> row.forEach { cell -> buildCalculateDeps(cell.components) } }
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
        // Layout containers are rendered by the engine itself (not their own
        // builders) so every nested field registers its conditionals, logic,
        // refreshOn and calculateValue and reacts to data changes at any depth.
        when {
            component.type == "panel" || component.type == "well" -> {
                renderPanelContainer(component, parent); return
            }
            component.type == "columns" -> {
                renderColumnsContainer(component, parent); return
            }
            component.type == "table" -> {
                renderTableContainer(component, parent); return
            }
            component.type == "hidden" -> {
                if (formData[component.key] == null) {
                    formData[component.key] = TextFieldBuilder.safeStr(component.defaultValue)
                }
                return
            }
            // Typeless containers (some designers emit groups without `type`)
            component.type.isBlank() && !component.components.isNullOrEmpty() -> {
                component.components.forEach { renderComponent(it, parent) }
                return
            }
        }

        componentMap[component.key] = component
        parentMap[component.key] = parent

        component.refreshOn?.let { trigger ->
            refreshDeps.getOrPut(trigger) { mutableListOf() }.add(component.key)
        }

        component.logic?.let { rules ->
            LogicEngine.dependencies(rules).forEach { dep ->
                logicDeps.getOrPut(dep) { mutableListOf() }.add(component.key)
            }
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

    /** Registers a layout container under a collision-free key so its own conditional works. */
    private fun registerContainer(component: FormComponent, view: View, parent: LinearLayout) {
        var key = component.key.ifBlank { "__container" }
        if (componentMap.containsKey(key)) key = "$key#${anonKeyCounter++}"
        componentMap[key] = component
        viewMap[key] = view
        parentMap[key] = parent
    }

    private fun renderPanelContainer(component: FormComponent, parent: LinearLayout) {
        val dp = context.resources.displayMetrics.density
        val card = MaterialCardView(context).apply {
            setCardBackgroundColor(context.getColor(R.color.bg_card))
            radius = context.resources.getDimension(R.dimen.corner_card)
            cardElevation = 0f
            strokeColor = context.getColor(R.color.stroke)
            strokeWidth = 1
        }
        val outer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val pad = context.resources.getDimensionPixelSize(R.dimen.spacing_md)
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, pad)
        }

        val title = component.title?.takeIf { it.isNotBlank() } ?: component.label.takeIf {
            it.isNotBlank() && component.type == "panel"
        }
        if (title != null) {
            val chevron = TextView(context).apply {
                textSize = 14f
                setTextColor(context.getColor(R.color.text_secondary))
            }
            val titleView = TextView(context).apply {
                text = title
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(context.getColor(R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(pad, pad, pad, (10 * dp).toInt())
                isClickable = true
                isFocusable = true
                addView(titleView)
                addView(chevron)
            }
            // Any titled panel can collapse — long forms stay navigable
            var expanded = !component.collapsed
            fun applyExpanded() {
                inner.visibility = if (expanded) View.VISIBLE else View.GONE
                chevron.text = if (expanded) "▾" else "▸"
            }
            applyExpanded()
            header.setOnClickListener {
                expanded = !expanded
                applyExpanded()
            }
            outer.addView(header)
        } else {
            inner.setPadding(pad, pad, pad, pad)
        }

        outer.addView(inner)
        card.addView(outer)

        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, context.resources.getDimensionPixelSize(R.dimen.spacing_sm))
        }
        wrapper.addView(card)
        parent.addView(wrapper)
        registerContainer(component, wrapper, parent)

        component.components?.forEach { child -> renderComponent(child, inner) }
    }

    private fun renderColumnsContainer(component: FormComponent, parent: LinearLayout) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = (component.columns?.sumOf { it.width } ?: 12).toFloat()
        }
        component.columns?.forEach { col ->
            val colContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, col.width.toFloat()
                )
            }
            col.components.forEach { child -> renderComponent(child, colContainer) }
            row.addView(colContainer)
        }
        parent.addView(row)
        registerContainer(component, row, parent)
    }

    private fun renderTableContainer(component: FormComponent, parent: LinearLayout) {
        val dp = context.resources.displayMetrics.density
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
                cell.components.forEach { child -> renderComponent(child, cellView) }
                rowView.addView(cellView)
            }
            table.addView(rowView)
        }
        parent.addView(table)
        registerContainer(component, table, parent)
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
                "table" ->
                    comp.tableRows?.forEach { rowCells ->
                        rowCells.forEach { cell -> errors += validateRecursive(cell.components, data) }
                    }
                "" -> comp.components?.let { errors += validateRecursive(it, data) }
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
        val valueStr = value?.toString() ?: ""

        // 1. Try direct tag-based update (works for text, number, textarea, select)
        val til = container.findViewWithTag<TextInputLayout>(key)
        if (til != null) {
            til.editText?.also { et ->
                if (et.text.toString() != valueStr) et.setText(valueStr)
            }
            evaluateConditionalsDependingOn(key)
            return
        }

        // 2. For non-text fields (radio, selectboxes, signature, file, etc.)
        //    fall back to full re-render
        if (componentMap.containsKey(key)) {
            rerenderField(key)
        } else {
            android.util.Log.w("FormEngine", "externalUpdate: no view found for key='$key'")
        }
        evaluateConditionalsDependingOn(key)
    }

    private fun isVisible(component: FormComponent): Boolean =
        component.key !in logicHidden && conditionalEngine.shouldShow(component, formData)

    private fun evaluateAllConditionals() {
        componentMap.values.forEach { comp ->
            val view = viewMap[comp.key] ?: return@forEach
            view.visibility = if (isVisible(comp)) View.VISIBLE else View.GONE
        }
    }

    /**
     * Re-evaluates the Advanced Logic rules of one component: runs `value` actions
     * and rebuilds the field when a `property` override (disabled, required, label…)
     * or the logic-driven hidden state changes.
     */
    private fun evaluateLogicFor(key: String) {
        val base = componentMap[key] ?: return
        val rules = base.logic ?: return

        var effective = base
        var hidden = false
        var newValue: String? = null
        rules.forEach { rule ->
            if (!LogicEngine.triggerFires(rule, formData)) return@forEach
            rule.actions.forEach { action ->
                when (action.type) {
                    "value" -> CalculateValueEngine.evaluate(action.value ?: "", formData)
                        ?.let { newValue = it }
                    "property" -> {
                        if (action.property == "hidden") {
                            val s = action.state ?: ""
                            hidden = s.equals("true", ignoreCase = true) || s == "1"
                        } else {
                            effective = LogicEngine.applyPropertyAction(effective, action)
                        }
                    }
                }
            }
        }

        val overrideChanged = if (effective != base) {
            logicOverrides.put(key, effective) != effective
        } else {
            logicOverrides.remove(key) != null
        }
        val hiddenChanged = if (hidden) logicHidden.add(key) else logicHidden.remove(key)
        val valueChanged = newValue != null && formData[key]?.toString() != newValue
        if (valueChanged) formData[key] = newValue

        if (valueChanged || overrideChanged) {
            rerenderField(key)
            if (valueChanged) evaluateConditionalsDependingOn(key)
        } else if (hiddenChanged) {
            viewMap[key]?.visibility = if (isVisible(base)) View.VISIBLE else View.GONE
        }
    }

    private fun evaluateConditionalsDependingOn(changedKey: String) {
        if (changedKey in activeEvalKeys) return
        activeEvalKeys.add(changedKey)
        try {
            componentMap.values.forEach { comp ->
                if (changedKey in conditionalEngine.dependsOn(comp)) {
                    val view = viewMap[comp.key] ?: return@forEach
                    view.visibility = if (isVisible(comp)) View.VISIBLE else View.GONE
                }
            }
            // Re-render cascade selects whose data source depends on changedKey
            refreshDeps[changedKey]?.toList()?.forEach { dependentKey ->
                if (componentMap[dependentKey]?.clearOnRefresh == true) {
                    formData.remove(dependentKey)
                }
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
            // Re-evaluate Advanced Logic rules triggered by changedKey
            logicDeps[changedKey]?.toList()?.forEach { dependentKey ->
                evaluateLogicFor(dependentKey)
            }
        } finally {
            activeEvalKeys.remove(changedKey)
        }
    }

    private fun rerenderField(key: String) {
        val component = logicOverrides[key] ?: componentMap[key] ?: return
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
        newView.visibility = if (isVisible(component)) View.VISIBLE else View.GONE
        parent.addView(newView, index)
    }
}
