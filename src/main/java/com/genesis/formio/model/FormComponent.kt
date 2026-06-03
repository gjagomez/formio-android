package com.genesis.formio.model

data class FormComponent(
    val type: String = "",
    val key: String = "",
    val label: String = "",
    val input: Boolean = true,
    val validate: ValidateOptions? = null,
    val conditional: ConditionalOptions? = null,
    val customConditional: String? = null,
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
    val tag: String? = null,
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
    val minSearch: Int = 0
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
    val json: String? = null
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
