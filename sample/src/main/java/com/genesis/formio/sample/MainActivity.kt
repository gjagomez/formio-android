package com.genesis.formio.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.genesis.formio.FormioRenderer
import com.genesis.formio.sample.databinding.ActivityMainBinding
import com.google.gson.GsonBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 1. Register the launcher — receives the result when the form closes
    private val formLauncher = registerForActivityResult(FormioRenderer.Contract()) { result ->
        if (result == null) {
            binding.tvResult.text = "Cancelled — no data returned."
            return@registerForActivityResult
        }

        // 2. result.formData is a Map<String, Any?> with all field values.
        //    Show it as raw JSON to see the exact shape the library returns.
        val status = if (result.isFinal) "FINAL" else "DRAFT"
        val json = GsonBuilder().setPrettyPrinting().serializeNulls().create()
            .toJson(result.formData)
        binding.tvResult.text = "Status: $status\n\n$json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.fabOpen.setOnClickListener {
            // 3. Read the JSON schema (from raw resources, a string, an API, etc.)
            val schemaJson = resources.openRawResource(R.raw.example_schema)
                .bufferedReader()
                .readText()

            // 4. Launch the form
            formLauncher.launch(
                FormioRenderer.Input(
                    schemaJson  = schemaJson,
                    prefillData = emptyMap(),   // optional: pre-populate fields
                    title       = "Example Form"
                )
            )
        }
    }
}
