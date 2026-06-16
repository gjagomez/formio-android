package com.genesis.formio.engine

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.genesis.formio.model.SelectOption
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView

class SelectFieldBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        // ── Label ──────────────────────────────────────────────────────────────
        val isRequired = component.validate?.required == true
        container.addView(TextView(context).apply {
            text = if (isRequired) {
                SpannableString("${component.label} *").apply {
                    setSpan(
                        ForegroundColorSpan(context.getColor(R.color.error_color)),
                        length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else {
                SpannableString(component.label)
            }
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (10 * dp).toInt())
        })

        // ── Options ────────────────────────────────────────────────────────────
        val isUrlSrc = component.dataSrc == "url"
        var options: List<SelectOption> = when (component.dataSrc) {
            "values" -> component.data?.values ?: component.values ?: emptyList()
            "custom" -> DataCustomResolver(AppFunctionBridge(context)).resolve(
                component.data?.custom ?: "", formData
            )
            "url" -> UrlSelectLoader.cached(component, formData) ?: emptyList()
            "json" -> UrlSelectLoader.parseStatic(component.data?.json, component)
            else -> component.data?.values ?: component.values ?: emptyList()
        }

        val currentValue = FieldValueUtil.valueOf(formData[component.key])
            ?: FieldValueUtil.valueOf(component.defaultValue) ?: ""
        val currentLabel = options.find { it.value == currentValue }?.label
            ?: if (isUrlSrc) currentValue else ""

        // ── Card ───────────────────────────────────────────────────────────────
        val card = MaterialCardView(context).apply {
            tag = "__card__${component.key}"
            setCardBackgroundColor(context.getColor(R.color.bg_elevated))
            radius = (12 * dp)
            cardElevation = 0f
            strokeWidth = (1.5f * dp).toInt()
            strokeColor = context.getColor(R.color.input_border)
            isClickable = !component.disabled
            isFocusable = !component.disabled
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val pad = (14 * dp).toInt()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val displayText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 15f
            if (currentLabel.isNotBlank()) {
                text = currentLabel
                setTextColor(context.getColor(R.color.text_primary))
            } else {
                text = component.placeholder?.takeIf { it.isNotBlank() } ?: ""
                setTextColor(context.getColor(R.color.text_hint))
            }
        }

        val arrowView = TextView(context).apply {
            text = "▾"
            textSize = 18f
            setTextColor(context.getColor(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * dp).toInt() }
        }

        row.addView(displayText)
        row.addView(arrowView)
        card.addView(row)
        container.addView(card)

        // Error label
        container.addView(TextView(context).apply {
            tag = "__err__${component.key}"
            setTextColor(context.getColor(R.color.error_color))
            textSize = 12f
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), 0, 0)
            visibility = View.GONE
        })

        // ── Remote options (dataSrc == "url") ──────────────────────────────────
        var remoteLoaded = !isUrlSrc || options.isNotEmpty()
        var remoteLoading = false

        fun refreshDisplayLabel() {
            val value = FieldValueUtil.valueOf(formData[component.key]) ?: currentValue
            val label = options.find { it.value == value }?.label
            if (!label.isNullOrBlank()) {
                displayText.text = label
                displayText.setTextColor(context.getColor(R.color.text_primary))
            } else if (displayText.text.toString() == context.getString(R.string.formio_loading_options)) {
                displayText.text = component.placeholder?.takeIf { it.isNotBlank() } ?: ""
                displayText.setTextColor(context.getColor(R.color.text_hint))
            }
        }

        fun loadRemoteOptions(onReady: (() -> Unit)? = null) {
            if (remoteLoading) return
            remoteLoading = true
            UrlSelectLoader.load(component, formData) { result ->
                remoteLoading = false
                remoteLoaded = true
                if (result != null) options = result
                refreshDisplayLabel()
                onReady?.invoke()
            }
        }

        // Eager load unless the schema asks for lazyLoad (fetch on first tap)
        if (isUrlSrc && !remoteLoaded && !component.lazyLoad) loadRemoteOptions()

        if (component.disabled) return container

        // ── Tap handler ────────────────────────────────────────────────────────
        fun onSelected(selected: SelectOption) {
            displayText.text = selected.label
            displayText.setTextColor(context.getColor(R.color.text_primary))
            // dataType "object" keeps both label and value in the submission
            val stored: Any? = if (component.dataType == "object") {
                linkedMapOf("label" to selected.label, "value" to selected.value)
            } else {
                selected.value
            }
            formData[component.key] = stored
            onChange(component.key, stored)
            card.strokeColor = context.getColor(R.color.input_border)
            container.findViewWithTag<TextView>("__err__${component.key}")?.visibility = View.GONE
        }

        fun openPicker() {
            // Remote lists tend to be long — give them the searchable sheet too
            if (component.searchEnabled || (isUrlSrc && options.size > 10)) {
                showSearchSheet(component, options, FieldValueUtil.valueOf(formData[component.key]), ::onSelected) {
                    card.strokeColor = context.getColor(R.color.input_border)
                }
            } else {
                showSimplePopup(card, options, ::onSelected) {
                    card.strokeColor = context.getColor(R.color.input_border)
                }
            }
        }

        card.setOnClickListener {
            card.strokeColor = context.getColor(R.color.accent_green)
            if (isUrlSrc && !remoteLoaded) {
                if (displayText.text.isNullOrBlank() ||
                    displayText.text.toString() == (component.placeholder ?: "")
                ) {
                    displayText.text = context.getString(R.string.formio_loading_options)
                    displayText.setTextColor(context.getColor(R.color.text_hint))
                }
                loadRemoteOptions { openPicker() }
            } else {
                openPicker()
            }
        }

        return container
    }

    // ── Búsqueda con BottomSheet ───────────────────────────────────────────────

    private fun showSearchSheet(
        component: FormComponent,
        options: List<SelectOption>,
        currentValue: String?,
        onSelected: (SelectOption) -> Unit,
        onDismiss: () -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val accentGreen = context.getColor(R.color.accent_green)
        val sheet = BottomSheetDialog(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.bg_card))
                cornerRadii = floatArrayOf(24*dp, 24*dp, 24*dp, 24*dp, 0f, 0f, 0f, 0f)
            }
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        // Drag handle
        val handleRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * dp).toInt(); bottomMargin = (12 * dp).toInt() }
        }
        handleRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.bg_elevated))
                cornerRadius = 4 * dp
            }
        })
        root.addView(handleRow)

        // Título
        root.addView(TextView(context).apply {
            text = component.label
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), (14 * dp).toInt())
        })

        // Barra de búsqueda
        val searchBar = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (16 * dp).toInt(); marginEnd = (16 * dp).toInt()
                bottomMargin = (8 * dp).toInt()
            }
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.bg_input))
                cornerRadius = 10 * dp
                setStroke((1.5f * dp).toInt(), context.getColor(R.color.input_border))
            }
        }

        val searchIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(context.getColor(R.color.text_hint))
            val s = (20 * dp).toInt()
            layoutParams = FrameLayout.LayoutParams(s, s).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                marginStart = (12 * dp).toInt()
            }
        }

        val searchEdit = EditText(context).apply {
            hint = "Buscar…"
            setHintTextColor(context.getColor(R.color.text_hint))
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 14f
            background = null
            setPadding(
                (44 * dp).toInt(), (12 * dp).toInt(),
                (12 * dp).toInt(), (12 * dp).toInt()
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setOnFocusChangeListener { _, focused ->
                (searchBar.background as? GradientDrawable)?.setStroke(
                    (1.5f * dp).toInt(),
                    if (focused) accentGreen else context.getColor(R.color.input_border)
                )
            }
        }

        searchBar.addView(searchEdit)
        searchBar.addView(searchIcon)
        root.addView(searchBar)

        // Divider
        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).apply { bottomMargin = (4 * dp).toInt() }
            setBackgroundColor(context.getColor(R.color.stroke))
        })

        // Lista con RecyclerView
        val recycler = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.5f).toInt()
            )
            layoutManager = LinearLayoutManager(context)
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val adapter = SearchAdapter(options, currentValue) { selected ->
            sheet.dismiss()
            onSelected(selected)
        }
        recycler.adapter = adapter
        root.addView(recycler)

        // Filtrado en tiempo real
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString() ?: "")
                recycler.scrollToPosition(0)
            }
        })

        sheet.setOnDismissListener { onDismiss() }
        sheet.setContentView(root)
        sheet.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        (sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet))
            ?.setBackgroundColor(Color.TRANSPARENT)
        sheet.show()
        searchEdit.requestFocus()
    }

    // ── Popup simple (sin búsqueda) ────────────────────────────────────────────

    private fun showSimplePopup(
        anchor: View,
        options: List<SelectOption>,
        onSelected: (SelectOption) -> Unit,
        onDismiss: () -> Unit
    ) {
        val popup = ListPopupWindow(context)
        popup.anchorView = anchor
        popup.setAdapter(DarkDropdownAdapter(context, options.map { it.label }))
        popup.width = ListPopupWindow.MATCH_PARENT
        popup.isModal = true

        popup.setOnItemClickListener { _, _, position, _ ->
            options.getOrNull(position)?.let { onSelected(it) }
            popup.dismiss()
        }
        popup.setOnDismissListener { onDismiss() }
        popup.show()
    }

    // ── RecyclerView adapter con filtro ───────────────────────────────────────

    private inner class SearchAdapter(
        allOptions: List<SelectOption>,
        private val currentValue: String?,
        private val onSelect: (SelectOption) -> Unit
    ) : RecyclerView.Adapter<SearchAdapter.VH>() {

        private val full = allOptions.toList()
        private var filtered = allOptions.toMutableList()

        inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp = context.resources.displayMetrics.density
            val ripple = android.util.TypedValue().also {
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    (20 * dp).toInt(), (14 * dp).toInt(),
                    (20 * dp).toInt(), (14 * dp).toInt()
                )
                isClickable = true
                isFocusable = true
                foreground = context.getDrawable(ripple.resourceId)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            return VH(row)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val dp = context.resources.displayMetrics.density
            val item = filtered[position]
            val isSelected = item.value == currentValue
            val accentGreen = context.getColor(R.color.accent_green)

            holder.root.removeAllViews()

            // Dot indicator if selected
            holder.root.addView(View(context).apply {
                val size = (7 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (12 * dp).toInt()
                    gravity = Gravity.CENTER_VERTICAL
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (isSelected) accentGreen else Color.TRANSPARENT)
                }
            })

            holder.root.addView(TextView(context).apply {
                text = item.label
                textSize = 15f
                setTextColor(
                    if (isSelected) accentGreen
                    else context.getColor(R.color.text_primary)
                )
                if (isSelected) setTypeface(null, android.graphics.Typeface.BOLD)
                else setTypeface(null, android.graphics.Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            })

            holder.root.setOnClickListener { onSelect(item) }
        }

        override fun getItemCount() = filtered.size

        fun filter(query: String) {
            filtered = if (query.isBlank()) full.toMutableList()
            else full.filter { it.label.contains(query, ignoreCase = true) }.toMutableList()
            notifyDataSetChanged()
        }
    }

    // ── Adaptador simple ──────────────────────────────────────────────────────

    private inner class DarkDropdownAdapter(
        ctx: Context,
        items: List<String>
    ) : ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            buildRow(getItem(position) ?: "")

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            buildRow(getItem(position) ?: "")

        private fun buildRow(text: String): TextView {
            val dp = context.resources.displayMetrics.density
            return TextView(context).apply {
                this.text = text
                setTextColor(context.getColor(R.color.text_primary))
                setBackgroundColor(context.getColor(R.color.bg_card))
                textSize = 14f
                setPadding(
                    (16 * dp).toInt(), (14 * dp).toInt(),
                    (16 * dp).toInt(), (14 * dp).toInt()
                )
            }
        }
    }
}
