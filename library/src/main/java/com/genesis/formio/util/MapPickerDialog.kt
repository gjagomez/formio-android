package com.genesis.formio.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import com.genesis.formio.R

private class MapJsInterface(
    private val mainHandler: Handler,
    private val dialog: Dialog,
    private val onConfirm: (Double, Double) -> Unit
) {
    @JavascriptInterface
    fun onConfirm(lat: Double, lng: Double) {
        mainHandler.post {
            dialog.dismiss()
            onConfirm(lat, lng)
        }
    }
}

class MapPickerDialog(
    private val activity: Activity,
    private val initialLat: Double? = null,
    private val initialLng: Double? = null,
    private val onConfirm: (lat: Double, lng: Double) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun show() {
        val dialog = Dialog(activity, R.style.Theme_FormioRenderer_SignatureDialog)
        dialog.setCancelable(true)

        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#0F0F0F"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ── Close button (top-right) ───────────────────────────────────────────
        val dp = activity.resources.displayMetrics.density
        val btnClose = ImageButton(activity).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#F0F0F0"))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener { dialog.dismiss() }
        }
        val closeLp = FrameLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = (8 * dp).toInt()
            marginEnd = (8 * dp).toInt()
        }

        // ── WebView ────────────────────────────────────────────────────────────
        val webView = WebView(activity).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = true
            }
            setBackgroundColor(Color.parseColor("#0F0F0F"))
            webChromeClient = WebChromeClient()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ── JS interface: Android.onConfirm(lat, lng) ──────────────────────────
        webView.addJavascriptInterface(
            MapJsInterface(mainHandler, dialog, onConfirm),
            "Android"
        )

        var pageReady = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                // If we already have initial coordinates, center on them
                val lat = initialLat ?: 14.6349
                val lng = initialLng ?: -90.5069
                view?.evaluateJavascript("initLocation($lat, $lng)", null)

                // If no initial coords were passed, get GPS and update the map
                if (initialLat == null) {
                    GeolocationHelper(activity).getCurrentLocation(
                        onResult = { gLat, gLng ->
                            mainHandler.post {
                                view?.evaluateJavascript("updateGps($gLat, $gLng)", null)
                            }
                        },
                        onError = { /* map stays at default Guatemala center */ }
                    )
                }
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                // Tiles may fail offline — the map UI itself is bundled locally, so this is non-fatal
                android.util.Log.w("MapPicker", "WebView error $errorCode: $description")
            }
        }

        webView.loadUrl("file:///android_asset/map_picker.html")

        root.addView(webView)
        root.addView(btnClose, closeLp)

        dialog.setContentView(root)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.show()
    }
}
