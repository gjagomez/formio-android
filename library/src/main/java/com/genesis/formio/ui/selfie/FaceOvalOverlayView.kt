package com.genesis.formio.ui.selfie

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class FaceOvalOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State {
        SEARCHING,
        FACE_FOUND,
        CHALLENGE,
        STEP_OK,
        FAIL,
        LIVENESS_OK,
        CAPTURING
    }

    var state: State = State.SEARCHING
        set(value) {
            field = value
            when (value) {
                State.FACE_FOUND, State.CHALLENGE -> startScanAnim()
                else -> stopScanAnim()
            }
            invalidate()
        }

    var challengeText: String = ""
        set(value) { field = value; invalidate() }

    var subChallengeText: String = ""
        set(value) { field = value; invalidate() }

    // 1f = full time remaining, 0f = expired
    var countdownProgress: Float = 1f
        set(value) { field = value; invalidate() }

    private var scanProgress = 0f
    private var scanAnimator: ValueAnimator? = null

    private val bgPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }

    private val ovalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 42f
        setShadowLayer(5f, 0f, 2f, Color.BLACK)
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        textSize = 30f
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
    }

    private val ovalRect = RectF()
    private val arcRect  = RectF()

    private fun stateColor(): Int = when (state) {
        State.SEARCHING   -> Color.WHITE
        State.FACE_FOUND  -> Color.parseColor("#FFC107")
        State.CHALLENGE   -> Color.parseColor("#FFC107")
        State.STEP_OK     -> Color.parseColor("#4CAF50")
        State.FAIL        -> Color.parseColor("#F44336")
        State.LIVENESS_OK -> Color.parseColor("#4CAF50")
        State.CAPTURING   -> Color.parseColor("#4CAF50")
    }

    private fun startScanAnim() {
        if (scanAnimator?.isRunning == true) return
        scanAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { scanProgress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stopScanAnim() { scanAnimator?.cancel(); scanAnimator = null }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stopScanAnim() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.43f
        val ovalW = w * 0.68f
        val ovalH = h * 0.56f

        ovalRect.set(cx - ovalW / 2f, cy - ovalH / 2f, cx + ovalW / 2f, cy + ovalH / 2f)
        arcRect.set(ovalRect.left - 10f, ovalRect.top - 10f, ovalRect.right + 10f, ovalRect.bottom + 10f)

        // Dark overlay with oval hole
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bc  = Canvas(bmp)
        bc.drawRect(0f, 0f, w, h, bgPaint)
        bc.drawOval(ovalRect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        })
        canvas.drawBitmap(bmp, 0f, 0f, null)
        bmp.recycle()

        val color = stateColor()

        // Oval border
        ovalPaint.color = color
        canvas.drawOval(ovalRect, ovalPaint)

        // Corner brackets
        bracketPaint.color = color
        val bLen = ovalW * 0.16f
        val bp = Path().apply {
            moveTo(ovalRect.left,         ovalRect.top + bLen)
            lineTo(ovalRect.left,         ovalRect.top)
            lineTo(ovalRect.left + bLen,  ovalRect.top)

            moveTo(ovalRect.right - bLen, ovalRect.top)
            lineTo(ovalRect.right,        ovalRect.top)
            lineTo(ovalRect.right,        ovalRect.top + bLen)

            moveTo(ovalRect.right,        ovalRect.bottom - bLen)
            lineTo(ovalRect.right,        ovalRect.bottom)
            lineTo(ovalRect.right - bLen, ovalRect.bottom)

            moveTo(ovalRect.left + bLen,  ovalRect.bottom)
            lineTo(ovalRect.left,         ovalRect.bottom)
            lineTo(ovalRect.left,         ovalRect.bottom - bLen)
        }
        canvas.drawPath(bp, bracketPaint)

        // Countdown arc (ring around the oval, depletes clockwise)
        if (state == State.CHALLENGE) {
            val arcColor = when {
                countdownProgress > 0.5f -> Color.parseColor("#4CAF50")
                countdownProgress > 0.25f -> Color.parseColor("#FFC107")
                else -> Color.parseColor("#F44336")
            }
            // Background track
            arcPaint.color = Color.argb(60, 255, 255, 255)
            canvas.drawArc(arcRect, -90f, 360f, false, arcPaint)
            // Active arc
            arcPaint.color = arcColor
            canvas.drawArc(arcRect, -90f, 360f * countdownProgress, false, arcPaint)
        }

        // Scan line (only while face found or challenge active)
        if (state == State.FACE_FOUND || state == State.CHALLENGE) {
            val lineY = ovalRect.top + ovalRect.height() * scanProgress
            canvas.save()
            canvas.clipRect(ovalRect.left, ovalRect.top, ovalRect.right, ovalRect.bottom)

            val scanColor = color
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    ovalRect.left, lineY, ovalRect.right, lineY,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.argb(160, Color.red(scanColor), Color.green(scanColor), Color.blue(scanColor)),
                        Color.argb(255, Color.red(scanColor), Color.green(scanColor), Color.blue(scanColor)),
                        Color.argb(160, Color.red(scanColor), Color.green(scanColor), Color.blue(scanColor)),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
                    Shader.TileMode.CLAMP
                )
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }
            canvas.drawLine(ovalRect.left, lineY, ovalRect.right, lineY, linePaint)

            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, lineY, 0f, lineY + 36f,
                    intArrayOf(
                        Color.argb(50, Color.red(scanColor), Color.green(scanColor), Color.blue(scanColor)),
                        Color.TRANSPARENT
                    ), null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(ovalRect.left, lineY, ovalRect.right, lineY + 36f, glowPaint)
            canvas.restore()
        }

        // Texts
        val mainText = when (state) {
            State.SEARCHING   -> "Coloca el rostro en el óvalo"
            State.FACE_FOUND  -> "Analizando rostro..."
            State.CHALLENGE   -> challengeText
            State.STEP_OK     -> "¡Bien! Sigue..."
            State.FAIL        -> "Intenta de nuevo"
            State.LIVENESS_OK -> "¡Verificado! Capturando..."
            State.CAPTURING   -> "Capturando..."
        }
        textPaint.color = if (state == State.FAIL) Color.parseColor("#FF6B6B") else Color.WHITE
        canvas.drawText(mainText, cx, ovalRect.bottom + 62f, textPaint)

        if (state == State.CHALLENGE && subChallengeText.isNotBlank()) {
            canvas.drawText(subChallengeText, cx, ovalRect.bottom + 100f, subTextPaint)
        }
    }
}
