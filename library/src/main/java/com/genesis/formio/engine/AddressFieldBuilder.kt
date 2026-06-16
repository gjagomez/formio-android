package com.genesis.formio.engine

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.TextView
import android.view.ViewGroup
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Form.io `address` component.
 *
 * Search mode queries the configured provider (only `nominatim` / OpenStreetMap
 * is supported, which is also Form.io's default) and stores the full provider
 * result plus `mode: "autocomplete"`. The manual-mode toggle renders the child
 * fields from the schema (address1, city, …) and stores their values plus
 * `mode: "manual"`.
 */
class AddressFieldBuilder(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density
        val isRequired = component.validate?.required == true

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        container.addView(FieldLabelBuilder.build(context, component.label, isRequired))

        // Current stored value (Map when restored in-session, JSON string from drafts)
        val current: MutableMap<String, Any?> = linkedMapOf()
        when (val raw = formData[component.key]) {
            is Map<*, *> -> raw.forEach { (k, v) -> if (k != null) current[k.toString()] = v }
            is String -> try {
                val o = JSONObject(raw)
                o.keys().forEach { k -> current[k] = o.opt(k) }
            } catch (_: Exception) { /* not JSON */ }
        }

        fun sync() {
            val value = LinkedHashMap(current)
            formData[component.key] = value
            onChange(component.key, value)
            container.findViewWithTag<TextView>("__err__${component.key}")?.visibility = View.GONE
        }

        // ── Search input ───────────────────────────────────────────────────────
        val searchEdit = EditText(context).apply {
            hint = component.placeholder?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.formio_address_buscar)
            setHintTextColor(context.getColor(R.color.text_hint))
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 15f
            isEnabled = !component.disabled
            maxLines = 2
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.bg_elevated))
                cornerRadius = 12 * dp
                setStroke((1.5f * dp).toInt(), context.getColor(R.color.input_border))
            }
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            setText(current["display_name"]?.toString() ?: "")
        }
        container.addView(searchEdit)

        val statusText = TextView(context).apply {
            setTextColor(context.getColor(R.color.text_hint))
            textSize = 12f
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), 0, 0)
            visibility = View.GONE
        }
        container.addView(statusText)

        var searchResults: List<JSONObject> = emptyList()

        fun showResults(anchor: View) {
            if (searchResults.isEmpty()) return
            val popup = ListPopupWindow(context)
            popup.anchorView = anchor
            popup.width = ListPopupWindow.MATCH_PARENT
            popup.isModal = true
            popup.setAdapter(object : ArrayAdapter<String>(
                context, android.R.layout.simple_list_item_1,
                searchResults.map { it.optString("display_name", "") }
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    TextView(context).apply {
                        text = getItem(position)
                        setTextColor(context.getColor(R.color.text_primary))
                        setBackgroundColor(context.getColor(R.color.bg_card))
                        textSize = 13f
                        setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
                    }
            })
            popup.setOnItemClickListener { _, _, position, _ ->
                searchResults.getOrNull(position)?.let { picked ->
                    current.clear()
                    current["mode"] = "autocomplete"
                    picked.keys().forEach { k -> current[k] = jsonToPlain(picked.opt(k)) }
                    searchEdit.setText(picked.optString("display_name", ""))
                    sync()
                }
                popup.dismiss()
            }
            popup.show()
        }

        fun search(query: String) {
            if (query.isBlank()) return
            statusText.text = context.getString(R.string.formio_address_buscando)
            statusText.visibility = View.VISIBLE
            executor.execute {
                val results = try { nominatimSearch(query) } catch (_: Exception) { emptyList() }
                mainHandler.post {
                    searchResults = results
                    statusText.visibility =
                        if (results.isEmpty()) View.VISIBLE else View.GONE
                    if (results.isEmpty()) {
                        statusText.text = context.getString(R.string.formio_address_sin_resultados)
                    }
                    showResults(searchEdit)
                }
            }
        }

        searchEdit.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(v.text.toString())
                true
            } else false
        }

        // ── Manual mode ────────────────────────────────────────────────────────
        val manualChildren = component.components ?: emptyList()
        if (manualChildren.isNotEmpty() && !component.disabled) {
            val manualLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (current["mode"] == "manual") View.VISIBLE else View.GONE
                setPadding(0, (8 * dp).toInt(), 0, 0)
            }

            val toggle = TextView(context).apply {
                text = context.getString(R.string.formio_address_manual)
                setTextColor(context.getColor(R.color.accent_green))
                textSize = 13f
                setPadding((4 * dp).toInt(), (8 * dp).toInt(), 0, 0)
                isClickable = true
                isFocusable = true
            }

            // Render the schema children with their manualMode conditional stripped
            val fieldFactory = FieldFactory(context)
            val manualMap = mutableMapOf<String, Any?>()
            manualChildren.forEach { child ->
                if (current["mode"] == "manual") manualMap[child.key] = current[child.key]
            }
            manualChildren.forEach { child ->
                fieldFactory.createView(
                    child.copy(customConditional = null, conditional = null),
                    manualMap
                ) { key, value ->
                    manualMap[key] = value
                    current.clear()
                    current["mode"] = "manual"
                    manualMap.forEach { (k, v) -> current[k] = v }
                    sync()
                }?.let { manualLayout.addView(it) }
            }

            toggle.setOnClickListener {
                val manualOn = manualLayout.visibility != View.VISIBLE
                manualLayout.visibility = if (manualOn) View.VISIBLE else View.GONE
                searchEdit.visibility = if (manualOn) View.GONE else View.VISIBLE
                toggle.text = context.getString(
                    if (manualOn) R.string.formio_address_buscar_toggle
                    else R.string.formio_address_manual
                )
            }
            if (current["mode"] == "manual") {
                searchEdit.visibility = View.GONE
                toggle.text = context.getString(R.string.formio_address_buscar_toggle)
            }

            container.addView(toggle)
            container.addView(manualLayout)
        }

        // Error label
        container.addView(TextView(context).apply {
            tag = "__err__${component.key}"
            setTextColor(context.getColor(R.color.error_color))
            textSize = 12f
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), 0, 0)
            visibility = View.GONE
        })

        return container
    }

    // ── Nominatim (OpenStreetMap) ──────────────────────────────────────────────

    private fun nominatimSearch(query: String): List<JSONObject> {
        val url = "https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&limit=5&q=" +
            URLEncoder.encode(query, "UTF-8")
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            // Nominatim's usage policy requires an identifying User-Agent
            conn.setRequestProperty("User-Agent", "formio-android/1.0")
            conn.setRequestProperty("Accept", "application/json")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        } finally {
            conn.disconnect()
        }
    }

    /** Converts org.json nodes to plain Kotlin maps/lists so Gson serializes them cleanly. */
    private fun jsonToPlain(node: Any?): Any? = when (node) {
        is JSONObject -> {
            val map = linkedMapOf<String, Any?>()
            node.keys().forEach { k -> map[k] = jsonToPlain(node.opt(k)) }
            map
        }
        is JSONArray -> (0 until node.length()).map { jsonToPlain(node.opt(it)) }
        JSONObject.NULL -> null
        else -> node
    }
}
