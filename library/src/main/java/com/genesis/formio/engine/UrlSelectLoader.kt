package com.genesis.formio.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.genesis.formio.model.FormComponent
import com.genesis.formio.model.SelectOption
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Resolves select options from a remote endpoint (Form.io `dataSrc: "url"`).
 *
 * Supported Form.io schema properties:
 *  - `data.url`        — endpoint, may contain `{{ data.someField }}` placeholders that are
 *                        interpolated with current form values (cascading selects via `refreshOn`)
 *  - `data.headers`    — list of `{key, value}` request headers
 *  - `selectValues`    — dot-path to the array inside the response (e.g. `"Results"`)
 *  - `valueProperty`   — dot-path to the value inside each item (e.g. `"Model_Name"`)
 *  - `template`        — item label template, e.g. `"<span>{{ item.Model_Name }}</span>"`
 *
 * Results are cached per resolved URL for the lifetime of the process, so re-renders
 * (conditionals, calculateValue, refreshOn) don't re-fetch the same endpoint.
 */
object UrlSelectLoader {

    private const val TAG = "UrlSelectLoader"

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = ConcurrentHashMap<String, List<SelectOption>>()

    private val dataPlaceholder = Regex("""\{\{\s*(?:data|row)\.([\w.]+)\s*\}\}""")
    private val itemPlaceholder = Regex("""\{\{\s*item\.([\w.]+)\s*\}\}""")
    private val htmlTag = Regex("<[^>]*>")

    /** Returns already-cached options for this component's resolved URL, if any. */
    fun cached(component: FormComponent, formData: Map<String, Any?>): List<SelectOption>? =
        resolveUrl(component, formData)?.let { cache[it] }

    /**
     * Loads the options asynchronously and invokes [callback] on the main thread.
     * Callback receives null on network/parse failure, an empty list when the URL
     * still has unresolved `{{ data.x }}` placeholders (parent field not selected yet).
     */
    fun load(
        component: FormComponent,
        formData: Map<String, Any?>,
        callback: (List<SelectOption>?) -> Unit
    ) {
        val url = resolveUrl(component, formData)
        if (url == null) {
            callback(emptyList())
            return
        }
        cache[url]?.let { callback(it); return }

        executor.execute {
            val options = try {
                parseOptions(fetch(url, component), component)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load options for '${component.key}' from $url: ${e.message}")
                null
            }
            if (options != null) cache[url] = options
            mainHandler.post { callback(options) }
        }
    }

    /**
     * Interpolates `{{ data.field }}` placeholders with URL-encoded form values.
     * Returns null when the component has no URL or a placeholder has no value yet.
     */
    fun resolveUrl(component: FormComponent, formData: Map<String, Any?>): String? {
        val raw = component.data?.url?.trim()
        if (raw.isNullOrBlank()) return null
        var unresolved = false
        val resolved = dataPlaceholder.replace(raw) { m ->
            val value = FieldValueUtil.valueOf(formData[m.groupValues[1]])
            if (value.isNullOrBlank()) {
                unresolved = true
                ""
            } else {
                URLEncoder.encode(value, "UTF-8")
            }
        }
        return if (unresolved) null else resolved
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private fun fetch(url: String, component: FormComponent): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            component.data?.headers?.forEach { h ->
                if (h.key.isNotBlank()) conn.setRequestProperty(h.key, h.value)
            }
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    /** Parses a static JSON array (`dataSrc: "json"` → `data.json`) into options. */
    fun parseStatic(json: String?, component: FormComponent): List<SelectOption> =
        try {
            if (json.isNullOrBlank()) emptyList() else parseOptions(json, component)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse data.json for '${component.key}': ${e.message}")
            emptyList()
        }

    private fun parseOptions(body: String, component: FormComponent): List<SelectOption> {
        val trimmed = body.trim()
        val items: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            val path = component.selectValues?.takeIf { it.isNotBlank() }
            val node = if (path != null) jsonAtPath(root, path) else firstArrayIn(root)
            node as? JSONArray ?: JSONArray()
        }

        val valuePath = component.valueProperty?.takeIf { it.isNotBlank() }
        val out = ArrayList<SelectOption>(items.length())
        for (i in 0 until items.length()) {
            val item = items.opt(i) ?: continue
            val value = valuePath?.let { jsonAtPath(item, it)?.toString() }
                ?: when (item) {
                    is JSONObject ->
                        item.takeIf { it.has("value") && !it.isNull("value") }?.get("value")?.toString()
                            ?: item.toString()
                    else -> item.toString()
                }
            val label = renderTemplate(component.template, item)
                ?: (item as? JSONObject)?.let { o ->
                    o.optString("label").ifBlank { null } ?: o.optString("name").ifBlank { null }
                }
                ?: value
            if (value.isNotBlank() || label.isNotBlank()) {
                out.add(SelectOption(label = label, value = value))
            }
        }
        return out
    }

    /** Renders `{{ item.x.y }}` placeholders against the item and strips HTML tags. */
    private fun renderTemplate(template: String?, item: Any): String? {
        val tpl = template?.takeIf { it.isNotBlank() } ?: return null
        val rendered = itemPlaceholder.replace(tpl) { m ->
            jsonAtPath(item, m.groupValues[1])?.toString() ?: ""
        }
        return htmlTag.replace(rendered, "").trim().takeIf { it.isNotBlank() }
    }

    /** Walks a dot-path (`a.b.0.c`) through JSONObject/JSONArray nodes. */
    private fun jsonAtPath(node: Any?, path: String): Any? {
        var current: Any? = node
        for (part in path.split('.')) {
            val cur = current
            current = when (cur) {
                is JSONObject -> if (cur.has(part) && !cur.isNull(part)) cur.get(part) else null
                is JSONArray -> part.toIntOrNull()?.let { idx -> cur.opt(idx) }
                else -> null
            } ?: return null
        }
        return current
    }

    /** Fallback when the response is an object and no `selectValues` was given. */
    private fun firstArrayIn(root: JSONObject): JSONArray? {
        for (key in root.keys()) {
            (root.opt(key) as? JSONArray)?.let { return it }
        }
        return null
    }
}
