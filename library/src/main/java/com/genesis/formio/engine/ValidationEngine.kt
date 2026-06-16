package com.genesis.formio.engine

import android.content.Context
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent

class ValidationEngine(private val context: Context) {

    fun validate(components: List<FormComponent>, formData: Map<String, Any?>): List<String> {
        val errors = mutableListOf<String>()
        components.forEach { comp ->
            val value = formData[comp.key]?.toString()?.takeIf { it != "null" } ?: ""
            val error = validateComponent(comp, value) ?: return@forEach
            errors.add("${comp.label}: $error")
        }
        return errors
    }

    fun validateComponent(component: FormComponent, value: String): String? {
        val error = validateInternal(component, value) ?: return null
        // If a customMessage is set, show it for any validation failure
        return component.validate?.customMessage?.takeIf { it.isNotBlank() } ?: error
    }

    private fun validateInternal(component: FormComponent, value: String): String? {
        val v = component.validate ?: return null
        val fieldName = component.errorLabel?.takeIf { it.isNotBlank() } ?: component.label

        if (v.required && value.isBlank())
            return context.getString(R.string.formio_error_campo_requerido)

        // A required checkbox must be checked, not merely non-blank
        if (v.required && component.type == "checkbox" && value != "true")
            return context.getString(R.string.formio_error_campo_requerido)

        if (value.isBlank()) return null

        // Numeric range and decimal validation
        val numericValue = value.replace(",", "").toDoubleOrNull()
        if (numericValue != null) {
            v.min?.let { min -> if (numericValue < min) return "$fieldName: valor mínimo $min" }
            v.max?.let { max -> if (numericValue > max) return "$fieldName: valor máximo $max" }
        }
        if (component.requireDecimal && !value.contains('.'))
            return "$fieldName: debe incluir decimales (ej: $value.00)"

        v.minLength?.takeIf { it > 0 }?.let { min ->
            if (value.length < min) return "$fieldName: mínimo $min caracteres"
        }
        v.maxLength?.takeIf { it > 0 }?.let { max ->
            if (value.length > max) return "$fieldName: máximo $max caracteres"
        }
        v.pattern?.takeIf { it.isNotBlank() }?.let { pattern ->
            try {
                if (!Regex(pattern).matches(value)) return "$fieldName: formato inválido"
            } catch (_: Exception) { /* patrón mal formado, ignorar */ }
        }
        v.minWords?.takeIf { it > 0 }?.let { min ->
            if (countWords(value) < min) return "$fieldName: mínimo $min palabra(s)"
        }
        v.maxWords?.takeIf { it > 0 }?.let { max ->
            if (countWords(value) > max) return "$fieldName: máximo $max palabra(s)"
        }
        v.custom?.takeIf { it.isNotBlank() }?.let { custom ->
            evaluateCustomJs(custom, value)?.let { return it }
        }

        return null
    }

    private fun countWords(value: String): Int =
        value.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    /**
     * Tries to evaluate simple formio custom validators written in JavaScript.
     * Supports the common pattern:
     *   valid = input === '' ? true : /regex/.test(input) ? true : 'Error message';
     */
    private fun evaluateCustomJs(custom: String, input: String): String? {
        // Pattern: /regex/[flags].test(input)
        val regexMatch = Regex("""/(.+?)/([a-z]*)\.test\s*\(\s*input\s*\)""").find(custom)
        if (regexMatch != null) {
            val pattern = regexMatch.groupValues[1]
            val flags = regexMatch.groupValues[2]
            return try {
                val options = buildSet {
                    if ('i' in flags) add(RegexOption.IGNORE_CASE)
                    if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
                }
                val regex = Regex(pattern, options)
                if (!regex.containsMatchIn(input)) {
                    extractJsErrorMessage(custom) ?: "Valor no válido"
                } else null
            } catch (e: Exception) {
                null
            }
        }

        // Pattern: valid = someCondition (simple boolean assignment)
        if (custom.contains("valid = true") && !custom.contains("?")) return null
        if (custom.contains("valid = false") && !custom.contains("?")) return "Valor no válido"

        return null // Complex JS — skip evaluation
    }

    private fun extractJsErrorMessage(custom: String): String? =
        Regex(""":\s*['"]([^'"]{2,120})['"]""").findAll(custom).lastOrNull()?.groupValues?.get(1)
}
