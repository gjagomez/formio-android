package com.genesis.formio.engine

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent

class ButtonBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        onButtonClick: ((key: String, action: String?, custom: String?) -> Unit)?
    ): View {
        val density = context.resources.displayMetrics.density
        val heightPx = (52 * density + 0.5f).toInt()

        val button = android.widget.Button(context).apply {
            text = component.label.ifBlank { component.key }
            textSize = 15f
            isAllCaps = false
            setBackgroundResource(R.drawable.bg_button_primary)
            setTextColor(context.getColor(R.color.text_on_accent))
        }

        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx)
        button.layoutParams = lp

        button.setOnClickListener {
            onButtonClick?.invoke(component.key, component.action, component.custom)
        }

        val padH = context.resources.getDimensionPixelSize(R.dimen.spacing_md)
        val padV = context.resources.getDimensionPixelSize(R.dimen.spacing_sm)

        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
        }
        wrapper.addView(button)
        return wrapper
    }
}
