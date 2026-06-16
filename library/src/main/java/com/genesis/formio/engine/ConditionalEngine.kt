package com.genesis.formio.engine

import com.genesis.formio.model.FormComponent
import org.json.JSONArray
import org.json.JSONObject

class ConditionalEngine {

    fun shouldShow(component: FormComponent, formData: Map<String, Any?>): Boolean {
        // 1. Custom JS conditional (highest priority)
        val customCond = component.customConditional
        if (!customCond.isNullOrBlank()) {
            return ExpressionEvaluator.evaluate(customCond, formData)
        }

        // 2. JSON Logic conditional  ("conditional": {"json": {...}})
        val jsonLogic = component.conditionalJsonLogic
        if (!jsonLogic.isNullOrBlank()) {
            return try {
                val logic = JSONObject(jsonLogic)
                isTruthy(evalLogic(logic, formData))
            } catch (_: Exception) { true }
        }

        // 3. Simple conditional ({show, when, eq})
        val cond = component.conditional ?: return true
        if (cond.show == null || cond.when_field.isNullOrBlank()) return true
        val rawValue = formData[cond.when_field]?.toString() ?: ""
        val matches = matchesValue(rawValue, cond.eq ?: "")
        return if (cond.show) matches else !matches
    }

    fun dependsOn(component: FormComponent): Set<String> {
        val deps = mutableSetOf<String>()
        component.conditional?.when_field?.let { deps.add(it) }
        val custom = component.customConditional ?: ""
        Regex("""(?:data|row)\.(\w+)""").findAll(custom).forEach { deps.add(it.groupValues[1]) }
        val jl = component.conditionalJsonLogic ?: ""
        Regex(""""var"\s*:\s*"data\.(\w+)"""").findAll(jl).forEach { deps.add(it.groupValues[1]) }
        return deps
    }

    /** Evaluates a raw JSON-logic expression (used by Advanced Logic triggers). */
    fun evaluateJson(json: String, formData: Map<String, Any?>): Boolean =
        try { isTruthy(evalLogic(JSONObject(json), formData)) } catch (_: Exception) { false }

    // ── JSON Logic evaluator ───────────────────────────────────────────────────

    private fun evalLogic(node: Any?, formData: Map<String, Any?>): Any? {
        if (node !is JSONObject) return node

        val keys = node.keys().asSequence().toList()
        if (keys.isEmpty()) return null
        val op = keys.first()
        val args = node.opt(op)

        return when (op) {
            "var" -> {
                val path = args?.toString() ?: ""
                val key = path.removePrefix("data.").removePrefix("row.")
                formData[key]?.toString() ?: ""
            }
            "===", "==" -> {
                val a = args as? JSONArray ?: return false
                resolve(a.opt(0), formData)?.toString() == resolve(a.opt(1), formData)?.toString()
            }
            "!==", "!=" -> {
                val a = args as? JSONArray ?: return false
                resolve(a.opt(0), formData)?.toString() != resolve(a.opt(1), formData)?.toString()
            }
            "!" -> {
                val v = if (args is JSONArray) resolve(args.opt(0), formData) else resolve(args, formData)
                !isTruthy(v)
            }
            "!!" -> {
                val v = if (args is JSONArray) resolve(args.opt(0), formData) else resolve(args, formData)
                isTruthy(v)
            }
            "and" -> {
                val a = args as? JSONArray ?: return false
                (0 until a.length()).all { isTruthy(resolve(a.opt(it), formData)) }
            }
            "or" -> {
                val a = args as? JSONArray ?: return false
                (0 until a.length()).any { isTruthy(resolve(a.opt(it), formData)) }
            }
            "if" -> {
                val a = args as? JSONArray ?: return null
                var i = 0
                while (i + 1 < a.length()) {
                    if (isTruthy(resolve(a.opt(i), formData))) return resolve(a.opt(i + 1), formData)
                    i += 2
                }
                if (i < a.length()) resolve(a.opt(i), formData) else null
            }
            ">" -> compareNumbers(args, formData) { l, r -> l > r }
            ">=" -> compareNumbers(args, formData) { l, r -> l >= r }
            "<" -> compareNumbers(args, formData) { l, r -> l < r }
            "<=" -> compareNumbers(args, formData) { l, r -> l <= r }
            "+" -> {
                val a = args as? JSONArray ?: return 0
                (0 until a.length()).sumOf {
                    resolve(a.opt(it), formData)?.toString()?.toDoubleOrNull() ?: 0.0
                }
            }
            "-" -> {
                val a = args as? JSONArray ?: return 0
                val l = resolve(a.opt(0), formData)?.toString()?.toDoubleOrNull() ?: 0.0
                val r = resolve(a.opt(1), formData)?.toString()?.toDoubleOrNull() ?: 0.0
                l - r
            }
            "in" -> {
                val a = args as? JSONArray ?: return false
                val needle = resolve(a.opt(0), formData)?.toString() ?: ""
                val haystack = resolve(a.opt(1), formData)?.toString() ?: ""
                haystack.contains(needle)
            }
            "cat" -> {
                val a = args as? JSONArray ?: return ""
                (0 until a.length()).joinToString("") { resolve(a.opt(it), formData)?.toString() ?: "" }
            }
            else -> null
        }
    }

    private fun resolve(value: Any?, formData: Map<String, Any?>): Any? =
        if (value is JSONObject) evalLogic(value, formData) else value

    private fun compareNumbers(args: Any?, formData: Map<String, Any?>, op: (Double, Double) -> Boolean): Boolean {
        val a = args as? JSONArray ?: return false
        val l = resolve(a.opt(0), formData)?.toString()?.toDoubleOrNull() ?: return false
        val r = resolve(a.opt(1), formData)?.toString()?.toDoubleOrNull() ?: return false
        return op(l, r)
    }

    private fun isTruthy(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is String -> value.isNotBlank() && value != "false" && value != "0"
        else -> true
    }

    // ── Simple conditional helper ──────────────────────────────────────────────

    private fun matchesValue(stored: String, eq: String): Boolean {
        if (stored.startsWith("{")) {
            return try {
                JSONObject(stored).optBoolean(eq, false)
            } catch (_: Exception) { stored == eq }
        }
        return stored == eq
    }
}
