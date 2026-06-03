package com.genesis.formio.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * Generic JavaScript executor for formio `custom` button actions.
 *
 * Loads a headless WebView with shims for `form`, `app`, `row`, `data`, and `moment`.
 * Any JS that calls form.getComponent("key").setValue(value) is bridged back to the
 * native FormEngine via [onSetField]. Any app.ws() call makes a real HTTPS request.
 *
 * Usage:
 *   val executor = FormJsExecutor(context, onSetField, onGetField, onShowMessage, onError)
 *   executor.execute(formData, buttonCustomJs)
 *   // call executor.destroy() in onDestroyView
 */
class FormJsExecutor(
    context: Context,
    private val onSetField: (key: String, value: String) -> Unit,
    private val onGetField: (key: String) -> String,
    private val onShowMessage: (title: String, msg: String, type: String) -> Unit,
    private val onError: (msg: String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val webView: WebView = createWebView(context)

    private var ready = false
    private var pendingPayload: String? = null
    @Volatile private var destroyed = false

    // Snapshot of form data used to answer getField() calls from JS bridge thread
    @Volatile private var fieldSnapshot: Map<String, String> = emptyMap()

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.addJavascriptInterface(JsBridge(), "Android")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                ready = true
                pendingPayload?.let { runPayload(it) }
                pendingPayload = null
            }
        }
        wv.loadUrl("file:///android_asset/form_js_executor.html")
        return wv
    }

    /**
     * Execute the button's [customJs] with [formData] available as `data` and `row`.
     * Must be called from the main thread.
     */
    fun execute(formData: Map<String, Any?>, customJs: String) {
        // Build a safe string snapshot for cross-thread getField access
        fieldSnapshot = formData.mapValues { (_, v) -> v?.toString() ?: "" }

        val payload = buildPayload(formData, customJs)
        if (ready) runPayload(payload) else pendingPayload = payload
    }

    fun destroy() {
        destroyed = true
        webView.destroy()
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private fun buildPayload(formData: Map<String, Any?>, code: String): String {
        val dataObj = JSONObject()
        formData.forEach { (key, value) ->
            when (value) {
                null      -> dataObj.put(key, "")
                is Boolean -> dataObj.put(key, value)
                is Int, is Long -> dataObj.put(key, value)
                is Double, is Float -> dataObj.put(key, value)
                else      -> dataObj.put(key, value.toString())
            }
        }
        val json = JSONObject()
            .put("data", dataObj)
            .put("code", code)
            .toString()
        // Base64-encode so the string is safe to inject into a JS single-quoted literal
        return Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun runPayload(base64: String) {
        webView.evaluateJavascript("_runButton('$base64')", null)
    }

    // ── JS ↔ Kotlin bridge ────────────────────────────────────────────────────

    inner class JsBridge {

        /** Called by form.getComponent(key).setValue(value) */
        @JavascriptInterface
        fun setField(key: String, value: String) {
            mainHandler.post { onSetField(key, value) }
        }

        /** Called by form.getComponent(key).getValue() */
        @JavascriptInterface
        fun getField(key: String): String = fieldSnapshot[key] ?: ""

        /**
         * Called by app.ws(settings, method).
         * Makes an HTTPS request on a background thread, then calls _wsResolve / _wsReject
         * to resume the JS Promise.
         */
        @JavascriptInterface
        fun wsCall(url: String, method: String, headersJson: String, body: String) {
            Thread {
                if (destroyed) return@Thread
                try {
                    val response = httpCall(url, method, headersJson, body)
                    val encoded = Base64.encodeToString(
                        response.toByteArray(Charsets.UTF_8), Base64.NO_WRAP
                    )
                    if (!destroyed) webView.post {
                        webView.evaluateJavascript(
                            "window._wsResolve(JSON.parse(atob('$encoded')))", null
                        )
                    }
                } catch (e: Exception) {
                    val msg = (e.message ?: "Error").replace("'", "\\'")
                    if (!destroyed) webView.post {
                        webView.evaluateJavascript("window._wsReject('$msg')", null)
                    }
                }
            }.start()
        }

        /** Called by app.mostrarMensaje(title, msg, type) */
        @JavascriptInterface
        fun showMessage(title: String, msg: String, type: String) {
            mainHandler.post { onShowMessage(title, msg, type) }
        }

        /** Called when uncaught JS exception occurs inside eval() */
        @JavascriptInterface
        fun onJsError(message: String) {
            android.util.Log.e("FormJS", "JS error in button custom: $message")
            mainHandler.post { onError(message) }
        }
    }

    // ── HTTP client ───────────────────────────────────────────────────────────

    private val sslCtx: SSLContext by lazy {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, t: String?) {}
            override fun checkServerTrusted(c: Array<out X509Certificate>?, t: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        SSLContext.getInstance("TLS").also { it.init(null, trustAll, SecureRandom()) }
    }

    private fun httpCall(
        url: String,
        method: String,
        headersJson: String,
        body: String
    ): String {
        val conn = (URL(url).openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = sslCtx.socketFactory
            hostnameVerifier = HostnameVerifier { _, _ -> true }
            requestMethod = method.uppercase()
            connectTimeout = 15_000
            readTimeout = 20_000

            val headers = runCatching { JSONObject(headersJson) }.getOrNull()
            headers?.keys()?.forEach { k -> setRequestProperty(k, headers.getString(k)) }

            if (body.isNotEmpty() && method != "get") {
                doOutput = true
                outputStream.use { os ->
                    OutputStreamWriter(os, Charsets.UTF_8).use { it.write(body) }
                }
            }
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: "{}"
    }
}
