package com.genesis.formio.engine

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R

object FieldLabelBuilder {

    /**
     * Builds a label row:
     *   "Label text"  [*]
     * The * is a separate, red, bold, larger TextView — always visible regardless of
     * TextInputLayout hintTextColor overrides.
     */
    fun build(context: Context, label: String, required: Boolean): LinearLayout {
        val dp = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (8 * dp).toInt())

            addView(TextView(context).apply {
                text = label
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
            })

            if (required) {
                addView(TextView(context).apply {
                    text = " *"
                    setTextColor(context.getColor(R.color.error_color))
                    textSize = 17f
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }
        }
    }
}
