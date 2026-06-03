package com.genesis.formio.engine

import com.genesis.formio.model.FormComponent

class ConditionalEngine {

    fun shouldShow(component: FormComponent, formData: Map<String, Any?>): Boolean {
        val customCond = component.customConditional
        if (!customCond.isNullOrBlank()) {
            return ExpressionEvaluator.evaluate(customCond, formData)
        }
        val cond = component.conditional ?: return true
        if (cond.show == null || cond.when_field.isNullOrBlank()) return true
        val currentValue = formData[cond.when_field]?.toString() ?: ""
        val matches = currentValue == cond.eq
        return if (cond.show) matches else !matches
    }

    fun dependsOn(component: FormComponent): Set<String> {
        val deps = mutableSetOf<String>()
        component.conditional?.when_field?.let { deps.add(it) }
        val custom = component.customConditional ?: return deps
        Regex("""(?:data|row)\.(\w+)""").findAll(custom).forEach {
            deps.add(it.groupValues[1])
        }
        return deps
    }
}
