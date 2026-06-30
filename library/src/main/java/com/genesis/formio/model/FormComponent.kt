package com.genesis.formio.model

data class FormComponent(
    val type: String = "",
    val key: String = "",
    val label: String = "",
    val input: Boolean = true,
    val validate: ValidateOptions? = null,
    val conditional: ConditionalOptions? = null,
    val customConditional: String? = null,
    val conditionalJsonLogic: String? = null,
    val calculateValue: String? = null,
    val defaultValue: Any? = null,
    val data: ComponentData? = null,
    val values: List<SelectOption>? = null,
    val components: List<FormComponent>? = null,
    val disabled: Boolean = false,
    val rows: Int? = null,
    val inputMask: String? = null,
    val dataSrc: String? = null,
    val title: String? = null,
    val content: String? = null,
    val html: String? = null,
    val tag: String? = null,
    val attrsJson: String? = null,
    val columns: List<Column>? = null,
    val refreshOn: String? = null,
    val clearOnRefresh: Boolean = false,
    val action: String? = null,
    val custom: String? = null,
    val inputType: String? = null,
    val delimiter: Boolean = false,
    val format: String? = null,
    val multiple: Boolean = false,
    val tableView: Boolean = false,
    val tree: Boolean = false,
    val reorder: Boolean = false,
    val addAnotherPosition: String? = null,
    val placeholder: String? = null,
    val validateOn: String? = null,
    val showCharCount: Boolean = false,
    val showWordCount: Boolean = false,
    val errorLabel: String? = null,
    val case: String? = null,
    val requireDecimal: Boolean = false,
    val autoExpand: Boolean = false,
    val currency: String? = null,
    val maxTags: Int = 0,
    val searchEnabled: Boolean = false,
    val minSearch: Int = 0,
    val valueProperty: String? = null,
    val dataType: String? = null,
    val template: String? = null,
    val selectValues: String? = null,
    val lazyLoad: Boolean = false,
    val logic: List<LogicRule>? = null,
    val questions: List<SelectOption>? = null,
    val dayFirst: Boolean = false,
    val dayFields: DayFields? = null,
    val provider: String? = null,
    val collapsible: Boolean = false,
    val collapsed: Boolean = false,
    val tableRows: List<List<TableCell>>? = null,
    val size: String = "md",
    val theme: String = "primary",
    val scan: Boolean = false,
    val uploadOnly: Boolean = false,
    val selfie: Boolean = false
)

/** One cell of a `table` layout component. */
data class TableCell(
    val components: List<FormComponent> = emptyList()
)

/** Visibility of the day/month/year inputs of a `day` component. */
data class DayFields(
    val hideDay: Boolean = false,
    val hideMonth: Boolean = false,
    val hideYear: Boolean = false
)

/** Form.io Advanced Logic rule (the "Logic" tab in the form designer). */
data class LogicRule(
    val name: String = "",
    val triggerType: String = "",            // "simple" | "javascript" | "json"
    val triggerSimple: ConditionalOptions? = null,
    val triggerJavascript: String? = null,
    val triggerJson: String? = null,
    val actions: List<LogicAction> = emptyList()
)

data class LogicAction(
    val name: String = "",
    val type: String = "",                   // "value" | "property"
    val value: String? = null,               // JS snippet for type "value" (e.g. "value = 'snob';")
    val property: String? = null,            // property path for type "property" (e.g. "disabled")
    val state: String? = null                // new state for type "property"
)

data class ValidateOptions(
    val required: Boolean = false,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val custom: String? = null,
    val minWords: Int? = null,
    val maxWords: Int? = null,
    val customMessage: String? = null
)

data class ConditionalOptions(
    val show: Boolean? = null,
    val when_field: String? = null,
    val eq: String? = null
)

data class ComponentData(
    val values: List<SelectOption>? = null,
    val custom: String? = null,
    val url: String? = null,
    val json: String? = null,
    val headers: List<HeaderEntry>? = null
)

data class HeaderEntry(
    val key: String = "",
    val value: String = ""
)

data class SelectOption(
    val label: String = "",
    val value: String = ""
)

data class Column(
    val components: List<FormComponent> = emptyList(),
    val width: Int = 6,
    val offset: Int = 0,
    val push: Int = 0,
    val pull: Int = 0,
    val size: String = "md"
)
