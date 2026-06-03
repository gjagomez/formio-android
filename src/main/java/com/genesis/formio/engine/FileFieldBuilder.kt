package com.genesis.formio.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.genesis.formio.ui.form.PhotoCaptureLauncher
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class FileFieldBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density
        val spacingSm = (8 * dp).toInt()
        val spacingMd = (16 * dp).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, spacingMd)
        }

        // ── Label ──────────────────────────────────────────────────────────────
        container.addView(TextView(context).apply {
            text = if (component.validate?.required == true) "${component.label} *"
                   else component.label
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (10 * dp).toInt())
        })

        // ══════════════════════════════════════════════════════════════════════
        // STATE A — NO PHOTO: three large action tiles
        // ══════════════════════════════════════════════════════════════════════
        val gap = (4 * dp).toInt()

        val cameraCardLarge = buildActionCard(
            iconRes   = R.drawable.ic_camera,
            iconColor = context.getColor(R.color.stat_sent),
            title     = "Cámara",
            subtitle  = "Tomar foto"
        ).also {
            it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = gap }
        }

        val galleryCardLarge = buildActionCard(
            iconRes   = R.drawable.ic_photo_library,
            iconColor = context.getColor(R.color.stat_done),
            title     = "Galería",
            subtitle  = "Subir foto"
        ).also {
            it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = gap }
        }

        val tilesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            addView(cameraCardLarge)
            addView(galleryCardLarge)
        }
        container.addView(tilesRow)

        // ══════════════════════════════════════════════════════════════════════
        // STATE B — WITH PHOTO: small buttons on top + large image below
        // ══════════════════════════════════════════════════════════════════════
        val withPhotoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ── Photo card (state B) ───────────────────────────────────────────
        val photoCard = MaterialCardView(context).apply {
            setCardBackgroundColor(android.graphics.Color.parseColor("#0A0A0A"))
            radius = (14 * dp)
            cardElevation = 0f
            strokeWidth = (2 * dp).toInt()
            strokeColor = context.getColor(R.color.accent_green)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val photoCardInner = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // Photo preview — CENTER_CROP fills the container naturally (like the camera shows it)
        val largeImageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (220 * dp).toInt()
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        photoCardInner.addView(largeImageView)

        // Divider
        photoCardInner.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            )
            setBackgroundColor(context.getColor(R.color.input_border))
        })

        // Footer bar
        val photoFooter = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(context.getColor(R.color.bg_card))
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
        }

        val accentGreen = context.getColor(R.color.accent_green)
        photoFooter.addView(View(context).apply {
            val s = (8 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = (8 * dp).toInt() }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(accentGreen)
            }
        })
        photoFooter.addView(TextView(context).apply {
            text = "Foto capturada"
            setTextColor(accentGreen)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Icon button builder (same style as signature)
        fun makeIconBtn(iconRes: Int, label: String, iconColor: Int, onClick: () -> Unit): LinearLayout {
            val ripple = android.util.TypedValue().also {
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }
            val circleAlpha = android.graphics.Color.argb(
                25, android.graphics.Color.red(iconColor),
                android.graphics.Color.green(iconColor), android.graphics.Color.blue(iconColor)
            )
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val btnPad = (10 * dp).toInt()
                setPadding(btnPad, (6 * dp).toInt(), btnPad, (6 * dp).toInt())
                isClickable = true; isFocusable = true
                foreground = context.getDrawable(ripple.resourceId)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = (2 * dp).toInt() }

                val circleSize = (34 * dp).toInt()
                val circle = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                        bottomMargin = (3 * dp).toInt()
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(circleAlpha)
                    }
                }
                circle.addView(ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    setImageResource(iconRes)
                    setColorFilter(iconColor)
                    val p = (8 * dp).toInt(); setPadding(p, p, p, p)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                })
                addView(circle)
                addView(TextView(context).apply {
                    text = label
                    setTextColor(iconColor)
                    textSize = 10f
                    gravity = Gravity.CENTER
                })
                setOnClickListener { onClick() }
            }
        }

        val btnView   = makeIconBtn(R.drawable.ic_eye,    "Ver",     context.getColor(R.color.text_secondary)) {}
        val btnChange = makeIconBtn(R.drawable.ic_camera, "Cambiar", context.getColor(R.color.stat_sent)) {}
        val btnDelete = makeIconBtn(R.drawable.ic_delete, "Borrar",  context.getColor(R.color.error_color)) {}

        photoFooter.addView(btnView)
        photoFooter.addView(btnChange)
        photoFooter.addView(btnDelete)
        photoCardInner.addView(photoFooter)
        photoCard.addView(photoCardInner)
        withPhotoLayout.addView(photoCard)

        // Keep references for the large card click (used in showPreview)
        val largeImageCard = photoCard

        container.addView(withPhotoLayout)

        // ── Helper: switch to "with photo" state ───────────────────────────
        fun showPreview(value: String) {
            if (value.isBlank()) return
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = if (value.startsWith("/") || value.startsWith("file://")) {
                    val path = if (value.startsWith("file://")) value.removePrefix("file://") else value
                    BitmapFactory.decodeFile(path, opts)
                } else {
                    val bytes = Base64.decode(value.substringAfter("base64,"), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
                if (bmp != null) largeImageView.setImageBitmap(bmp)
            } catch (_: Throwable) { }

            tilesRow.visibility = View.GONE
            withPhotoLayout.visibility = View.VISIBLE

            // Ver → full-screen viewer with zoom/rotate/flip
            btnView.setOnClickListener {
                com.genesis.formio.ui.widgets.ImageViewerDialog(context).show(value)
            }
            // Tapping the image itself also opens the viewer
            largeImageView.isClickable = true
            largeImageView.isFocusable = true
            largeImageView.setOnClickListener {
                com.genesis.formio.ui.widgets.ImageViewerDialog(context).show(value)
            }

            // Borrar → clear photo, back to tiles
            btnDelete.setOnClickListener {
                formData[component.key] = null
                onChange(component.key, null)
                largeImageView.setImageBitmap(null)
                withPhotoLayout.visibility = View.GONE
                tilesRow.visibility = View.VISIBLE
            }
        }

        // Cambiar → show camera/gallery picker sheet
        fun launchChangePicker(onResult: (String) -> Unit) {
            val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(context)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(context.getColor(R.color.bg_card))
                val p = (20 * dp).toInt()
                setPadding(p, p, p, (32 * dp).toInt())
            }
            root.addView(TextView(context).apply {
                text = "Cambiar foto"
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, (16 * dp).toInt())
            })
            fun option(iconRes: Int, label: String, color: Int, onClick: () -> Unit) {
                root.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true; isFocusable = true
                    val ripple = android.util.TypedValue().also {
                        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
                    }
                    foreground = context.getDrawable(ripple.resourceId)
                    setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    addView(ImageView(context).apply {
                        setImageResource(iconRes); setColorFilter(color)
                        val s = (24 * dp).toInt()
                        layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = (16 * dp).toInt() }
                    })
                    addView(TextView(context).apply {
                        text = label; setTextColor(context.getColor(R.color.text_primary)); textSize = 15f
                    })
                    setOnClickListener { sheet.dismiss(); onClick() }
                })
            }
            option(R.drawable.ic_camera, "Tomar foto", context.getColor(R.color.stat_sent)) {
                (context as? PhotoCaptureLauncher)?.launchCamera(component.key, onResult)
            }
            option(R.drawable.ic_photo_library, "Elegir de galería", context.getColor(R.color.stat_done)) {
                (context as? PhotoCaptureLauncher)?.launchGallery(component.key, onResult)
            }
            sheet.setContentView(root)
            sheet.show()
        }

        btnChange.setOnClickListener {
            launchChangePicker { path ->
                formData[component.key] = path
                onChange(component.key, path)
                showPreview(path)
            }
        }

        // ── Large tile clicks ──────────────────────────────────────────────
        cameraCardLarge.setOnClickListener {
            (context as? PhotoCaptureLauncher)?.launchCamera(component.key) { path ->
                formData[component.key] = path
                onChange(component.key, path)
                showPreview(path)
            } ?: Toast.makeText(context, "Cámara no disponible", Toast.LENGTH_SHORT).show()
        }

        galleryCardLarge.setOnClickListener {
            (context as? PhotoCaptureLauncher)?.launchGallery(component.key) { path ->
                formData[component.key] = path
                onChange(component.key, path)
                showPreview(path)
            } ?: Toast.makeText(context, "Galería no disponible", Toast.LENGTH_SHORT).show()
        }

        // If there's already a saved value, show the preview immediately
        val existingValue = formData[component.key]?.toString() ?: ""
        if (existingValue.isNotBlank()) showPreview(existingValue)

        return container
    }

    // ── Large action tile ──────────────────────────────────────────────────────

    private fun buildActionCard(
        iconRes: Int,
        iconColor: Int,
        title: String,
        subtitle: String
    ): MaterialCardView {
        val dp = context.resources.displayMetrics.density
        val rippleAttr = android.util.TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }

        return MaterialCardView(context).apply {
            setCardBackgroundColor(context.getColor(R.color.bg_elevated))
            radius = (12 * dp)
            cardElevation = 0f
            strokeWidth = (1 * dp).toInt()
            strokeColor = context.getColor(R.color.input_border)
            isClickable = true
            isFocusable = true
            foreground = context.getDrawable(rippleAttr.resourceId)

            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    (8 * dp).toInt(), (18 * dp).toInt(),
                    (8 * dp).toInt(), (14 * dp).toInt()
                )
            }

            val circleSize = (52 * dp).toInt()
            val circleBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(30, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor)))
            }
            val circleFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(circleSize, circleSize)
                background = circleBg
            }
            val iconPad = (13 * dp).toInt()
            circleFrame.addView(ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(iconRes)
                setColorFilter(iconColor)
                setPadding(iconPad, iconPad, iconPad, iconPad)
            })

            inner.addView(circleFrame)
            inner.addView(TextView(context).apply {
                text = title
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (8 * dp).toInt() }
            })
            inner.addView(TextView(context).apply {
                text = subtitle
                setTextColor(context.getColor(R.color.text_secondary))
                textSize = 10f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (2 * dp).toInt() }
            })

            addView(inner)
        }
    }

    // ── Compact button chip (used in "with photo" state) ──────────────────────

}
