package com.genesis.formio.ui.widgets

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.genesis.formio.R
import com.google.android.material.card.MaterialCardView

/**
 * Modern in-app toast banner with per-type styling, used by
 * `app.mostrarMensaje(title, msg, type)` and internal library messages.
 *
 * Types: `success`, `warning`, `danger`/`error`, anything else renders as info.
 * Shows at the top of the activity, slides in, auto-dismisses, tap to close.
 */
object FormioToast {

    private const val TAG_TOAST = "__formio_toast__"
    private const val AUTO_DISMISS_MS = 3500L

    fun show(context: Context, title: String, message: String, type: String? = null) {
        val activity = context.findActivity()
        if (activity == null || activity.isFinishing) {
            // No window to attach to — degrade gracefully
            Toast.makeText(context, listOf(title, message).filter { it.isNotBlank() }
                .joinToString(": "), Toast.LENGTH_LONG).show()
            return
        }
        activity.runOnUiThread { showBanner(activity, title, message, normalize(type)) }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private enum class Kind { SUCCESS, WARNING, DANGER, INFO }

    private fun normalize(type: String?): Kind = when (type?.trim()?.lowercase()) {
        "success", "ok", "exito", "éxito" -> Kind.SUCCESS
        "warning", "warn", "alerta" -> Kind.WARNING
        "danger", "error" -> Kind.DANGER
        else -> Kind.INFO
    }

    private fun showBanner(activity: Activity, title: String, message: String, kind: Kind) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        // Replace any banner still on screen
        decor.findViewWithTag<View>(TAG_TOAST)?.let { decor.removeView(it) }

        val dp = activity.resources.displayMetrics.density
        val accent = activity.getColor(
            when (kind) {
                Kind.SUCCESS -> R.color.accent_green
                Kind.WARNING -> R.color.msg_warning
                Kind.DANGER -> R.color.error_color
                Kind.INFO -> R.color.stat_sent
            }
        )
        val symbol = when (kind) {
            Kind.SUCCESS -> "✓"
            Kind.WARNING -> "!"
            Kind.DANGER -> "✕"
            Kind.INFO -> "i"
        }

        val card = MaterialCardView(activity).apply {
            tag = TAG_TOAST
            radius = 16 * dp
            cardElevation = 12 * dp
            setCardBackgroundColor(activity.getColor(R.color.bg_card))
            strokeWidth = (1.5f * dp).toInt()
            strokeColor = Color.argb(
                110, Color.red(accent), Color.green(accent), Color.blue(accent)
            )
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
        }

        // Icon in a tinted circle
        row.addView(TextView(activity).apply {
            text = symbol
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(accent)
            gravity = Gravity.CENTER
            val size = (30 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (12 * dp).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(45, Color.red(accent), Color.green(accent), Color.blue(accent)))
            }
        })

        val textColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        if (title.isNotBlank()) {
            textColumn.addView(TextView(activity).apply {
                text = title
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(activity.getColor(R.color.text_primary))
            })
        }
        if (message.isNotBlank()) {
            textColumn.addView(TextView(activity).apply {
                text = message
                textSize = 13f
                setTextColor(activity.getColor(R.color.text_secondary))
            })
        }
        row.addView(textColumn)
        card.addView(row)

        val topInset = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { activity.resources.getDimensionPixelSize(it) } ?: (24 * dp).toInt()

        decor.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ).apply {
            topMargin = topInset + (12 * dp).toInt()
            leftMargin = (16 * dp).toInt()
            rightMargin = (16 * dp).toInt()
        })

        // Slide in
        card.alpha = 0f
        card.translationY = -40 * dp
        card.animate().alpha(1f).translationY(0f).setDuration(220).start()

        fun dismiss() {
            card.animate().alpha(0f).translationY(-40 * dp).setDuration(180)
                .withEndAction { decor.removeView(card) }
                .start()
        }
        card.setOnClickListener { dismiss() }
        card.postDelayed({ if (card.parent != null) dismiss() }, AUTO_DISMISS_MS)
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx != null) {
            if (ctx is Activity) return ctx
            ctx = (ctx as? android.content.ContextWrapper)?.baseContext
        }
        return null
    }
}
