package com.genesis.formio.ui.widgets

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Base64
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

class SignatureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Public API ─────────────────────────────────────────────────────────────

    var inkColor: Int = Color.rgb(15, 15, 35)
        set(value) { field = value; invalidate() }

    var onSignatureChanged: ((isEmpty: Boolean) -> Unit)? = null

    fun undo() {
        if (completedStrokes.isNotEmpty()) {
            completedStrokes.removeAt(completedStrokes.lastIndex)
            invalidate()
            onSignatureChanged?.invoke(completedStrokes.isEmpty())
        }
    }

    fun clear() {
        completedStrokes.clear()
        currentStroke = null
        invalidate()
        onSignatureChanged?.invoke(true)
    }

    fun resetZoom() {
        drawMatrix.reset()
        scaleFactor = 1f
        invalidate()
    }

    fun isSignatureEmpty() = completedStrokes.isEmpty()
    fun hasUndo() = completedStrokes.isNotEmpty()
    fun currentZoom() = scaleFactor

    fun getBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(
            width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888
        )
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = inkColor
        }
        completedStrokes.forEach { stroke ->
            stroke.segments.forEach { seg ->
                p.strokeWidth = seg.width
                c.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, p)
            }
        }
        return bmp
    }

    fun getBitmapBase64(): String {
        val out = ByteArrayOutputStream()
        getBitmap().compress(Bitmap.CompressFormat.PNG, 95, out)
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // ── Internal data ──────────────────────────────────────────────────────────

    private data class Segment(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val width: Float
    )
    private data class Stroke(val segments: MutableList<Segment> = mutableListOf())

    private val completedStrokes = mutableListOf<Stroke>()
    private var currentStroke: Stroke? = null

    // ── Zoom / pan ─────────────────────────────────────────────────────────────

    private val drawMatrix = Matrix()
    private var scaleFactor = 1f
    private var lastPanMidX = 0f
    private var lastPanMidY = 0f
    private var twoFingerActive = false

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val newScale = (scaleFactor * d.scaleFactor).coerceIn(1f, 6f)
                val factor = newScale / scaleFactor
                drawMatrix.postScale(factor, factor, d.focusX, d.focusY)
                scaleFactor = newScale
                clampMatrix()
                invalidate()
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetZoom(); return true
            }
        })

    // ── Draw velocity state ────────────────────────────────────────────────────

    private var lastX = 0f
    private var lastY = 0f
    private var lastTime = 0L
    private var lastVelocity = 0f

    companion object {
        private const val MIN_W = 1.6f
        private const val MAX_W = 6f
        private const val VEL_SCALE = 0.055f
        private const val SMOOTHING = 0.55f
    }

    // ── Paints ─────────────────────────────────────────────────────────────────

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        color = Color.argb(55, 0, 160, 110)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 40f
        color = Color.argb(40, 40, 40, 70)
    }
    private val xMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = Color.argb(70, 0, 160, 110)
    }
    private val zoomBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 15, 15, 35)
        style = Paint.Style.FILL
    }
    private val zoomTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val overlayHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 200, 200, 220)
        textSize = 18f
        textAlign = Paint.Align.CENTER
    }

    // ── Touch ──────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event.pointerCount >= 2 || scaleDetector.isInProgress) {
            handlePan(event)
            twoFingerActive = true
            return true
        }

        // Let first ACTION_UP after two-finger gesture pass without drawing
        if (event.action == MotionEvent.ACTION_UP && twoFingerActive) {
            twoFingerActive = false
            return true
        }
        if (twoFingerActive) return true

        val cp = screenToCanvas(event.x, event.y)
        handleDraw(event.action, cp.x, cp.y)
        return true
    }

    private fun handlePan(event: MotionEvent) {
        if (scaleFactor <= 1f || event.pointerCount < 2) return
        val midX = (event.getX(0) + event.getX(1)) / 2f
        val midY = (event.getY(0) + event.getY(1)) / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                lastPanMidX = midX; lastPanMidY = midY
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    drawMatrix.postTranslate(midX - lastPanMidX, midY - lastPanMidY)
                    clampMatrix()
                    invalidate()
                }
                lastPanMidX = midX; lastPanMidY = midY
            }
        }
    }

    private fun handleDraw(action: Int, x: Float, y: Float) {
        val now = System.currentTimeMillis()
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                currentStroke = Stroke()
                lastX = x; lastY = y; lastTime = now; lastVelocity = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val dt = (now - lastTime).coerceAtLeast(1).toFloat()
                val dist = sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY))
                lastVelocity = lastVelocity * SMOOTHING + (dist / dt) * (1f - SMOOTHING)
                // Thinner at high zoom so exported stroke looks consistent
                val base = (MAX_W - lastVelocity * VEL_SCALE).coerceIn(MIN_W, MAX_W)
                val w = (base / scaleFactor).coerceIn(MIN_W / 3f, MAX_W)
                currentStroke?.segments?.add(Segment(lastX, lastY, x, y, w))
                lastX = x; lastY = y; lastTime = now
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentStroke?.let {
                    if (it.segments.isEmpty()) {
                        // Tap with no movement → draw a small dot
                        val w = (2.5f / scaleFactor).coerceIn(1f, 3f)
                        it.segments.add(Segment(x - 0.5f, y, x + 0.5f, y, w))
                    }
                    completedStrokes.add(it)
                }
                currentStroke = null
                onSignatureChanged?.invoke(false)
                invalidate()
            }
        }
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // ── Zoomed/panned layer ────────────────────────────────────────────────
        canvas.save()
        canvas.concat(drawMatrix)

        val baselineY = h * 0.70f
        val padH = w * 0.06f

        canvas.drawLine(padH, baselineY, w - padH, baselineY, baselinePaint)
        canvas.drawText("×", padH - 4f, baselineY + 7f, xMarkPaint)

        if (completedStrokes.isEmpty() && currentStroke == null) {
            canvas.drawText("Firme aquí", w / 2f, baselineY - 18f, hintPaint)
        }

        completedStrokes.forEach { stroke ->
            stroke.segments.forEach { seg ->
                strokePaint.color = inkColor
                strokePaint.strokeWidth = seg.width
                canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, strokePaint)
            }
        }
        currentStroke?.segments?.forEach { seg ->
            strokePaint.color = inkColor
            strokePaint.strokeWidth = seg.width
            canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, strokePaint)
        }

        canvas.restore()

        // ── Fixed overlay (not zoomed) ─────────────────────────────────────────
        if (scaleFactor > 1.05f) {
            // Zoom badge top-right
            val label = "%.1f×".format(scaleFactor)
            val tw = zoomTextPaint.measureText(label)
            canvas.drawRoundRect(w - tw - 28f, 10f, w - 10f, 44f, 18f, 18f, zoomBgPaint)
            canvas.drawText(label, w - tw - 18f, 36f, zoomTextPaint)
            // Reset hint bottom-center
            canvas.drawText("Doble toque para resetear zoom", w / 2f, h - 10f, overlayHintPaint)
        } else if (completedStrokes.isEmpty()) {
            canvas.drawText("Pellizca para acercar  •  1 dedo para firmar", w / 2f, h - 10f, overlayHintPaint)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun screenToCanvas(sx: Float, sy: Float): PointF {
        val inv = Matrix()
        drawMatrix.invert(inv)
        val pts = floatArrayOf(sx, sy)
        inv.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    private fun clampMatrix() {
        val v = FloatArray(9)
        drawMatrix.getValues(v)
        val scale = v[Matrix.MSCALE_X]
        v[Matrix.MTRANS_X] = v[Matrix.MTRANS_X].coerceIn(width * (1f - scale), 0f)
        v[Matrix.MTRANS_Y] = v[Matrix.MTRANS_Y].coerceIn(height * (1f - scale), 0f)
        drawMatrix.setValues(v)
    }
}
