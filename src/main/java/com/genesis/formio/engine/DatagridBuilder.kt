package com.genesis.formio.engine

import android.content.Context
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
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject

class DatagridBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        // ── Header row: label + row counter badge ──────────────────────────────
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
                cornerRadius = (10 * dp)
                setColor(context.getColor(R.color.accent_green))
            }
            setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        headerRow.addView(countBadge)
        container.addView(headerRow)

        // ── Empty state ────────────────────────────────────────────────────────
        val emptyCard = MaterialCardView(context).apply {
            setCardBackgroundColor(context.getColor(R.color.bg_card))
            radius = (12 * dp)
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
            setPadding((16 * dp).toInt(), (28 * dp).toInt(), (16 * dp).toInt(), (28 * dp).toInt())
        }
        val accentGreen = context.getColor(R.color.accent_green)
        val iconColor = Color.argb(60, Color.red(accentGreen), Color.green(accentGreen), Color.blue(accentGreen))
        val iconCircle = FrameLayout(context).apply {
            val size = (48 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = (12 * dp).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(iconColor)
            }
        }
        iconCircle.addView(ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageResource(R.drawable.ic_add)
            setColorFilter(accentGreen)
            val p = (12 * dp).toInt(); setPadding(p, p, p, p)
            scaleType = ImageView.ScaleType.FIT_CENTER
        })
        emptyInner.addView(iconCircle)
        emptyInner.addView(TextView(context).apply {
            text = "Sin registros"
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        emptyInner.addView(TextView(context).apply {
            text = "Toca \"+ Agregar\" para añadir el primer registro"
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * dp).toInt() }
        })
        emptyCard.addView(emptyInner)
        container.addView(emptyCard)

        // ── Rows container ─────────────────────────────────────────────────────
        val rowsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(rowsContainer)

        // ── State ──────────────────────────────────────────────────────────────
        val rowMaps = mutableListOf<MutableMap<String, Any?>>()
        val rowViews = mutableListOf<View>()
        val rowNumberLabels = mutableListOf<TextView>()

        fun syncToFormData() {
            val arr = JSONArray()
            rowMaps.forEach { map ->
                val obj = JSONObject()
                map.forEach { (k, v) -> obj.put(k, v?.toString() ?: "") }
                arr.put(obj)
            }
            formData[component.key] = arr.toString()
            onChange(component.key, arr.toString())
        }

        fun updateCountBadge() {
            val n = rowMaps.size
            countBadge.text = "$n"
            emptyCard.visibility = if (n == 0) View.VISIBLE else View.GONE
            rowNumberLabels.forEachIndexed { i, tv -> tv.text = "Registro ${i + 1}" }
        }

        fun addRow(rowData: JSONObject = JSONObject()) {
            val rowMap = mutableMapOf<String, Any?>()
            component.components?.forEach { child -> rowMap[child.key] = rowData.opt(child.key) }
            rowMaps.add(rowMap)

            // ── Row card ───────────────────────────────────────────────────────
            val rowCard = MaterialCardView(context).apply {
                setCardBackgroundColor(context.getColor(R.color.bg_card))
                radius = (14 * dp)
                cardElevation = 0f
                strokeWidth = (1 * dp).toInt()
                strokeColor = context.getColor(R.color.input_border)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * dp).toInt() }
            }

            val rowInner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            // ── Row header ─────────────────────────────────────────────────────
            val rowHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(context.getColor(R.color.bg_elevated))
                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
            }

            // Dot indicator
            rowHeader.addView(View(context).apply {
                val size = (8 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (10 * dp).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(context.getColor(R.color.accent_green))
                }
            })

            // Row number label
            val rowNumLabel = TextView(context).apply {
                textSize = 13f
                setTextColor(context.getColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            rowHeader.addView(rowNumLabel)
            rowNumberLabels.add(rowNumLabel)

            // Delete button
            val deleteBtn = ImageButton(context).apply {
                setImageResource(R.drawable.ic_delete)
                setColorFilter(context.getColor(R.color.stat_new))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(40, 232, 62, 140))
                }
                val btnSize = (36 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
                val p = (8 * dp).toInt(); setPadding(p, p, p, p)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            rowHeader.addView(deleteBtn)
            rowInner.addView(rowHeader)

            // ── Divider ────────────────────────────────────────────────────────
            rowInner.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                )
                setBackgroundColor(context.getColor(R.color.input_border))
            })

            // ── Fields ─────────────────────────────────────────────────────────
            val fieldsLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val p = (14 * dp).toInt()
                setPadding(p, (8 * dp).toInt(), p, (4 * dp).toInt())
            }

            val fieldFactory = FieldFactory(context)
            component.components?.forEach { child ->
                if (child.type != "hidden" && child.type != "button") {
                    fieldFactory.createView(child, rowMap) { key, value ->
                        rowMap[key] = value
                        syncToFormData()
                    }?.let { fieldsLayout.addView(it) }
                }
            }
            rowInner.addView(fieldsLayout)
            rowCard.addView(rowInner)
            rowsContainer.addView(rowCard)
            rowViews.add(rowCard)

            // Delete logic
            deleteBtn.setOnClickListener {
                val idx = rowViews.indexOf(rowCard)
                if (idx >= 0) {
                    rowMaps.removeAt(idx)
                    rowViews.removeAt(idx)
                    rowNumberLabels.removeAt(idx)
                    rowsContainer.removeView(rowCard)
                    syncToFormData()
                    updateCountBadge()
                }
            }

            syncToFormData()
            updateCountBadge()
        }

        // ── Restore existing rows ──────────────────────────────────────────────
        val existingData: JSONArray = try {
            JSONArray(formData[component.key]?.toString() ?: "[]")
        } catch (_: Exception) { JSONArray() }
        for (i in 0 until existingData.length()) {
            addRow(existingData.optJSONObject(i) ?: JSONObject())
        }

        // ── Add button ─────────────────────────────────────────────────────────
        val addBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (12 * dp)
                setColor(Color.TRANSPARENT)
                setStroke((1.5f * dp).toInt(), context.getColor(R.color.accent_green))
            }
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * dp).toInt() }
            isClickable = true
            isFocusable = true
            val ripple = android.util.TypedValue().also {
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
            }
            foreground = context.getDrawable(ripple.resourceId)
        }

        addBtn.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
            setColorFilter(context.getColor(R.color.accent_green))
            val size = (18 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (8 * dp).toInt()
            }
        })
        addBtn.addView(TextView(context).apply {
            text = context.getString(R.string.formio_btn_agregar_fila)
            setTextColor(context.getColor(R.color.accent_green))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        addBtn.setOnClickListener { addRow() }
        container.addView(addBtn)

        updateCountBadge()
        return container
    }
}
