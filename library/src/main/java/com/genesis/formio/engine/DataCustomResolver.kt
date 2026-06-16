package com.genesis.formio.engine

import com.genesis.formio.model.SelectOption

class DataCustomResolver(private val bridge: AppFunctionBridge) {

    fun resolve(script: String, formData: Map<String, Any?>): List<SelectOption> {
        return when {
            script.contains("app.getActividad") -> {
                val arg = extractArg(script, "getActividad", formData)
                bridge.getActividad(arg)
            }
            script.contains("app.getSector") -> bridge.getSector()
            script.contains("app.filterDepa") -> bridge.filterDepa()
            script.contains("app.filterMuni") -> {
                val arg = extractArg(script, "filterMuni", formData)
                bridge.filterMuni(arg)
            }
            script.contains("app.filterAlde") -> {
                val args = extractArgs(script, "filterAlde", formData)
                bridge.filterAlde(args.getOrElse(0) { "" }, args.getOrElse(1) { "" })
            }
            script.contains("app.fMuni") -> bridge.fMuni()
            script.contains("app.getProductos") -> bridge.getProductos()
            script.contains("app.getSubproductos") -> {
                val arg = extractArg(script, "getSubproductos", formData)
                bridge.getSubproductos(arg)
            }
            else -> emptyList()
        }
    }

    private fun extractArg(script: String, funcName: String, formData: Map<String, Any?>): String {
        val match = Regex("""$funcName\(([^)]+)\)""").find(script) ?: return ""
        val rawArg = match.groupValues[1].trim()
        return resolveArgValue(rawArg, formData)
    }

    private fun extractArgs(script: String, funcName: String, formData: Map<String, Any?>): List<String> {
        val match = Regex("""$funcName\(([^)]+)\)""").find(script) ?: return emptyList()
        return match.groupValues[1].split(",").map { resolveArgValue(it.trim(), formData) }
    }

    private fun resolveArgValue(raw: String, formData: Map<String, Any?>): String {
        val dataRef = Regex("""(?:data|row)\.(\w+)""").find(raw)
        if (dataRef != null) {
            return formData[dataRef.groupValues[1]]?.toString() ?: ""
        }
        return raw.trim().trim('"').trim('\'')
    }
}
