package com.genesis.formio.engine

import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.genesis.formio.R
import com.genesis.formio.model.FormComponent
import com.genesis.formio.ui.widgets.SignatureView
import com.genesis.formio.util.SignatureStorage
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class SignatureFieldBuilder(private val context: Context) {

    fun build(
        component: FormComponent,
        formData: MutableMap<String, Any?>,
        onChange: (String, Any?) -> Unit
    ): View {
        val dp = context.resources.displayMetrics.density
        val accentGreen = context.getColor(R.color.accent_green)
        val rippleAttr = android.util.TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        // ── Label con * rojo si requerido ──────────────────────────────────────
        val isRequired = component.validate?.required == true
        container.addView(TextView(context).apply {
            text = if (isRequired) {
                SpannableString("${component.label} *").also {
                    it.setSpan(ForegroundColorSpan(context.getColor(R.color.error_color)),
                        it.length - 1, it.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else component.label
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (10 * dp).toInt())
        })

        // ── Card ───────────────────────────────────────────────────────────────
        val card = MaterialCardView(context).apply {
            setCardBackgroundColor(context.getColor(R.color.bg_elevated))
            radius = (14 * dp)
            cardElevation = 0f
            strokeWidth = (1.5f * dp).toInt()
            strokeColor = Color.argb(80,
                Color.red(accentGreen), Color.green(accentGreen), Color.blue(accentGreen))
            isClickable = true
            isFocusable = true
            foreground = context.getDrawable(rippleAttr.resourceId)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val cardInner = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // ── STATE A: empty ─────────────────────────────────────────────────────
        val emptyState = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (32 * dp).toInt(), (16 * dp).toInt(), (28 * dp).toInt())
        }

        val circleSize = (64 * dp).toInt()
        val circleBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(30, Color.red(accentGreen), Color.green(accentGreen), Color.blue(accentGreen)))
        }
        val circleFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                bottomMargin = (14 * dp).toInt()
            }
            background = circleBg
        }
        circleFrame.addView(ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.ic_signature)
            setColorFilter(accentGreen)
            val p = (14 * dp).toInt(); setPadding(p, p, p, p)
            scaleType = ImageView.ScaleType.FIT_CENTER
        })
        emptyState.addView(circleFrame)

        emptyState.addView(TextView(context).apply {
            text = "Agregar Firma"
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        emptyState.addView(TextView(context).apply {
            text = "Toque para dibujar su firma"
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * dp).toInt() }
        })

        // Signature line hint
        val sigLineRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (18 * dp).toInt() }
        }
        sigLineRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, (1 * dp).toInt(), 1f)
            background = GradientDrawable().apply {
                setStroke((1 * dp).toInt(), Color.argb(60,
                    Color.red(accentGreen), Color.green(accentGreen), Color.blue(accentGreen)),
                    (8 * dp), (4 * dp))
            }
        })
        sigLineRow.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_signature)
            setColorFilter(Color.argb(80, Color.red(accentGreen), Color.green(accentGreen), Color.blue(accentGreen)))
            val s = (16 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginStart = (8 * dp).toInt(); marginEnd = (8 * dp).toInt()
            }
        })
        sigLineRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, (1 * dp).toInt(), 1f)
            background = GradientDrawable().apply {
                setStroke((1 * dp).toInt(), Color.argb(60,
                    Color.red(accentGreen), Color.green(accentGreen), Color.blue(accentGreen)),
                    (8 * dp), (4 * dp))
            }
        })
        emptyState.addView(sigLineRow)

        // ── STATE B: signed ────────────────────────────────────────────────────
        val signedState = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        // Signature preview area (white background)
        val previewFrame = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (130 * dp).toInt())
        }
        val sigPreview = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = (12 * dp).toInt(); setPadding(p, p, p, p)
        }
        previewFrame.addView(sigPreview)
        signedState.addView(previewFrame)

        // Divider
        signedState.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
            setBackgroundColor(context.getColor(R.color.input_border))
        })

        // Footer bar
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(context.getColor(R.color.bg_card))
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
        }

        // Green dot + status text
        footer.addView(View(context).apply {
            val s = (8 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = (8 * dp).toInt() }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accentGreen) }
        })
        footer.addView(TextView(context).apply {
            text = "Firma capturada"
            setTextColor(accentGreen)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Action icon buttons inline
        fun makeIconBtn(iconRes: Int, label: String, iconColor: Int, onClick: () -> Unit): LinearLayout {
            val ripple = android.util.TypedValue().also {
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val btnPad = (10 * dp).toInt()
                setPadding(btnPad, (6 * dp).toInt(), btnPad, (6 * dp).toInt())
                isClickable = true
                isFocusable = true
                foreground = context.getDrawable(ripple.resourceId)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = (2 * dp).toInt() }

                // Circle background for icon
                val circleSize = (34 * dp).toInt()
                val circleAlpha = Color.argb(25, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor))
                val circle = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                        bottomMargin = (3 * dp).toInt()
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
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
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                })
                setOnClickListener { onClick() }
            }
        }

        val btnView   = makeIconBtn(R.drawable.ic_eye,    "Ver",     context.getColor(R.color.text_secondary)) {}
        val btnChange = makeIconBtn(R.drawable.ic_edit,   "Cambiar", context.getColor(R.color.stat_sent)) {}
        val btnDelete = makeIconBtn(R.drawable.ic_delete, "Borrar",  context.getColor(R.color.error_color)) {}

        footer.addView(btnView)
        footer.addView(btnChange)
        footer.addView(btnDelete)
        signedState.addView(footer)

        // ── Assemble card ──────────────────────────────────────────────────────
        cardInner.addView(emptyState)
        cardInner.addView(signedState)
        card.addView(cardInner)
        container.addView(card)

        // ── Helper: switch to signed state ─────────────────────────────────────
        fun showSigned(value: String) {
            try {
                val bmp = if (value.startsWith("/") || value.startsWith("file://")) {
                    val path = if (value.startsWith("file://")) value.removePrefix("file://") else value
                    BitmapFactory.decodeFile(path)
                } else {
                    val bytes = Base64.decode(value.substringAfter("base64,"), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                if (bmp != null) sigPreview.setImageBitmap(bmp)
            } catch (_: Throwable) { }
            emptyState.visibility = View.GONE
            signedState.visibility = View.VISIBLE
            card.strokeColor = accentGreen
            card.strokeWidth = (2 * dp).toInt()
        }

        fun clearSigned() {
            sigPreview.setImageBitmap(null)
            emptyState.visibility = View.VISIBLE
            signedState.visibility = View.GONE
            card.strokeColor = Color.argb(80, Color.red(accentGreen), Color.green(accentGreen), Color.blue(accentGreen))
            card.strokeWidth = (1.5f * dp).toInt()
        }

        // Wire footer buttons
        btnView.setOnClickListener {
            formData[component.key]?.toString()?.takeIf { it.isNotBlank() }?.let {
                com.genesis.formio.ui.widgets.ImageViewerDialog(context).show(it)
            }
        }
        btnChange.setOnClickListener {
            openSignatureDialog(component.label) { base64 ->
                formData[component.key] = base64
                onChange(component.key, base64)
                showSigned(base64)
            }
        }
        btnDelete.setOnClickListener {
            formData[component.key]?.toString()?.let { SignatureStorage.delete(it) }
            formData[component.key] = null
            onChange(component.key, null)
            clearSigned()
        }

        // Empty card tap → open dialog
        card.setOnClickListener {
            val saved = formData[component.key]?.toString()
            if (saved.isNullOrBlank()) {
                openSignatureDialog(component.label) { base64 ->
                    formData[component.key] = base64
                    onChange(component.key, base64)
                    showSigned(base64)
                }
            }
            // When signed, taps on the card are handled by the footer buttons
        }

        // Restore existing signature
        formData[component.key]?.toString()?.takeIf { it.isNotBlank() }?.let { showSigned(it) }

        return container
    }


    // ── Full-screen landscape signature dialog ─────────────────────────────────

    internal fun openSignatureDialog(fieldLabel: String, onSaved: (String) -> Unit) {
        val dp = context.resources.displayMetrics.density
        val activity = context as? Activity
        val accentGreen = context.getColor(R.color.accent_green)

        val prevOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val dialog = Dialog(context, R.style.Theme_FormioRenderer_SignatureDialog)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        dialog.setOnDismissListener { activity?.requestedOrientation = prevOrientation }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(context.getColor(R.color.bg_primary))
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
        }

        // ── Header ─────────────────────────────────────────────────────────────
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
        }

        // Icon + title block
        val titleBlock = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val iconSize = (36 * dp).toInt()
        val iconBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(30, Color.red(accentGreen), Color.green(accentGreen), Color.blue(accentGreen)))
        }
        val iconFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = (10 * dp).toInt() }
            background = iconBg
        }
        iconFrame.addView(ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.ic_signature)
            setColorFilter(accentGreen)
            val p = (8 * dp).toInt(); setPadding(p, p, p, p)
            scaleType = ImageView.ScaleType.FIT_CENTER
        })
        titleBlock.addView(iconFrame)
        titleBlock.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = fieldLabel
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = "Dibuje su firma con el dedo"
                setTextColor(context.getColor(R.color.text_secondary))
                textSize = 12f
            })
        })
        header.addView(titleBlock)

        // ── Ink color selector ─────────────────────────────────────────────────
        val inkColors = listOf(
            Color.rgb(15, 15, 35) to "Negro",
            Color.rgb(10, 40, 130) to "Azul",
            Color.rgb(100, 10, 10) to "Rojo"
        )
        val colorRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        header.addView(colorRow)

        // ── Signature canvas ───────────────────────────────────────────────────
        val sigView = SignatureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            inkColor = inkColors[0].first
        }
        val sigCard = MaterialCardView(context).apply {
            setCardBackgroundColor(Color.WHITE)
            radius = (12 * dp)
            cardElevation = (6 * dp)
            strokeColor = accentGreen
            strokeWidth = (2 * dp).toInt()
            isClickable = false
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { bottomMargin = (10 * dp).toInt() }
        }
        sigCard.addView(sigView)

        // Wire color dots
        val colorDots = mutableListOf<View>()
        inkColors.forEachIndexed { i, (color, _) ->
            val dotSize = (28 * dp).toInt()
            val dot = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = (6 * dp).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (i == 0) setStroke((2 * dp).toInt(), accentGreen)
                }
                isClickable = true
                isFocusable = true
            }
            dot.setOnClickListener {
                sigView.inkColor = color
                colorDots.forEachIndexed { j, d ->
                    (d.background as? GradientDrawable)?.apply {
                        setStroke(if (j == i) (2 * dp).toInt() else 0, accentGreen)
                    }
                }
            }
            colorDots.add(dot)
            colorRow.addView(dot)
        }

        // ── Bottom action bar ──────────────────────────────────────────────────
        val actionBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(context.getColor(R.color.bg_card))
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.bg_card))
                cornerRadius = (10 * dp)
            }
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        fun makeBtn(label: String, color: Int, onClick: () -> Unit) =
            MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = label
                setTextColor(color)
                textSize = 13f
                insetTop = 0; insetBottom = 0
                minimumHeight = 0; minimumWidth = 0
                setOnClickListener { onClick() }
            }

        val undoBtn = makeBtn("↩ Deshacer", context.getColor(R.color.text_secondary)) { sigView.undo() }
        val zoomResetBtn = makeBtn("⊙ 1×", context.getColor(R.color.accent_green)) { sigView.resetZoom() }

        sigView.onSignatureChanged = { _ ->
            undoBtn.alpha = if (sigView.hasUndo()) 1f else 0.35f
        }
        undoBtn.alpha = 0.35f
        zoomResetBtn.alpha = 0.35f

        // Update zoom reset button visibility when zoom changes via the sigView override
        val origInvalidate = sigView
        sigView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val zoomed = sigView.currentZoom() > 1.05f
            zoomResetBtn.alpha = if (zoomed) 1f else 0.35f
        }

        actionBar.addView(makeBtn("✕ Limpiar", context.getColor(R.color.text_secondary)) { sigView.clear() })
        actionBar.addView(undoBtn)
        actionBar.addView(zoomResetBtn)
        actionBar.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })
        actionBar.addView(makeBtn("Cancelar", context.getColor(R.color.error_color)) { dialog.dismiss() })
        actionBar.addView(MaterialButton(context).apply {
            text = "Guardar"
            setTextColor(context.getColor(R.color.text_on_accent))
            backgroundTintList = context.getColorStateList(R.color.accent_green_selector)
            cornerRadius = (8 * dp).toInt()
            insetTop = (4 * dp).toInt(); insetBottom = (4 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * dp).toInt() }
            setOnClickListener {
                if (sigView.isSignatureEmpty()) {
                    com.genesis.formio.ui.widgets.FormioToast.show(context, "", "Por favor dibuje su firma primero", "warning")
                    return@setOnClickListener
                }
                val bitmap = sigView.getBitmap()
                saveSignatureToGallery(bitmap)
                val path = SignatureStorage.saveSignature(context, bitmap)
                onSaved(path)
                dialog.dismiss()
            }
        })

        root.addView(header)
        root.addView(sigCard)
        root.addView(actionBar)
        dialog.setContentView(root)
        dialog.show()
    }

    fun showSignatureZoom(value: String) {
        com.genesis.formio.ui.widgets.ImageViewerDialog(context).show(value)
    }

    private fun saveSignatureToGallery(bitmap: Bitmap) {
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "firma_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GForms")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        } catch (_: Exception) { }
    }
}
