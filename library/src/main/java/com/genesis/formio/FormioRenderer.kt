package com.genesis.formio

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.genesis.formio.ui.form.FormWizardActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Public entry point for the formio-android renderer library.
 *
 * Usage with ActivityResultContract (recommended):
 *
 *   val formLauncher = registerForActivityResult(FormioRenderer.Contract()) { result ->
 *       result ?: return@registerForActivityResult
 *       val data: Map<String, Any?> = result.formData
 *       val isFinal: Boolean = result.isFinal
 *       // persist / process the result here
 *   }
 *   formLauncher.launch(FormioRenderer.Input(schemaJson, prefillData, "Form title"))
 */
object FormioRenderer {

    data class Input(
        val schemaJson: String,
        val prefillData: Map<String, Any?> = emptyMap(),
        val title: String = ""
    )

    data class Result(
        val formData: Map<String, Any?>,
        val isFinal: Boolean
    )

    class Contract : ActivityResultContract<Input, Result?>() {

        override fun createIntent(context: Context, input: Input): Intent {
            // Write schema to cache file to avoid TransactionTooLargeException
            val schemaFile = File(context.cacheDir, "formio_schema_pending.json")
            schemaFile.writeText(input.schemaJson)

            return Intent(context, FormWizardActivity::class.java).apply {
                putExtra(FormWizardActivity.EXTRA_SCHEMA_PATH, schemaFile.absolutePath)
                putExtra(FormWizardActivity.EXTRA_TITLE, input.title)
                if (input.prefillData.isNotEmpty()) {
                    putExtra(FormWizardActivity.EXTRA_PREFILL_JSON, Gson().toJson(input.prefillData))
                }
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Result? {
            if (resultCode != Activity.RESULT_OK || intent == null) return null
            val dataJson = intent.getStringExtra(FormWizardActivity.EXTRA_RESULT_DATA) ?: return null
            val isFinal = intent.getBooleanExtra(FormWizardActivity.EXTRA_RESULT_IS_FINAL, false)
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val data: Map<String, Any?> = Gson().fromJson(dataJson, type) ?: emptyMap()
            return Result(data, isFinal)
        }
    }
}
