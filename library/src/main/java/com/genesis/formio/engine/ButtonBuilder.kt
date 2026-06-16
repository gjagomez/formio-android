package com.genesis.formio.engine

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.google.android.material.button.MaterialButton

class ButtonBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        onButtonClick: ((key: String, action: String?, custom: String?) -> Unit)?
    ): View {
        val dp = context.resources.displayMetrics.density

        val heightPx = when (component.size.lowercase()) {
            "xs", "sm" -> (42 * dp).toInt()
            "lg", "xl" -> (56 * dp).toInt()
            else        -> (50 * dp).toInt()
        }
        val textSizeSp = when (component.size.lowercase()) {
            "lg", "xl" -> 15f
            "xs", "sm" -> 13f
            else        -> 14f
        }

        val button = MaterialButton(context).apply {
            text = component.label.ifBlank { component.key }
            textSize = textSizeSp
            isAllCaps = false
            cornerRadius = (12 * dp).toInt()
            insetTop = 0
            insetBottom = 0
            applyTheme(component.theme, dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx
            )
            setOnClickListener {
                onButtonClick?.invoke(component.key, component.action, component.custom)
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
            addView(button)
        }
    }

    // ── Theme resolver ─────────────────────────────────────────────────────────

    private fun MaterialButton.applyTheme(theme: String, dp: Float) {
        when (theme.lowercase()) {
            "secondary", "default" -> applyOutlineTheme(dp)
            else                   -> applyFilledTheme(theme)
        }
    }

    /** Filled button: background from btn_<theme>_bg, text from btn_<theme>_text. */
    private fun MaterialButton.applyFilledTheme(theme: String) {
        val bgColor   = context.getColor(bgResFor(theme))
        val textColor = context.getColor(textResFor(theme))
        backgroundTintList = ColorStateList.valueOf(bgColor)
        setTextColor(textColor)
        rippleColor = ColorStateList.valueOf(rippleOf(bgColor))
    }

    /** Outline button: transparent fill, stroke + text from btn_secondary_* colors. */
    private fun MaterialButton.applyOutlineTheme(dp: Float) {
        val strokeColor = context.getColor(R.color.btn_secondary_stroke)
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        setTextColor(context.getColor(R.color.btn_secondary_text))
        strokeWidth = (1.5f * dp).toInt()
        this.strokeColor = ColorStateList.valueOf(strokeColor)
        rippleColor = ColorStateList.valueOf(rippleOf(strokeColor))
    }

    // ── Color resource lookup ──────────────────────────────────────────────────

    private fun bgResFor(theme: String): Int = when (theme.lowercase()) {
        "success" -> R.color.btn_success_bg
        "warning" -> R.color.btn_warning_bg
        "info"    -> R.color.btn_info_bg
        "danger"  -> R.color.btn_danger_bg
        else      -> R.color.btn_primary_bg   // primary + any unknown theme
    }

    private fun textResFor(theme: String): Int = when (theme.lowercase()) {
        "success" -> R.color.btn_success_text
        "warning" -> R.color.btn_warning_text
        "info"    -> R.color.btn_info_text
        "danger"  -> R.color.btn_danger_text
        else      -> R.color.btn_primary_text
    }

    /** 25 % opacity version of the given color, used as the press ripple. */
    private fun rippleOf(color: Int): Int =
        Color.argb(0x40, Color.red(color), Color.green(color), Color.blue(color))
}
