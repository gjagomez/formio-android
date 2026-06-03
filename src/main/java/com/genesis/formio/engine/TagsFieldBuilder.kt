package com.genesis.formio.engine

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class TagsFieldBuilder(private val context: Context) {

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

        // ── Label ──────────────────────────────────────────────────────────────
        val hintText: CharSequence = if (isRequired) {
            SpannableString("${component.label} *").also {
                it.setSpan(
                    ForegroundColorSpan(context.getColor(R.color.error_color)),
                    it.length - 1, it.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else component.label

        container.addView(TextView(context).apply {
            text = hintText
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (10 * dp).toInt())
        })

        // ── Card wrapper ───────────────────────────────────────────────────────
        val card = MaterialCardView(context).apply {
            setCardBackgroundColor(context.getColor(R.color.bg_elevated))
            radius = (12 * dp)
            cardElevation = 0f
            strokeWidth = (1.5f * dp).toInt()
            strokeColor = context.getColor(R.color.input_border)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = (12 * dp).toInt()
            setPadding(p, p, p, p)
        }

        // ── ChipGroup ──────────────────────────────────────────────────────────
        val chipGroup = ChipGroup(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        inner.addView(chipGroup)

        // ── State ──────────────────────────────────────────────────────────────
        val tags = mutableListOf<String>()
        val maxTags = component.maxTags.takeIf { it > 0 } ?: Int.MAX_VALUE

        fun saveTags() {
            val value = tags.joinToString(",")
            formData[component.key] = value
            onChange(component.key, value)
        }

        fun addChip(text: String) {
            val chip = Chip(context).apply {
                this.text = text
                isCloseIconVisible = !component.disabled
                setTextColor(context.getColor(R.color.text_primary))
                setChipBackgroundColorResource(R.color.bg_card)
                chipStrokeWidth = (1f * dp)
                setChipStrokeColorResource(R.color.input_border)
                setOnCloseIconClickListener {
                    tags.remove(text)
                    chipGroup.removeView(this)
                    saveTags()
                }
            }
            chipGroup.addView(chip)
        }

        // Restore existing tags
        formData[component.key]?.toString()?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.forEach { tag -> tags.add(tag); addChip(tag) }

        // ── Input to add new tag ───────────────────────────────────────────────
        if (!component.disabled) {
            val r = 12 * dp
            val til = TextInputLayout(
                context, null,
                com.google.android.material.R.attr.textInputOutlinedStyle
            ).apply {
                hint = "Agregar etiqueta..."
                setBoxBackgroundColorResource(R.color.bg_elevated)
                setBoxCornerRadii(r, r, r, r)
                boxStrokeWidth = (1.5f * dp).toInt()
                boxStrokeColor = context.getColor(R.color.input_border)
                hintTextColor = context.getColorStateList(R.color.text_hint_selector)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (8 * dp).toInt() }
            }

            val editText = TextInputEditText(til.context).apply {
                imeOptions = EditorInfo.IME_ACTION_DONE
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 14f
            }

            editText.setOnFocusChangeListener { _, hasFocus ->
                til.boxStrokeColor = context.getColor(
                    if (hasFocus) R.color.accent_green else R.color.input_border
                )
            }

            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val tag = editText.text?.toString()?.trim() ?: ""
                    if (tag.isNotBlank() && !tags.contains(tag) && tags.size < maxTags) {
                        tags.add(tag)
                        addChip(tag)
                        saveTags()
                        editText.setText("")
                    }
                    true
                } else false
            }

            til.addView(editText)
            inner.addView(til)
        }

        card.addView(inner)
        container.addView(card)
        return container
    }
}
