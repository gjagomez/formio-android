package com.genesis.formio.engine

import com.genesis.formio.model.FormComponent
import com.genesis.formio.model.LogicAction
import com.genesis.formio.model.LogicRule
import com.genesis.formio.model.ValidateOptions

/**
 * Evaluates Form.io Advanced Logic rules (the "Logic" tab of the designer).
 *
 * Supported triggers: `simple` ({show, when, eq}), `javascript`
 * (e.g. `result = data.x === 'bob';`) and `json` (JSON-logic).
 *
 * Supported actions:
 *  - `value`    — runs the snippet (e.g. `value = 'snob';`) and assigns the result
 *  - `property` — overrides a component property while the trigger holds:
 *                 `disabled`, `hidden`, `validate.required` / `required`,
 *                 `label`, `placeholder`, `description`/`tooltip` (as label suffix is not
 *                 rendered, so only the first four are applied)
 */
object LogicEngine {

    private val conditionalEngine = ConditionalEngine()

    fun triggerFires(rule: LogicRule, formData: Map<String, Any?>): Boolean {
        return when (rule.triggerType) {
            "simple" -> {
                val simple = rule.triggerSimple
                if (simple == null || simple.when_field.isNullOrBlank()) {
                    false
                } else {
                    val raw = FieldValueUtil.valueOf(formData[simple.when_field]) ?: ""
                    val matches = raw == (simple.eq ?: "")
                    if (simple.show == false) !matches else matches
                }
            }
            "javascript" -> {
                val js = rule.triggerJavascript
                if (js.isNullOrBlank()) false else ExpressionEvaluator.evaluate(js, formData)
            }
            "json" -> {
                val json = rule.triggerJson
                if (json.isNullOrBlank()) false else conditionalEngine.evaluateJson(json, formData)
            }
            else -> false
        }
    }

    /** Form-data keys that any of the rules' triggers depend on. */
    fun dependencies(rules: List<LogicRule>): Set<String> {
        val deps = mutableSetOf<String>()
        rules.forEach { rule ->
            rule.triggerSimple?.when_field?.takeIf { it.isNotBlank() }?.let { deps.add(it) }
            rule.triggerJavascript?.let { js ->
                Regex("""(?:data|row)\.(\w+)""").findAll(js).forEach { deps.add(it.groupValues[1]) }
            }
            rule.triggerJson?.let { jl ->
                Regex(""""var"\s*:\s*"(?:data\.|row\.)?(\w+)"""").findAll(jl).forEach { deps.add(it.groupValues[1]) }
            }
        }
        return deps
    }

    /** Applies a `property` action on top of the component, returning the override. */
    fun applyPropertyAction(component: FormComponent, action: LogicAction): FormComponent {
        val state = action.state ?: ""
        val asBool = state.equals("true", ignoreCase = true) || state == "1"
        return when (action.property) {
            "disabled" -> component.copy(disabled = asBool)
            "required", "validate.required" -> component.copy(
                validate = (component.validate ?: ValidateOptions()).copy(required = asBool)
            )
            "label" -> component.copy(label = state)
            "placeholder" -> component.copy(placeholder = state)
            else -> component
        }
    }
}
