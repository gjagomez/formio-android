package com.genesis.formio.engine

import android.content.Context
import com.genesis.formio.model.SelectOption
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class AppFunctionBridge(private val context: Context) {
    private val catalogos: JSONObject by lazy { loadCatalogos() }

    private fun loadCatalogos(): JSONObject {
        return try {
            val json = context.assets.open("catalogos.json")
                .bufferedReader().use { it.readText() }
            JSONObject(json)
        } catch (e: IOException) {
            JSONObject()
        }
    }

    fun getActividad(sectorId: String): List<SelectOption> {
        val arr = catalogos.optJSONArray("actividades") ?: return emptyList()
        return (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .filter { it.optString("sectorId") == sectorId }
            .map { SelectOption(it.optString("label"), it.optString("value")) }
    }

    fun getSector(): List<SelectOption> =
        jsonArrayToOptions(catalogos.optJSONArray("sectores"))

    fun filterDepa(): List<SelectOption> =
        jsonArrayToOptions(catalogos.optJSONArray("departamentos"))

    fun filterMuni(depaId: String): List<SelectOption> {
        val arr = catalogos.optJSONArray("municipios") ?: return emptyList()
        return (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .filter { it.optString("departamentoId") == depaId }
            .map { SelectOption(it.optString("label"), it.optString("value")) }
    }

    fun filterAlde(depaId: String, muniId: String): List<SelectOption> {
        val arr = catalogos.optJSONArray("aldeas") ?: return emptyList()
        return (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .filter {
                it.optString("departamentoId") == depaId &&
                it.optString("municipioId") == muniId
            }
            .map { SelectOption(it.optString("label"), it.optString("value")) }
    }

    fun fMuni(): List<SelectOption> {
        val depas = catalogos.optJSONObject("departamentoMap") ?: JSONObject()
        val arr = catalogos.optJSONArray("municipios") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { obj ->
            val depaName = depas.optString(obj.optString("departamentoId"), "")
            val muniName = obj.optString("label")
            SelectOption("$depaName - $muniName", obj.optString("value"))
        }
    }

    fun getProductos(): List<SelectOption> =
        jsonArrayToOptions(catalogos.optJSONArray("productos"))

    fun getSubproductos(productoId: String): List<SelectOption> {
        val arr = catalogos.optJSONArray("subproductos") ?: return emptyList()
        return (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .filter { it.optString("productoId") == productoId }
            .map { SelectOption(it.optString("label"), it.optString("value")) }
    }

    private fun jsonArrayToOptions(arr: JSONArray?): List<SelectOption> {
        arr ?: return emptyList()
        return (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let {
                SelectOption(it.optString("label"), it.optString("value"))
            }
        }
    }
}
