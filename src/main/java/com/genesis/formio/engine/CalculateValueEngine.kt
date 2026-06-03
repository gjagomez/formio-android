package com.genesis.formio.engine

/**
 * Evaluates formio.js `calculateValue` JavaScript expressions in Kotlin.
 * Supports: if/else if/else, ===, !==, ==, !=, ||, &&, row.KEY, data.KEY,
 * string literals, string concatenation (+), and value assignment.
 */
object CalculateValueEngine {

    fun evaluate(expression: String, formData: Map<String, Any?>): String? {
        return try {
            evaluateExpr(expression.trim(), formData)
        } catch (_: Exception) {
            null
        }
    }

    fun extractDependencies(expression: String): Set<String> =
        Regex("""(?:row|data)\.(\w+)""")
            .findAll(expression)
            .map { it.groupValues[1] }
            .toSet()

    // ── Top-level dispatch ────────────────────────────────────────────────────

    private fun evaluateExpr(expr: String, data: Map<String, Any?>): String? {
        val trimmed = expr.trim()
        // if / else-if / else chain
        if (trimmed.startsWith("if")) return evaluateIfChain(trimmed, data)
        // Direct: value = 'literal'
        Regex("""^value\s*=\s*['"]([^'"]*?)['"]""").find(trimmed)
            ?.let { return it.groupValues[1] }
        // Direct: value = someExpression
        Regex("""^value\s*=\s*(.+)$""", RegexOption.DOT_MATCHES_ALL).find(trimmed)
            ?.let { return evaluateValueExpr(it.groupValues[1].trim().trimEnd(';'), data) }
        return null
    }

    // ── if / else-if / else chain ─────────────────────────────────────────────

    private fun evaluateIfChain(expr: String, data: Map<String, Any?>): String? {
        var remaining = expr.trim()
        while (remaining.isNotEmpty()) {
            val t = remaining.trimStart()
            when {
                t.startsWith("if") && !t.startsWith("if(").not() -> {
                    // "if(" or "if ("
                    val rest = t.removePrefix("if").trimStart()
                    val (cond, body, after) = extractCondBody(rest)
                    remaining = after.trim()
                    if (evaluateCondition(cond, data)) return extractValue(body, data)
                }
                t.startsWith("else if") -> {
                    val rest = t.removePrefix("else").trimStart()
                    val (cond, body, after) = extractCondBody(rest)
                    remaining = after.trim()
                    if (evaluateCondition(cond, data)) return extractValue(body, data)
                }
                t.startsWith("else") -> {
                    val rest = t.removePrefix("else").trimStart()
                    val braceStart = rest.indexOf('{')
                    if (braceStart < 0) return null
                    val braceEnd = matchingChar(rest, braceStart, '{', '}')
                    val body = rest.substring(braceStart + 1, braceEnd).trim()
                    return extractValue(body, data)
                }
                else -> break
            }
        }
        return null
    }

    private fun extractCondBody(expr: String): Triple<String, String, String> {
        val parenStart = expr.indexOf('(')
        val parenEnd   = matchingChar(expr, parenStart, '(', ')')
        val condition  = expr.substring(parenStart + 1, parenEnd)
        val braceStart = expr.indexOf('{', parenEnd)
        val braceEnd   = matchingChar(expr, braceStart, '{', '}')
        val body       = expr.substring(braceStart + 1, braceEnd).trim()
        val after      = expr.substring(braceEnd + 1)
        return Triple(condition, body, after)
    }

    private fun extractValue(body: String, data: Map<String, Any?>): String? {
        val t = body.trim()
        // value = 'literal'
        Regex("""value\s*=\s*['"]([^'"]*?)['"]""").find(t)
            ?.let { return it.groupValues[1] }
        // Nested if/else
        if (t.startsWith("if")) return evaluateIfChain(t, data)
        // value = expression
        Regex("""value\s*=\s*(.+)$""", RegexOption.DOT_MATCHES_ALL).find(t)
            ?.let { return evaluateValueExpr(it.groupValues[1].trim().trimEnd(';'), data) }
        return null
    }

    // ── Condition evaluation ─────────────────────────────────────────────────

    private fun evaluateCondition(cond: String, data: Map<String, Any?>): Boolean {
        val orParts = splitTopLevel(cond, "||")
        if (orParts.size > 1) return orParts.any { evaluateAnd(it.trim(), data) }
        return evaluateAnd(cond.trim(), data)
    }

    private fun evaluateAnd(expr: String, data: Map<String, Any?>): Boolean {
        val andParts = splitTopLevel(expr, "&&")
        if (andParts.size > 1) return andParts.all { evaluateComparison(it.trim(), data) }
        return evaluateComparison(expr.trim(), data)
    }

    private fun evaluateComparison(expr: String, data: Map<String, Any?>): Boolean {
        val r = resolveRefs(expr, data)
        return when {
            "!==" in r -> { val p = r.split("!==", limit = 2); norm(p[0]) != norm(p[1]) }
            "===" in r -> { val p = r.split("===", limit = 2); norm(p[0]) == norm(p[1]) }
            "!="  in r -> { val p = r.split("!=" , limit = 2); norm(p[0]) != norm(p[1]) }
            "=="  in r -> { val p = r.split("==" , limit = 2); norm(p[0]) == norm(p[1]) }
            else -> r.trim() == "true"
        }
    }

    // ── Value expression evaluation (concatenation, etc.) ────────────────────

    private fun evaluateValueExpr(expr: String, data: Map<String, Any?>): String {
        val parts = splitTopLevel(expr, "+")
        if (parts.size > 1) {
            return parts.joinToString("") { part ->
                val t = part.trim()
                when {
                    t.startsWith("'") || t.startsWith("\"") ->
                        t.trim('\'').trim('"')
                    Regex("""(?:row|data)\.(\w+)""").containsMatchIn(t) ->
                        data[Regex("""(?:row|data)\.(\w+)""").find(t)!!.groupValues[1]]?.toString() ?: ""
                    else -> data[t]?.toString() ?: t
                }
            }
        }
        return resolveRefs(expr, data).trim('"').trim('\'')
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun resolveRefs(expr: String, data: Map<String, Any?>): String {
        var result = expr
        Regex("""(?:row|data)\.(\w+)""").findAll(expr).toList().asReversed().forEach { match ->
            val value = data[match.groupValues[1]]?.toString() ?: ""
            result = result.replaceRange(match.range, "\"$value\"")
        }
        return result
    }

    private fun norm(s: String) = s.trim().trim('"').trim('\'').trim()

    private fun matchingChar(s: String, openIdx: Int, open: Char, close: Char): Int {
        var depth = 0
        for (i in openIdx until s.length) {
            when (s[i]) { open -> depth++; close -> { depth--; if (depth == 0) return i } }
        }
        return s.length - 1
    }

    private fun splitTopLevel(expr: String, op: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var inStr = false
        var strChar = ' '
        val cur = StringBuilder()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                inStr                              -> { cur.append(c); if (c == strChar) inStr = false }
                c == '\'' || c == '"'             -> { inStr = true; strChar = c; cur.append(c) }
                c == '(' || c == '['              -> { depth++; cur.append(c) }
                c == ')' || c == ']'              -> { depth--; cur.append(c) }
                depth == 0 && expr.startsWith(op, i) -> {
                    parts.add(cur.toString()); cur.clear(); i += op.length; continue
                }
                else                              -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty()) parts.add(cur.toString())
        return parts
    }
}
