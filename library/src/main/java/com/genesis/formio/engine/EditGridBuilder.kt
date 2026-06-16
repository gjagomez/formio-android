package com.genesis.formio.engine

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject

class EditGridBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density
        val accentGreen = context.getColor(R.color.accent_green)
        val errorColor = context.getColor(R.color.error_color)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        // ── Header: label + count badge ────────────────────────────────────────
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * dp).toInt() }
        }
        headerRow.addView(TextView(context).apply {
            text = component.label
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val countBadge = TextView(context).apply {
            textSize = 11f
            setTextColor(context.getColor(R.color.text_on_accent))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * dp
                setColor(accentGreen)
            }
            setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        }
        headerRow.addView(countBadge)
        container.addView(headerRow)

        // ── Empty state ────────────────────────────────────────────────────────
        val emptyCard = MaterialCardView(context).apply {
            setCardBackgroundColor(context.getColor(R.color.bg_card))
            radius = 12f * dp
            cardElevation = 0f
            strokeWidth = (1 * dp).toInt()
            strokeColor = context.getColor(R.color.input_border)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * dp).toInt() }
        }
        val emptyInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt())
        }
        val iconBg = Color.argb(50, Color.red(accentGreen), Color.green(accentGreen), Color.blue(accentGreen))
        val iconCircle = FrameLayout(context).apply {
            val size = (44 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { bottomMargin = (10 * dp).toInt() }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(iconBg) }
        }
        iconCircle.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_edit); setColorFilter(accentGreen)
            val p = (10 * dp).toInt(); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        })
        emptyInner.addView(iconCircle)
        emptyInner.addView(TextView(context).apply {
            text = "Sin registros"
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
        })
        emptyInner.addView(TextView(context).apply {
            text = "Toca \"+ Agregar\" para añadir el primer registro"
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 12f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * dp).toInt() }
        })
        emptyCard.addView(emptyInner)
        container.addView(emptyCard)

        // ── Rows container ─────────────────────────────────────────────────────
        val rowsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        container.addView(rowsContainer)

        // ── Per-row state lists ────────────────────────────────────────────────
        val rowMaps = mutableListOf<MutableMap<String, Any?>>()
        val rowCards = mutableListOf<MaterialCardView>()
        val summaryViews = mutableListOf<TextView>()
        val rowNumLabels = mutableListOf<TextView>()
        val collapsedViews = mutableListOf<View>()
        val expandedViews = mutableListOf<View>()

        // ── Helpers ────────────────────────────────────────────────────────────
        fun syncToFormData() {
            val arr = JSONArray()
            rowMaps.forEach { map ->
                val obj = JSONObject()
                map.forEach { (k, v) -> obj.put(k, FieldValueUtil.toJsonValue(v)) }
                arr.put(obj)
            }
            formData[component.key] = arr.toString()
            onChange(component.key, arr.toString())
        }

        fun buildSummary(map: Map<String, Any?>): String =
            component.components
                ?.filter { it.type != "hidden" && it.type != "button" }
                ?.take(3)
                ?.mapNotNull { c -> FieldValueUtil.labelOf(map[c.key])?.takeIf { it.isNotBlank() } }
                ?.joinToString("  ·  ")
                ?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.formio_editgrid_sin_datos)

        fun updateState() {
            countBadge.text = "${rowMaps.size}"
            emptyCard.visibility = if (rowMaps.isEmpty()) View.VISIBLE else View.GONE
            rowNumLabels.forEachIndexed { i, tv -> tv.text = "Registro ${i + 1}" }
        }

        fun collapseRow(card: MaterialCardView, collapsed: View, expanded: View) {
            collapsed.visibility = View.VISIBLE
            expanded.visibility = View.GONE
            card.strokeColor = context.getColor(R.color.input_border)
        }

        fun expandRow(card: MaterialCardView, collapsed: View, expanded: View) {
            collapsed.visibility = View.GONE
            expanded.visibility = View.VISIBLE
            card.strokeColor = accentGreen
        }

        // ── Add row ────────────────────────────────────────────────────────────
        fun addRow(initial: JSONObject = JSONObject(), startExpanded: Boolean = true) {
            val rowMap = mutableMapOf<String, Any?>()
            component.components?.forEach { c -> rowMap[c.key] = initial.opt(c.key) }
            rowMaps.add(rowMap)

            val tempMap = rowMap.toMutableMap()
            var isSaved = !startExpanded

            // Card
            val rowCard = MaterialCardView(context).apply {
                setCardBackgroundColor(context.getColor(R.color.bg_card))
                radius = 14f * dp
                cardElevation = 0f
                strokeWidth = (1.5f * dp).toInt()
                strokeColor = context.getColor(R.color.input_border)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * dp).toInt() }
            }
            rowCards.add(rowCard)
            val cardInner = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            rowCard.addView(cardInner)

            // ── Collapsed view ─────────────────────────────────────────────────
            val collapsedView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            }

            // Accent dot
            collapsedView.addView(View(context).apply {
                val s = (8 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = (10 * dp).toInt() }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accentGreen) }
            })

            // Text block: row number + summary
            val textBlock = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val rowNumLabel = TextView(context).apply {
                textSize = 13f
                setTextColor(context.getColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            rowNumLabels.add(rowNumLabel)
            val summaryText = TextView(context).apply {
                textSize = 12f
                setTextColor(context.getColor(R.color.text_secondary))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (2 * dp).toInt() }
            }
            summaryViews.add(summaryText)
            textBlock.addView(rowNumLabel)
            textBlock.addView(summaryText)
            collapsedView.addView(textBlock)

            // Edit button
            val editBtn = ImageButton(context).apply {
                setImageResource(R.drawable.ic_edit)
                setColorFilter(accentGreen)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(30, Color.red(accentGreen), Color.green(accentGreen), Color.blue(accentGreen)))
                }
                val s = (36 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = (6 * dp).toInt() }
                val p = (8 * dp).toInt(); setPadding(p, p, p, p)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            collapsedView.addView(editBtn)

            // Delete button
            val deleteBtn = ImageButton(context).apply {
                setImageResource(R.drawable.ic_delete)
                setColorFilter(errorColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(30, Color.red(errorColor), Color.green(errorColor), Color.blue(errorColor)))
                }
                val s = (36 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s)
                val p = (8 * dp).toInt(); setPadding(p, p, p, p)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            collapsedView.addView(deleteBtn)
            collapsedViews.add(collapsedView)
            cardInner.addView(collapsedView)

            // ── Expanded view ──────────────────────────────────────────────────
            val expandedView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }

            // Expanded header with green tint
            val expandedHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(
                    Color.argb(35, Color.red(accentGreen), Color.green(accentGreen), Color.blue(accentGreen))
                )
                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            }
            expandedHeader.addView(View(context).apply {
                val s = (8 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = (10 * dp).toInt() }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accentGreen) }
            })
            expandedHeader.addView(TextView(context).apply {
                text = context.getString(R.string.formio_editgrid_editando)
                setTextColor(accentGreen)
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val closeHeaderBtn = ImageButton(context).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(context.getColor(R.color.text_secondary))
                background = null
                val s = (32 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s)
                val p = (6 * dp).toInt(); setPadding(p, p, p, p)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            expandedHeader.addView(closeHeaderBtn)
            expandedView.addView(expandedHeader)

            // Divider
            expandedView.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                setBackgroundColor(context.getColor(R.color.input_border))
            })

            // Fields
            val fieldsLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val h = (14 * dp).toInt()
                setPadding(h, (8 * dp).toInt(), h, (4 * dp).toInt())
            }
            RowFieldsRenderer.render(
                FieldFactory(context),
                component.components ?: emptyList(),
                tempMap,
                fieldsLayout
            ) { _, _ -> }
            expandedView.addView(fieldsLayout)

            // Bottom divider
            expandedView.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                ).apply { marginStart = (14 * dp).toInt(); marginEnd = (14 * dp).toInt() }
                setBackgroundColor(context.getColor(R.color.input_border))
            })

            // Save / Cancel buttons
            val actionsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            }
            val cancelBtn = MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = context.getString(R.string.formio_editgrid_cancelar)
                setTextColor(context.getColor(R.color.text_secondary))
                strokeColor = ColorStateList.valueOf(context.getColor(R.color.input_border))
                cornerRadius = (8 * dp).toInt()
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (40 * dp).toInt()
                ).apply { marginEnd = (8 * dp).toInt() }
            }
            actionsRow.addView(cancelBtn)
            val saveBtn = MaterialButton(context).apply {
                text = context.getString(R.string.formio_editgrid_guardar)
                setTextColor(context.getColor(R.color.text_on_accent))
                backgroundTintList = ColorStateList.valueOf(accentGreen)
                cornerRadius = (8 * dp).toInt()
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (40 * dp).toInt()
                )
            }
            actionsRow.addView(saveBtn)
            expandedView.addView(actionsRow)
            expandedViews.add(expandedView)
            cardInner.addView(expandedView)

            rowsContainer.addView(rowCard)

            // ── Click handlers ─────────────────────────────────────────────────
            editBtn.setOnClickListener {
                val idx = rowCards.indexOf(rowCard)
                tempMap.clear(); tempMap.putAll(rowMaps[idx])
                expandRow(rowCard, collapsedView, expandedView)
            }

            saveBtn.setOnClickListener {
                val idx = rowCards.indexOf(rowCard)
                rowMaps[idx].clear(); rowMaps[idx].putAll(tempMap)
                summaryViews[idx].text = buildSummary(rowMaps[idx])
                isSaved = true
                collapseRow(rowCard, collapsedView, expandedView)
                syncToFormData()
                updateState()
            }

            fun doCancel() {
                if (!isSaved) {
                    val idx = rowCards.indexOf(rowCard)
                    rowMaps.removeAt(idx); rowCards.removeAt(idx)
                    summaryViews.removeAt(idx); rowNumLabels.removeAt(idx)
                    collapsedViews.removeAt(idx); expandedViews.removeAt(idx)
                    rowsContainer.removeView(rowCard)
                    syncToFormData(); updateState()
                } else {
                    collapseRow(rowCard, collapsedView, expandedView)
                }
            }

            cancelBtn.setOnClickListener { doCancel() }
            closeHeaderBtn.setOnClickListener { doCancel() }

            deleteBtn.setOnClickListener {
                val idx = rowCards.indexOf(rowCard)
                rowMaps.removeAt(idx); rowCards.removeAt(idx)
                summaryViews.removeAt(idx); rowNumLabels.removeAt(idx)
                collapsedViews.removeAt(idx); expandedViews.removeAt(idx)
                rowsContainer.removeView(rowCard)
                syncToFormData(); updateState()
            }

            // Initial state
            summaryText.text = buildSummary(rowMap)
            if (startExpanded) expandRow(rowCard, collapsedView, expandedView)
            else collapseRow(rowCard, collapsedView, expandedView)

            syncToFormData()
            updateState()
        }

        // ── Restore existing data ──────────────────────────────────────────────
        val existing = try {
            JSONArray(formData[component.key]?.toString() ?: "[]")
        } catch (_: Exception) { JSONArray() }
        for (i in 0 until existing.length()) {
            addRow(existing.optJSONObject(i) ?: JSONObject(), startExpanded = false)
        }

        // ── Add button ─────────────────────────────────────────────────────────
        val addBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * dp
                setColor(Color.TRANSPARENT)
                setStroke((1.5f * dp).toInt(), accentGreen)
            }
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * dp).toInt() }
            isClickable = true; isFocusable = true
            val ripple = android.util.TypedValue().also {
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
            }
            foreground = context.getDrawable(ripple.resourceId)
        }
        addBtn.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_add); setColorFilter(accentGreen)
            val s = (18 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = (8 * dp).toInt() }
        })
        addBtn.addView(TextView(context).apply {
            text = context.getString(R.string.formio_btn_agregar_fila)
            setTextColor(accentGreen); textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        addBtn.setOnClickListener { addRow() }
        container.addView(addBtn)

        updateState()
        return container
    }
}
