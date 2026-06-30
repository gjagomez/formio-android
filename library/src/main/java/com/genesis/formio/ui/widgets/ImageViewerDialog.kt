package com.genesis.formio.ui.widgets

import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.util.Base64
import android.view.*
import android.widget.*
import com.genesis.formio.R
import kotlin.math.min
import kotlin.math.sqrt

class ImageViewerDialog(private val context: Context) {

    fun show(value: String) {
        val bmp = loadBitmap(value) ?: return
        buildDialog(bmp)
    }

    // ── Bitmap loader (base64 or file path) ────────────────────────────────────

    private fun loadBitmap(value: String): Bitmap? {
        return try {
            if (value.startsWith("/") || value.startsWith("file://")) {
                val path = value.removePrefix("file://")
                val raw = BitmapFactory.decodeFile(path) ?: return null
                val exif = ExifInterface(path)
                val degrees = when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees == 0f) raw
                else {
                    val matrix = Matrix().apply { postRotate(degrees) }
                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                        .also { if (it !== raw) raw.recycle() }
                }
            } else {
                val bytes = Base64.decode(value.substringAfter("base64,"), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) { null }
    }

    // ── Dialog ─────────────────────────────────────────────────────────────────

    private fun buildDialog(bmp: Bitmap) {
        val dp = context.resources.displayMetrics.density
        val screenW = context.resources.displayMetrics.widthPixels.toFloat()
        val screenH = context.resources.displayMetrics.heightPixels.toFloat()

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.argb(245, 5, 5, 15)))
        }

        // ── Transform state ────────────────────────────────────────────────────
        var zoom = 1f
        var panX = 0f
        var panY = 0f
        var rotation = 0f
        var flipH = false
        var flipV = false

        // ── Root layout ────────────────────────────────────────────────────────
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(245, 5, 5, 15))
            isClickable = true
        }

        // ── ImageView ─────────────────────────────────────────────────────────
        val imageView = ImageView(context).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.MATRIX
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(imageView)

        // ── Zoom badge ────────────────────────────────────────────────────────
        val zoomBadge = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(180, 15, 15, 35))
                cornerRadius = (20 * dp)
            }
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            ).apply { setMargins((16 * dp).toInt(), 0, 0, (90 * dp).toInt()) }
            visibility = View.GONE
        }
        root.addView(zoomBadge)

        // ── Hint label ────────────────────────────────────────────────────────
        val hint = TextView(context).apply {
            text = "Pellizca para zoom  •  2 dedos para mover  •  Doble toque 1:1"
            setTextColor(Color.argb(140, 200, 200, 220))
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { setMargins(0, 0, 0, (58 * dp).toInt()) }
        }
        root.addView(hint)

        // ── Bottom toolbar ────────────────────────────────────────────────────
        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(200, 10, 10, 25))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (56 * dp).toInt(),
                Gravity.BOTTOM
            )
        }
        root.addView(toolbar)

        // ── Matrix rebuilder ──────────────────────────────────────────────────
        fun applyMatrix() {
            imageView.post {
                val vW = imageView.width.toFloat()
                val vH = imageView.height.toFloat()
                if (vW <= 0 || vH <= 0) return@post

                val bW = bmp.width.toFloat()
                val bH = bmp.height.toFloat()

                val m = Matrix()
                // Center bitmap in view
                m.setTranslate((vW - bW) / 2f, (vH - bH) / 2f)
                // Fit to view (no crop)
                val fitScale = min(vW / bW, vH / bH)
                m.postScale(fitScale, fitScale, vW / 2f, vH / 2f)
                // Apply rotation around center
                m.postRotate(rotation, vW / 2f, vH / 2f)
                // Apply flip
                if (flipH) m.postScale(-1f, 1f, vW / 2f, vH / 2f)
                if (flipV) m.postScale(1f, -1f, vW / 2f, vH / 2f)
                // Apply zoom centered
                m.postScale(zoom, zoom, vW / 2f, vH / 2f)
                // Apply pan
                m.postTranslate(panX, panY)
                imageView.imageMatrix = m

                // Update badge
                if (zoom > 1.05f) {
                    zoomBadge.text = "%.1f×".format(zoom)
                    zoomBadge.visibility = View.VISIBLE
                    hint.visibility = View.GONE
                } else {
                    zoomBadge.visibility = View.GONE
                    hint.visibility = if (zoom <= 1.05f) View.VISIBLE else View.GONE
                }
            }
        }

        // ── Touch gestures ────────────────────────────────────────────────────
        var lastPanMidX = 0f
        var lastPanMidY = 0f
        var twoFingerDown = false
        var singleDragLastX = 0f
        var singleDragLastY = 0f

        val scaleDetector = ScaleGestureDetector(context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: ScaleGestureDetector): Boolean {
                    zoom = (zoom * d.scaleFactor).coerceIn(0.5f, 8f)
                    applyMatrix()
                    return true
                }
            })

        val gestureDetector = GestureDetector(context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    zoom = if (zoom > 1.5f) 1f else 2.5f
                    panX = 0f; panY = 0f
                    applyMatrix()
                    return true
                }
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (zoom <= 1.05f) dialog.dismiss()
                    return true
                }
            })

        imageView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            when {
                event.pointerCount >= 2 -> {
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    when (event.actionMasked) {
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            lastPanMidX = midX; lastPanMidY = midY; twoFingerDown = true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!scaleDetector.isInProgress && zoom > 1f) {
                                panX += midX - lastPanMidX
                                panY += midY - lastPanMidY
                                applyMatrix()
                            }
                            lastPanMidX = midX; lastPanMidY = midY
                        }
                        MotionEvent.ACTION_POINTER_UP -> twoFingerDown = false
                    }
                }
                !twoFingerDown && event.pointerCount == 1 && zoom > 1.05f -> {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            singleDragLastX = event.x; singleDragLastY = event.y
                        }
                        MotionEvent.ACTION_MOVE -> {
                            panX += event.x - singleDragLastX
                            panY += event.y - singleDragLastY
                            singleDragLastX = event.x; singleDragLastY = event.y
                            applyMatrix()
                        }
                    }
                }
            }
            true
        }

        // ── Toolbar buttons ───────────────────────────────────────────────────
        fun toolBtn(iconRes: Int, label: String, tint: Int = Color.WHITE, onClick: () -> Unit): LinearLayout {
            val ripple = android.util.TypedValue().also {
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true; isFocusable = true
                foreground = context.getDrawable(ripple.resourceId)
                setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
                addView(ImageView(context).apply {
                    setImageResource(iconRes)
                    setColorFilter(tint)
                    val s = (22 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(s, s).apply { bottomMargin = (2 * dp).toInt() }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                })
                addView(TextView(context).apply {
                    text = label
                    setTextColor(Color.argb(180, 220, 220, 220))
                    textSize = 9f
                    gravity = Gravity.CENTER
                })
                setOnClickListener { onClick() }
            }
        }

        // Divider helper
        fun divider() = View(context).apply {
            setBackgroundColor(Color.argb(60, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams((1 * dp).toInt(), (32 * dp).toInt()).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }

        toolbar.addView(toolBtn(R.drawable.ic_rotate_left, "Izq") {
            rotation -= 90f; panX = 0f; panY = 0f; applyMatrix()
        })
        toolbar.addView(toolBtn(R.drawable.ic_rotate_right, "Der") {
            rotation += 90f; panX = 0f; panY = 0f; applyMatrix()
        })
        toolbar.addView(divider())
        toolbar.addView(toolBtn(R.drawable.ic_flip, "Voltear H") {
            flipH = !flipH; applyMatrix()
        })
        toolbar.addView(toolBtn(R.drawable.ic_flip, "Voltear V") {
            flipV = !flipV; applyMatrix()
        })
        toolbar.addView(divider())
        toolbar.addView(toolBtn(R.drawable.ic_add, "1:1", Color.argb(200, 100, 220, 180)) {
            zoom = 1f; panX = 0f; panY = 0f; rotation = 0f; flipH = false; flipV = false
            applyMatrix()
        })

        // ── Close button (top right) ──────────────────────────────────────────
        val closeBtn = FrameLayout(context).apply {
            val size = (44 * dp).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                setMargins((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), 0)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.argb(120, 30, 30, 50))
            }
            isClickable = true; isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }
        closeBtn.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.WHITE)
            val p = (10 * dp).toInt(); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
        })
        root.addView(closeBtn)

        dialog.setContentView(root)
        dialog.show()
        applyMatrix()
    }
}
