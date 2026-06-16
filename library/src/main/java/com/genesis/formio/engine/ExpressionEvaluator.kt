package com.genesis.formio.engine

object ExpressionEvaluator {

    fun evaluate(expression: String, formData: Map<String, Any?>): Boolean {
        val expr = extractShowExpression(expression) ?: return true
        return evaluateOr(expr.trim(), formData)
    }

    private fun extractShowExpression(raw: String): String? {
        val cleaned = raw.trim().trimEnd(';')
        // Match "show = expr" or "value = expr" (assignment, not comparison ==)
        val match = Regex("""^(\w+)\s*=(?!=)(.+)$""", RegexOption.DOT_MATCHES_ALL).find(cleaned)
        return if (match != null) match.groupValues[2].trim() else cleaned
    }

    private fun evaluateOr(expr: String, data: Map<String, Any?>): Boolean {
        if ("||" in expr) {
            return expr.split("||").any { evaluateAnd(it.trim(), data) }
        }
        return evaluateAnd(expr, data)
    }

    private fun evaluateAnd(expr: String, data: Map<String, Any?>): Boolean {
        if ("&&" in expr) {
            return expr.split("&&").all { evaluateComparison(it.trim(), data) }
        }
        return evaluateComparison(expr, data)
    }

    private fun evaluateComparison(expr: String, data: Map<String, Any?>): Boolean {
        val resolved = resolveReferences(expr, data)
        return when {
            "===" in resolved -> {
                val parts = resolved.split("===")
                normalize(parts[0]) == normalize(parts[1])
            }
            "!==" in resolved -> {
                val parts = resolved.split("!==")
                normalize(parts[0]) != normalize(parts[1])
            }
            "==" in resolved -> {
                val parts = resolved.split("==")
                normalize(parts[0]) == normalize(parts[1])
            }
            "!=" in resolved -> {
                val parts = resolved.split("!=")
                normalize(parts[0]) != normalize(parts[1])
            }
            else -> resolved.trim().lowercase() == "true"
        }
    }

    private fun resolveReferences(expr: String, data: Map<String, Any?>): String {
        var result = expr
        Regex("""(?:data|row)\.(\w+)""").findAll(expr).forEach { match ->
            val key = match.groupValues[1]
            val value = data[key]?.toString() ?: ""
            result = result.replace(match.value, "\"$value\"")
        }
        return result
    }

    private fun normalize(value: String): String =
        value.trim().trim('"').trim('\'')
}
