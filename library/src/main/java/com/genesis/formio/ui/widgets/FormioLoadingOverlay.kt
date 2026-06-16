package com.genesis.formio.ui.widgets

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R

/**
 * Full-screen loading overlay shown while an app.ws() HTTP call is in flight.
 * Renders two concentric spinning arcs + a centered accent circle + status text.
 * Call [show] from the main thread; call [hide] from the main thread when done.
 */
class FormioLoadingOverlay(private val activity: Activity) {

    private var dialog: Dialog? = null
    private var rootView: FrameLayout? = null
    private var spinnerView: LoadingSpinnerView? = null

    fun show(message: String = "Procesando...") {
        if (activity.isFinishing || activity.isDestroyed) return
        if (dialog?.isShowing == true) return

        val dp = activity.resources.displayMetrics.density

        val root = FrameLayout(activity).apply {
            setBackgroundColor(0xCC080810.toInt())
            isClickable = true
            isFocusable = true
        }
        rootView = root

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val sv = LoadingSpinnerView(activity, dp).apply {
            layoutParams = LinearLayout.LayoutParams(
                (200 * dp).toInt(), (200 * dp).toInt()
            )
        }
        spinnerView = sv
        content.addView(sv)

        if (message.isNotBlank()) {
            content.addView(TextView(activity).apply {
                text = message
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                alpha = 0.90f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (24 * dp).toInt() }
            })
        }

        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        dialog = Dialog(
            activity,
            android.R.style.Theme_Translucent_NoTitleBar_Fullscreen
        ).apply {
            setContentView(root)
            setCancelable(false)
            window?.apply {
                setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT
                )
                setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                )
            }
        }

        dialog?.show()
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(220).start()
        sv.start()
    }

    fun hide() {
        val d = dialog ?: return
        val root = rootView

        fun doHide() {
            spinnerView?.stop()
            spinnerView = null
            rootView = null
            if (!activity.isFinishing && !activity.isDestroyed) {
                try { d.dismiss() } catch (_: Exception) {}
            }
            dialog = null
        }

        if (root != null && root.isAttachedToWindow) {
            root.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction { doHide() }
                .start()
        } else {
            doHide()
        }
    }

    // ── Animated spinner view ──────────────────────────────────────────────────

    private class LoadingSpinnerView(context: Context, private val dp: Float) : View(context) {

        private var outerAngle = 0f
        private var innerAngle = 0f
        private var outerAnim: ValueAnimator? = null
        private var innerAnim: ValueAnimator? = null

        // Outer dashed ring
        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * dp
            color = Color.WHITE
            alpha = 110
            pathEffect = DashPathEffect(floatArrayOf(5f * dp, 11f * dp), 0f)
        }

        // Inner solid sweep arc
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.5f * dp
            color = Color.WHITE
            alpha = 225
            strokeCap = Paint.Cap.ROUND
        }

        // Subtle halo behind center circle
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 20f * dp
            color = Color.WHITE
            alpha = 15
        }

        // Center filled circle (accent color)
        private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = try {
                context.getColor(R.color.btn_primary_bg)
            } catch (_: Exception) {
                Color.parseColor("#00C896")
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (width == 0 || height == 0) return
            val cx = width / 2f
            val cy = height / 2f
            val outerR = width / 2f - 6f * dp
            val innerR = outerR - 22f * dp
            val centerR = innerR - 18f * dp

            // Halo
            canvas.drawCircle(cx, cy, innerR + 10f * dp, glowPaint)

            // Outer dashed ring — rotates clockwise
            canvas.save()
            canvas.rotate(outerAngle, cx, cy)
            canvas.drawCircle(cx, cy, outerR, outerPaint)
            canvas.restore()

            // Inner sweep arc — rotates counter-clockwise
            canvas.save()
            canvas.rotate(innerAngle, cx, cy)
            val rect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
            canvas.drawArc(rect, -90f, 255f, false, innerPaint)
            canvas.restore()

            // Center accent circle
            canvas.drawCircle(cx, cy, centerR, centerPaint)
        }

        fun start() {
            outerAnim = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    outerAngle = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            innerAnim = ValueAnimator.ofFloat(360f, 0f).apply {
                duration = 1800
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { innerAngle = it.animatedValue as Float }
                start()
            }
        }

        fun stop() {
            outerAnim?.cancel(); outerAnim = null
            innerAnim?.cancel(); innerAnim = null
        }
    }
}
