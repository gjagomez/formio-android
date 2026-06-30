package com.genesis.formio.ui.form

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.genesis.formio.engine.ConditionalEngine
import com.genesis.formio.model.FormComponent
import com.genesis.formio.model.Column
import com.genesis.formio.model.ComponentData
import com.genesis.formio.model.ConditionalOptions
import com.genesis.formio.model.DayFields
import com.genesis.formio.model.HeaderEntry
import com.genesis.formio.model.LogicAction
import com.genesis.formio.model.LogicRule
import com.genesis.formio.model.SelectOption
import com.genesis.formio.model.TableCell
import com.genesis.formio.model.ValidateOptions
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FormWizardViewModel(app: Application) : AndroidViewModel(app) {

    val pages = MutableLiveData<List<FormPage>>()
    val currentPageIndex = MutableLiveData(0)
    val displayType = MutableLiveData<String>("form")
    val hiddenPageIndices = MutableLiveData<Set<Int>>(emptySet())
    val formData = mutableMapOf<String, Any?>()
    val debugInfo = MutableLiveData<String?>()

    private val allPanels = mutableListOf<FormComponent>()
    // Keys of datagrid/editgrid components — their values are stored internally as JSON
    // strings and converted back to real arrays when building the result JSON.
    private val gridKeys = mutableSetOf<String>()

    private val gson = Gson()

    fun loadFromJson(schemaJson: String, prefillData: Map<String, Any?> = emptyMap()) {
        prefillData.forEach { (k, v) -> formData[k] = v }
        viewModelScope.launch {
            // Heavy JSON parsing runs off the main thread
            val parsed = withContext(Dispatchers.Default) { runParsing(schemaJson) }
            // Apply results on the main thread
            displayType.value = parsed.display
            gridKeys.clear(); gridKeys.addAll(parsed.gridKeySet)
            allPanels.clear(); allPanels.addAll(parsed.panels)
            if (!parsed.debugMsg.isNullOrBlank()) debugInfo.value = parsed.debugMsg
            pages.value = parsed.pages
            reevaluatePageVisibility()
        }
    }

    private data class ParseResult(
        val display: String,
        val pages: List<FormPage>,
        val panels: List<FormComponent>,
        val gridKeySet: Set<String>,
        val debugMsg: String?
    )

    /** Pure parsing — no ViewModel state written here, safe to run on any thread. */
    private fun runParsing(schemaJson: String): ParseResult {
        return try {
            val root = JSONObject(schemaJson)
            val topKeys = root.keys().asSequence().toList().joinToString(", ")
            val display = root.optString("display", "form")
            val componentsArray = root.optJSONArray("components")
                ?: return ParseResult(display, emptyList(), emptyList(), emptySet(),
                    "SIN components. Claves raíz: $topKeys")

            val allComponents = parseComponentsArray(componentsArray)
            val gridKeySet = gatherGridKeys(allComponents)

            if (allComponents.isEmpty()) {
                return ParseResult(display, emptyList(), emptyList(), gridKeySet,
                    "components vacío (len=${componentsArray.length()}). Claves raíz: $topKeys")
            }

            if (display == "wizard") {
                val wizardPages = allComponents.filter { it.type == "panel" || it.type == "well" }
                if (wizardPages.isNotEmpty()) {
                    ParseResult(
                        display = display,
                        pages = wizardPages.map { p ->
                            FormPage(title = p.title ?: p.label, components = p.components ?: emptyList())
                        },
                        panels = wizardPages,
                        gridKeySet = gridKeySet,
                        debugMsg = "OK wizard: ${wizardPages.size} panels"
                    )
                } else {
                    ParseResult(
                        display = display,
                        pages = listOf(FormPage(title = root.optString("title", ""), components = allComponents)),
                        panels = emptyList(),
                        gridKeySet = gridKeySet,
                        debugMsg = "OK wizard sin panels: ${allComponents.size} campos"
                    )
                }
            } else {
                ParseResult(
                    display = display,
                    pages = listOf(FormPage(title = root.optString("title", ""), components = allComponents)),
                    panels = emptyList(),
                    gridKeySet = gridKeySet,
                    debugMsg = "OK form: ${allComponents.size} campos"
                )
            }
        } catch (e: Exception) {
            ParseResult("form", emptyList(), emptyList(), emptySet(),
                "EXCEPCIÓN: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Pure recursive grid-key collector — returns a new Set, does not touch ViewModel state. */
    private fun gatherGridKeys(components: List<FormComponent>): Set<String> {
        val keys = mutableSetOf<String>()
        fun collect(comps: List<FormComponent>) {
            comps.forEach { comp ->
                if (comp.type == "datagrid" || comp.type == "editgrid") keys.add(comp.key)
                comp.components?.let { collect(it) }
                comp.columns?.forEach { collect(it.components) }
            }
        }
        collect(components)
        return keys
    }

    fun getFormDataJson(): String {
        val out = LinkedHashMap<String, Any?>(formData.size)
        formData.forEach { (key, value) ->
            out[key] = if (key in gridKeys && value is String) {
                try { gson.fromJson(value, List::class.java) } catch (_: Exception) { value }
            } else {
                value
            }
        }
        return gson.toJson(out)
    }

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
        conditionalJsonLogic = (o.opt("conditional") as? JSONObject)?.let { c ->
            c.opt("json")?.let { j -> if (j is JSONObject || j is JSONArray) j.toString() else null }
        },
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
        html = o.optStr("html"),
        tag = o.optStr("tag"),
        attrsJson = o.optJSONArray("attrs")?.toString(),
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
        minSearch = o.optInt("minSearch", 0),
        valueProperty = o.optStr("valueProperty"),
        dataType = o.optStr("dataType"),
        template = o.optStr("template"),
        selectValues = o.optStr("selectValues"),
        lazyLoad = o.optBoolean("lazyLoad", false),
        logic = o.optJSONArray("logic")?.let { parseLogic(it) },
        questions = o.optJSONArray("questions")?.let { parseSelectOptions(it) },
        dayFirst = o.optBoolean("dayFirst", false),
        dayFields = o.optJSONObject("fields")?.let { f ->
            DayFields(
                hideDay = f.optJSONObject("day")?.optBoolean("hide", false) ?: false,
                hideMonth = f.optJSONObject("month")?.optBoolean("hide", false) ?: false,
                hideYear = f.optJSONObject("year")?.optBoolean("hide", false) ?: false
            )
        },
        provider = o.optStr("provider"),
        collapsible = o.optBoolean("collapsible", false),
        collapsed = o.optBoolean("collapsed", false),
        tableRows = o.optJSONArray("rows")?.let { parseTableRows(it) },
        size = o.optString("size", "md"),
        theme = o.optString("theme", "primary"),
        scan = o.optBoolean("scan", false),
        uploadOnly = o.optBoolean("uploadOnly", false),
        selfie = o.optBoolean("selfie", false)
    )

    private fun parseTableRows(arr: JSONArray): List<List<TableCell>>? {
        val rows = (0 until arr.length()).mapNotNull { i ->
            val rowArr = arr.optJSONArray(i) ?: return@mapNotNull null
            (0 until rowArr.length()).mapNotNull { j ->
                rowArr.optJSONObject(j)?.let { cell ->
                    TableCell(
                        components = cell.optJSONArray("components")
                            ?.let { parseComponentsArray(it) } ?: emptyList()
                    )
                }
            }
        }
        return rows.ifEmpty { null }
    }

    private fun parseLogic(arr: JSONArray): List<LogicRule> =
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                val trigger = o.optJSONObject("trigger")
                LogicRule(
                    name = o.optString("name", ""),
                    triggerType = trigger?.optString("type", "") ?: "",
                    triggerSimple = trigger?.optJSONObject("simple")?.let { parseConditional(it) },
                    triggerJavascript = trigger?.optStr("javascript"),
                    triggerJson = trigger?.opt("json")?.let { j ->
                        if (j is JSONObject || j is JSONArray) j.toString() else null
                    },
                    actions = o.optJSONArray("actions")?.let { acts ->
                        (0 until acts.length()).mapNotNull { k ->
                            acts.optJSONObject(k)?.let { a ->
                                LogicAction(
                                    name = a.optString("name", ""),
                                    type = a.optString("type", ""),
                                    value = a.optStr("value"),
                                    property = a.optJSONObject("property")?.optString("value"),
                                    state = a.optStr("state") ?: a.optStr("text")
                                )
                            }
                        }
                    } ?: emptyList()
                )
            }
        }

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
        json = o.optStr("json"),
        headers = o.optJSONArray("headers")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    HeaderEntry(key = it.optString("key", ""), value = it.optString("value", ""))
                }
            }
        }
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

    fun reevaluatePageVisibility() {
        if (allPanels.isEmpty()) return
        val engine = ConditionalEngine()
        val hidden = allPanels.indices.filter { i -> !engine.shouldShow(allPanels[i], formData) }.toSet()
        hiddenPageIndices.postValue(hidden)
    }

    data class FormPage(val title: String, val components: List<FormComponent>)
}
