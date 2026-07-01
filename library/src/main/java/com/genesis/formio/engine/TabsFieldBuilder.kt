package com.genesis.formio.engine

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.card.MaterialCardView

class TabsFieldBuilder(
    private val context: Context,
    private val onButtonClick: ((key: String, action: String?, custom: String?) -> Unit)? = null
) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density

        val tabs = component.components ?: return LinearLayout(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (8 * dp).toInt())
        }

        // ── Tab bar ────────────────────────────────────────────────────────────
        val tabScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }

        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
        }
        tabScrollView.addView(tabRow)
        container.addView(tabScrollView)

        // ── Content frame — cards are added lazily ─────────────────────────────
        val contentFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(contentFrame)

        val tabLabels   = mutableListOf<TextView>()
        // null = not yet rendered; non-null = already built MaterialCardView
        val contentCards = arrayOfNulls<MaterialCardView>(tabs.size)
        val fieldFactory = FieldFactory(context, onButtonClick)

        // ── Build a tab's card on demand ───────────────────────────────────────
        fun buildCard(index: Int): MaterialCardView {
            contentCards[index]?.let { return it }

            val tabDef = tabs[index]
            val card = MaterialCardView(context).apply {
                setCardBackgroundColor(context.getColor(R.color.bg_card))
                radius = (12 * dp)
                cardElevation = 0f
                strokeColor = context.getColor(R.color.input_border)
                strokeWidth = (1 * dp).toInt()
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }

            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val p = (16 * dp).toInt()
                setPadding(p, p, p, p)
            }

            tabDef.components?.forEach { child ->
                if (child.type != "hidden") {
                    fieldFactory.createView(child, formData, onChange)?.let { panel.addView(it) }
                }
            }

            card.addView(panel)
            contentFrame.addView(card)
            contentCards[index] = card
            return card
        }

        // ── Selection logic ────────────────────────────────────────────────────
        fun selectTab(index: Int) {
            tabLabels.forEachIndexed { i, label ->
                if (i == index) {
                    label.setBackgroundResource(R.drawable.bg_tab_active)
                    label.setTextColor(context.getColor(R.color.text_on_accent))
                    label.setTypeface(null, Typeface.BOLD)
                } else {
                    label.setBackgroundResource(R.drawable.bg_tab_inactive)
                    label.setTextColor(context.getColor(R.color.text_secondary))
                    label.setTypeface(null, Typeface.NORMAL)
                }
            }

            // Lazy: build the selected tab if it hasn't been rendered yet
            val selectedCard = buildCard(index)

            // Hide all other already-built cards, show the selected one
            contentCards.forEachIndexed { i, card ->
                card?.visibility = if (i == index) View.VISIBLE else View.GONE
            }
            selectedCard.visibility = View.VISIBLE

            // Scroll active tab into view
            tabScrollView.post {
                val tabView = tabLabels.getOrNull(index) ?: return@post
                val scrollX = tabView.left - tabScrollView.width / 2 + tabView.width / 2
                tabScrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
            }
        }

        // ── Build only tab labels up-front (no content yet) ───────────────────
        tabs.forEachIndexed { index, tabDef ->
            val tabLabel = TextView(context).apply {
                text = tabDef.label
                textSize = 13f
                setPadding(
                    (14 * dp).toInt(), (8 * dp).toInt(),
                    (14 * dp).toInt(), (8 * dp).toInt()
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (6 * dp).toInt() }
                setOnClickListener { selectTab(index) }
            }
            tabRow.addView(tabLabel)
            tabLabels.add(tabLabel)
        }

        // Select (and lazily render) the first tab
        if (tabs.isNotEmpty()) selectTab(0)

        return container
    }
}
