package com.genesis.formio.engine

import org.json.JSONObject

/**
 * Helpers for field values that may be stored as objects instead of plain strings.
 *
 * Selects with `dataType: "object"` store `{"label": ..., "value": ...}` so the
 * submission keeps both; everywhere that needs to compare, display or serialize
 * a value goes through these helpers to support both shapes transparently.
 */
object FieldValueUtil {

    /** Comparable raw value — extracts `value` from object-shaped entries. */
    fun valueOf(raw: Any?): String? = when (raw) {
        null -> null
        is Map<*, *> -> raw["value"]?.toString()
        is JSONObject -> if (raw.has("value")) raw.opt("value")?.toString() else raw.toString()
        is String -> {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{")) {
                try {
                    JSONObject(trimmed).opt("value")?.toString() ?: raw
                } catch (_: Exception) { raw }
            } else raw
        }
        else -> raw.toString()
    }

    /** Human-readable label — extracts `label` from object-shaped entries. */
    fun labelOf(raw: Any?): String? = when (raw) {
        null -> null
        is Map<*, *> -> raw["label"]?.toString() ?: raw["value"]?.toString()
        is JSONObject -> raw.optString("label").ifBlank { null } ?: valueOf(raw)
        is String -> {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{")) {
                try {
                    val o = JSONObject(trimmed)
                    o.optString("label").ifBlank { null } ?: valueOf(o)
                } catch (_: Exception) { raw }
            } else raw
        }
        else -> raw.toString()
    }

    /** Converts a row value into something a JSONObject can store faithfully. */
    fun toJsonValue(value: Any?): Any = when (value) {
        null -> ""
        is Boolean -> value
        is Map<*, *> -> JSONObject(value.entries.associate { it.key.toString() to it.value })
        is JSONObject -> value
        else -> value.toString()
    }
}
