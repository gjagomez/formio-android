package com.genesis.formio.ui.form

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.genesis.formio.model.FormComponent
import com.genesis.formio.model.Column
import com.genesis.formio.model.ComponentData
import com.genesis.formio.model.ConditionalOptions
import com.genesis.formio.model.SelectOption
import com.genesis.formio.model.ValidateOptions
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject

class FormWizardViewModel(app: Application) : AndroidViewModel(app) {

    val pages = MutableLiveData<List<FormPage>>()
    val currentPageIndex = MutableLiveData(0)
    val formData = mutableMapOf<String, Any?>()
    val debugInfo = MutableLiveData<String?>()

    private val gson = Gson()

    fun loadFromJson(schemaJson: String, prefillData: Map<String, Any?> = emptyMap()) {
        prefillData.forEach { (k, v) -> formData[k] = v }
        try {
            val root = JSONObject(schemaJson)
            val topKeys = root.keys().asSequence().toList().joinToString(", ")
            val componentsArray = root.optJSONArray("components")
            if (componentsArray == null) {
                debugInfo.postValue("SIN components. Claves raíz: $topKeys")
                pages.postValue(emptyList())
                return
            }
            val allComponents = parseComponentsArray(componentsArray)
            if (allComponents.isEmpty()) {
                debugInfo.postValue("components vacío (len=${componentsArray.length()}). Claves raíz: $topKeys")
                pages.postValue(emptyList())
                return
            }
            val wizardPages = allComponents.filter { it.type == "panel" || it.type == "well" }
            val result = if (wizardPages.isNotEmpty()) {
                val panelInfo = wizardPages.joinToString(" | ") { p ->
                    "${p.title ?: p.label}(${p.components?.size ?: 0} fields)"
                }
                debugInfo.postValue("OK wizard: $panelInfo")
                wizardPages.map { panel ->
                    FormPage(title = panel.title ?: panel.label, components = panel.components ?: emptyList())
                }
            } else {
                debugInfo.postValue("OK single-page: ${allComponents.size} campos")
                listOf(FormPage(title = root.optString("title", ""), components = allComponents))
            }
            pages.postValue(result)
        } catch (e: Exception) {
            debugInfo.postValue("EXCEPCIÓN: ${e.javaClass.simpleName}: ${e.message}")
            pages.postValue(emptyList())
        }
    }

    fun getFormDataJson(): String = gson.toJson(formData)

    // ── Schema parsing ─────────────────────────────────────────────────────────

    private fun parseComponentsArray(arr: JSONArray): List<FormComponent> =
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parseComponent(it) } }

    private fun parseComponent(o: JSONObject): FormComponent = FormComponent(
        type = o.optString("type", ""),
        key = o.optString("key", ""),
        label = o.optString("label", ""),
        input = o.optBoolean("input", true),
        validate = (o.opt("validate") as? JSONObject)?.let { parseValidate(it) },
        conditional = (o.opt("conditional") as? JSONObject)?.let { parseConditional(it) },
        customConditional = o.optStr("customConditional"),
        calculateValue = o.optStr("calculateValue"),
        defaultValue = if (o.isNull("defaultValue")) null else o.opt("defaultValue"),
        data = o.optJSONObject("data")?.let { parseComponentData(it) },
        values = o.optJSONArray("values")?.let { parseSelectOptions(it) },
        components = o.optJSONArray("components")?.let { parseComponentsArray(it) },
        disabled = o.optBoolean("disabled", false),
        rows = if (o.has("rows") && !o.isNull("rows")) o.optInt("rows", 3) else null,
        inputMask = o.optStr("inputMask"),
        dataSrc = o.optStr("dataSrc"),
        title = o.optStr("title"),
        content = o.optStr("content"),
        tag = o.optStr("tag"),
        columns = o.optJSONArray("columns")?.let { parseColumns(it) },
        refreshOn = o.optStr("refreshOn"),
        clearOnRefresh = o.optBoolean("clearOnRefresh", false),
        action = o.optStr("action"),
        custom = o.optStr("custom"),
        inputType = o.optStr("inputType"),
        delimiter = o.optBoolean("delimiter", false),
        format = o.optStr("format"),
        multiple = o.optBoolean("multiple", false),
        tableView = o.optBoolean("tableView", false),
        tree = o.optBoolean("tree", false),
        reorder = o.optBoolean("reorder", false),
        addAnotherPosition = o.optStr("addAnotherPosition"),
        placeholder = o.optStr("placeholder"),
        validateOn = o.optStr("validateOn"),
        showCharCount = o.optBoolean("showCharCount", false),
        showWordCount = o.optBoolean("showWordCount", false),
        errorLabel = o.optStr("errorLabel"),
        case = o.optStr("case"),
        requireDecimal = o.optBoolean("requireDecimal", false),
        autoExpand = o.optBoolean("autoExpand", false),
        currency = o.optStr("currency"),
        maxTags = o.optInt("maxTags", 0),
        searchEnabled = o.optBoolean("searchEnabled", false),
        minSearch = o.optInt("minSearch", 0)
    )

    private fun parseValidate(o: JSONObject) = ValidateOptions(
        required = o.optBoolean("required", false),
        minLength = if (o.has("minLength") && !o.isNull("minLength")) o.optInt("minLength") else null,
        maxLength = if (o.has("maxLength") && !o.isNull("maxLength")) o.optInt("maxLength") else null,
        pattern = o.optStr("pattern"),
        min = o.opt("min")?.let { raw -> if (raw is Number) raw.toDouble() else raw.toString().toDoubleOrNull() },
        max = o.opt("max")?.let { raw -> if (raw is Number) raw.toDouble() else raw.toString().toDoubleOrNull() },
        custom = o.optStr("custom"),
        minWords = if (o.has("minWords") && !o.isNull("minWords")) o.optInt("minWords") else null,
        maxWords = if (o.has("maxWords") && !o.isNull("maxWords")) o.optInt("maxWords") else null,
        customMessage = o.optStr("customMessage")
    )

    private fun parseConditional(o: JSONObject) = ConditionalOptions(
        show = if (o.has("show") && !o.isNull("show")) o.optBoolean("show") else null,
        when_field = o.optStr("when"),
        eq = o.optStr("eq")
    )

    private fun parseComponentData(o: JSONObject) = ComponentData(
        values = o.optJSONArray("values")?.let { parseSelectOptions(it) },
        custom = o.optStr("custom"),
        url = o.optStr("url"),
        json = o.optStr("json")
    )

    private fun parseSelectOptions(arr: JSONArray): List<SelectOption> =
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                SelectOption(label = it.optString("label", ""), value = it.optString("value", ""))
            }
        }

    private fun parseColumns(arr: JSONArray): List<Column> =
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                Column(
                    components = o.optJSONArray("components")?.let { parseComponentsArray(it) } ?: emptyList(),
                    width = o.optInt("width", 6),
                    offset = o.optInt("offset", 0),
                    push = o.optInt("push", 0),
                    pull = o.optInt("pull", 0),
                    size = o.optString("size", "md")
                )
            }
        }

    private fun JSONObject.optStr(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "")
        return v.ifEmpty { null }
    }

    data class FormPage(val title: String, val components: List<FormComponent>)
}
