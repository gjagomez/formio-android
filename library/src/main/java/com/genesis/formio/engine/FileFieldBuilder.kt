package com.genesis.formio.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.genesis.formio.ui.form.PhotoCaptureLauncher
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
        val useScan    = component.scan
        val uploadOnly = component.uploadOnly
        val useSelfie  = component.selfie
        val useWebcam  = component.webcam

        // Dual tiles only when uploadOnly=true AND webcam=true:
        // primary=gallery, secondary=camera/selfie/scan
        // scan and selfie are always single tiles regardless of webcam
        val showDualTiles = uploadOnly && useWebcam

        val primaryCard = when {
            uploadOnly -> buildActionCard(
                iconRes   = R.drawable.ic_photo_library,
                iconColor = context.getColor(R.color.stat_done),
                title     = "Galería",
                subtitle  = "Subir imagen"
            )
            useSelfie  -> buildActionCard(
                iconRes   = R.drawable.ic_selfie,
                iconColor = context.getColor(R.color.stat_sent),
                title     = "Selfie",
                subtitle  = "Foto de rostro"
            )
            useScan    -> buildActionCard(
                iconRes   = R.drawable.ic_scan,
                iconColor = context.getColor(R.color.stat_sent),
                title     = "Escanear",
                subtitle  = "Escanear documento"
            )
            else       -> buildActionCard(
                iconRes   = R.drawable.ic_camera,
                iconColor = context.getColor(R.color.stat_sent),
                title     = "Cámara",
                subtitle  = "Tomar foto"
            )
        }

        // Secondary tile (only when uploadOnly+webcam): selfie/scan/camera depending on flags
        val secondaryCard: MaterialCardView? = if (showDualTiles) when {
            useSelfie -> buildActionCard(
                iconRes   = R.drawable.ic_selfie,
                iconColor = context.getColor(R.color.stat_sent),
                title     = "Selfie",
                subtitle  = "Foto de rostro"
            )
            useScan   -> buildActionCard(
                iconRes   = R.drawable.ic_scan,
                iconColor = context.getColor(R.color.stat_sent),
                title     = "Escanear",
                subtitle  = "Escanear documento"
            )
            else      -> buildActionCard(
                iconRes   = R.drawable.ic_camera,
                iconColor = context.getColor(R.color.stat_sent),
                title     = "Cámara",
                subtitle  = "Tomar foto"
            )
        } else null

        val tilesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        if (showDualTiles && secondaryCard != null) {
            primaryCard.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginEnd = (spacingSm / 2) }
            secondaryCard.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = (spacingSm / 2) }
            tilesRow.addView(primaryCard)
            tilesRow.addView(secondaryCard)
        } else {
            primaryCard.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            tilesRow.addView(primaryCard)
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
        val btnChangeIcon = when {
            uploadOnly -> R.drawable.ic_photo_library
            useSelfie  -> R.drawable.ic_selfie
            useScan    -> R.drawable.ic_scan
            else       -> R.drawable.ic_camera
        }
        val btnChangeColor = if (uploadOnly) context.getColor(R.color.stat_done)
                             else context.getColor(R.color.stat_sent)
        val btnChange = makeIconBtn(btnChangeIcon, "Cambiar", btnChangeColor) {}
        val btnDelete = makeIconBtn(R.drawable.ic_delete, "Borrar",  context.getColor(R.color.error_color)) {}

        photoFooter.addView(btnView)
        photoFooter.addView(btnChange)
        photoFooter.addView(btnDelete)
        photoCardInner.addView(photoFooter)
        photoCard.addView(photoCardInner)
        withPhotoLayout.addView(photoCard)

        container.addView(withPhotoLayout)

        // ── Helper: switch to "with photo" state ───────────────────────────
        fun showPreview(value: String) {
            if (value.isBlank()) return
            val bmp: Bitmap? = try {
                if (value.startsWith("/") || value.startsWith("file://")) {
                    val path = if (value.startsWith("file://")) value.removePrefix("file://") else value
                    decodeBitmapFromPath(path)
                } else {
                    val raw64 = if (value.contains("base64,")) value.substringAfter("base64,") else value
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bytes = Base64.decode(raw64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
            } catch (_: Throwable) { null }

            if (bmp == null) return  // don't enter state B with an empty image

            // Recycle the previous bitmap before replacing it
            val prev = (largeImageView.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            largeImageView.setImageBitmap(bmp)
            if (prev != null && prev != bmp && !prev.isRecycled) prev.recycle()

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
            when {
                uploadOnly -> option(R.drawable.ic_photo_library, "Elegir de galería", context.getColor(R.color.stat_done)) {
                    (context as? PhotoCaptureLauncher)?.launchGallery(component.key, onResult)
                }
                useSelfie  -> option(R.drawable.ic_selfie, "Tomar selfie", context.getColor(R.color.stat_sent)) {
                    (context as? PhotoCaptureLauncher)?.launchSelfieCamera(component.key, onResult)
                }
                useScan    -> option(R.drawable.ic_scan, "Escanear documento", context.getColor(R.color.stat_sent)) {
                    (context as? PhotoCaptureLauncher)?.launchDocumentScanner(component.key, onResult)
                }
                else       -> option(R.drawable.ic_camera, "Tomar foto", context.getColor(R.color.stat_sent)) {
                    (context as? PhotoCaptureLauncher)?.launchCamera(component.key, onResult)
                }
            }
            if (showDualTiles) when {
                useSelfie -> option(R.drawable.ic_selfie, "Tomar selfie", context.getColor(R.color.stat_sent)) {
                    (context as? PhotoCaptureLauncher)?.launchSelfieCamera(component.key, onResult)
                }
                useScan   -> option(R.drawable.ic_scan, "Escanear documento", context.getColor(R.color.stat_sent)) {
                    (context as? PhotoCaptureLauncher)?.launchDocumentScanner(component.key, onResult)
                }
                else      -> option(R.drawable.ic_camera, "Tomar foto", context.getColor(R.color.stat_sent)) {
                    (context as? PhotoCaptureLauncher)?.launchCamera(component.key, onResult)
                }
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

        // ── Large tile click ───────────────────────────────────────────────
        fun captureOnResult(path: String) {
            formData[component.key] = path
            onChange(component.key, path)
            showPreview(path)
        }

        primaryCard.setOnClickListener {
            when {
                uploadOnly -> (context as? PhotoCaptureLauncher)
                    ?.launchGallery(component.key, ::captureOnResult)
                    ?: com.genesis.formio.ui.widgets.FormioToast.show(context, "", "Galería no disponible", "danger")
                useSelfie  -> (context as? PhotoCaptureLauncher)
                    ?.launchSelfieCamera(component.key, ::captureOnResult)
                    ?: com.genesis.formio.ui.widgets.FormioToast.show(context, "", "Selfie no disponible", "danger")
                useScan    -> (context as? PhotoCaptureLauncher)
                    ?.launchDocumentScanner(component.key, ::captureOnResult)
                    ?: com.genesis.formio.ui.widgets.FormioToast.show(context, "", "Escáner no disponible", "danger")
                else       -> (context as? PhotoCaptureLauncher)
                    ?.launchCamera(component.key, ::captureOnResult)
                    ?: com.genesis.formio.ui.widgets.FormioToast.show(context, "", "Cámara no disponible", "danger")
            }
        }

        secondaryCard?.setOnClickListener {
            when {
                useSelfie -> (context as? PhotoCaptureLauncher)
                    ?.launchSelfieCamera(component.key, ::captureOnResult)
                    ?: com.genesis.formio.ui.widgets.FormioToast.show(context, "", "Selfie no disponible", "danger")
                useScan   -> (context as? PhotoCaptureLauncher)
                    ?.launchDocumentScanner(component.key, ::captureOnResult)
                    ?: com.genesis.formio.ui.widgets.FormioToast.show(context, "", "Escáner no disponible", "danger")
                else      -> (context as? PhotoCaptureLauncher)
                    ?.launchCamera(component.key, ::captureOnResult)
                    ?: com.genesis.formio.ui.widgets.FormioToast.show(context, "", "Cámara no disponible", "danger")
            }
        }

        // If there's already a saved value, show the preview immediately
        val existingValue = formData[component.key]?.toString() ?: ""
        if (existingValue.isNotBlank()) showPreview(existingValue)

        return container
    }

    // ── EXIF-aware bitmap loader ───────────────────────────────────────────────

    private fun decodeBitmapFromPath(path: String): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val raw = BitmapFactory.decodeFile(path, opts) ?: return null
        return try {
            val exif = ExifInterface(path)
            val degrees = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return raw
            }
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                .also { if (it !== raw) raw.recycle() }
        } catch (_: Throwable) {
            raw  // ExifInterface failed but raw bitmap is valid — return as-is
        }
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
