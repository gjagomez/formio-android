package com.genesis.formio.util

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
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
    private val context: Context,
    private val onSetField: (key: String, value: String) -> Unit,
    private val onGetField: (key: String) -> String,
    private val onShowMessage: (title: String, msg: String, type: String) -> Unit,
    private val onError: (msg: String) -> Unit,
    private val onWsStart: (() -> Unit)? = null,
    private val onWsEnd: (() -> Unit)? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    internal val webView: WebView = createWebView(context)

    private var ready = false
    private var pendingPayload: String? = null
    @Volatile private var destroyed = false

    // Tracks concurrent in-flight ws calls (main-thread only via mainHandler)
    private var pendingWsCalls = 0

    // Snapshot of form data used to answer getField() calls from JS bridge thread
    @Volatile private var fieldSnapshot: Map<String, String> = emptyMap()

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.addJavascriptInterface(JsBridge(), "Android")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                android.util.Log.d("FormJS", "WebView loaded: $url")
                ready = true
                pendingPayload?.let { runPayload(it) }
                pendingPayload = null
            }
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                android.util.Log.e("FormJS", "WebView error $errorCode: $description for $failingUrl")
                mainHandler.post { onError("WebView no cargó: $description") }
            }
        }
        wv.webChromeClient = object : WebChromeClient() {

            override fun onJsAlert(
                view: WebView?, url: String?, message: String?, result: JsResult
            ): Boolean {
                AlertDialog.Builder(context)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(
                view: WebView?, url: String?, message: String?, result: JsResult
            ): Boolean {
                AlertDialog.Builder(context)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok)     { _, _ -> result.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel()  }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView?, url: String?, message: String?,
                defaultValue: String?, result: JsPromptResult
            ): Boolean {
                val input = EditText(context).apply { setText(defaultValue) }
                AlertDialog.Builder(context)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        result.confirm(input.text.toString())
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
        }
        // Load HTML from assets; fall back to embedded string if asset is missing
        val html = try {
            context.assets.open("form_js_executor.html").bufferedReader().readText()
        } catch (_: Exception) {
            EXECUTOR_HTML
        }
        wv.loadDataWithBaseURL(
            "file:///android_asset/",
            html,
            "text/html",
            "UTF-8",
            null
        )
        return wv
    }

    /**
     * Execute the button's [customJs] with [formData] available as `data` and `row`.
     * Must be called from the main thread.
     */
    fun execute(formData: Map<String, Any?>, customJs: String) {
        fieldSnapshot = formData.mapValues { (_, v) -> v?.toString() ?: "" }
        val payload = buildPayload(formData, customJs)
        if (ready) runPayload(payload) else pendingPayload = payload
    }

    fun attachTo(parent: android.view.ViewGroup) {
        if (webView.parent == null) {
            val lp = android.view.ViewGroup.LayoutParams(1, 1)
            parent.addView(webView, lp)
            webView.visibility = android.view.View.GONE
        }
    }

    fun destroy() {
        destroyed = true
        mainHandler.post {
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        }
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
        val js = "(function(){try{_runButton('$base64')}catch(e){Android.onJsError('WebView: '+(e.message||String(e)))}})()"
        webView.evaluateJavascript(js, null)
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
         * Called by fetch() and app.ws() shims.
         * [callId] lets concurrent requests resolve independently via _wsResult(id, text, isError).
         */
        @JavascriptInterface
        fun wsCall(url: String, method: String, headersJson: String, body: String, callId: Int) {
            // Notify loading start on the main thread
            mainHandler.post {
                if (pendingWsCalls++ == 0) onWsStart?.invoke()
            }
            Thread {
                if (destroyed) {
                    mainHandler.post { if (--pendingWsCalls == 0) onWsEnd?.invoke() }
                    return@Thread
                }
                try {
                    val response = httpCall(url, method, headersJson, body)
                    val encoded = Base64.encodeToString(
                        response.toByteArray(Charsets.UTF_8), Base64.NO_WRAP
                    )
                    if (!destroyed) webView.post {
                        webView.evaluateJavascript(
                            "_wsResult($callId, atob('$encoded'), false)", null
                        )
                    }
                } catch (e: Exception) {
                    val msg = (e.message ?: "Error").replace("'", "\\'").replace("\n", " ")
                    if (!destroyed) webView.post {
                        webView.evaluateJavascript(
                            "_wsResult($callId, '$msg', true)", null
                        )
                    }
                }
                // Delay 500 ms so JS .then() callback can run before overlay hides
                mainHandler.postDelayed({
                    if (!destroyed && --pendingWsCalls == 0) onWsEnd?.invoke()
                }, 500)
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

    companion object {
        // Embedded fallback so the executor works even if the asset isn't packaged
        private val EXECUTOR_HTML = """
<!DOCTYPE html><html><head><meta charset="utf-8"><script>
function moment(d,f){function pad(n){return n<10?'0'+n:''+n}var _d=d?new Date(d):new Date();return{_d:_d,format:function(f){if(!_d||isNaN(_d.getTime()))return'';return f.replace('YYYY',_d.getFullYear()).replace('MM',pad(_d.getMonth()+1)).replace('DD',pad(_d.getDate())).replace('HH',pad(_d.getHours())).replace('mm',pad(_d.getMinutes()))},valueOf:function(){return _d?_d.getTime():0},isAfter:function(o){return this.valueOf()>o.valueOf()},isBefore:function(o){return this.valueOf()<o.valueOf()},isSame:function(o){return this.valueOf()===o.valueOf()}};}
var _httpQueue={},_httpCallId=0;
function _httpCall(url,method,headers,body){return new Promise(function(resolve,reject){var id=++_httpCallId;_httpQueue[id]={resolve:resolve,reject:reject};Android.wsCall(String(url||''),String(method||'get').toLowerCase(),JSON.stringify(typeof headers==='object'&&headers?headers:{}),typeof body==='string'?body:(body?JSON.stringify(body):''),id);});}
function _wsResult(id,rawText,isError){var cb=_httpQueue[id];if(!cb)return;delete _httpQueue[id];if(isError){cb.reject(new Error(rawText));}else{cb.resolve(rawText);}}
window._wsResolve=function(){};window._wsReject=function(){};
window.fetch=function(url,options){options=options||{};return _httpCall(url,(options.method||'GET').toLowerCase(),options.headers||{},options.body||'').then(function(rawText){return{ok:true,status:200,text:function(){return Promise.resolve(rawText);},json:function(){try{return Promise.resolve(JSON.parse(rawText));}catch(e){return Promise.reject(new SyntaxError('Invalid JSON'));}},clone:function(){return this;}};});};
var form={getComponent:function(key){return{setValue:function(val){Android.setField(key,(val===null||val===undefined)?'':String(val));},getValue:function(){return Android.getField(key);},redraw:function(){},triggerChange:function(){}};},refresh:function(){}};
var app={ws:function(settings,method){return _httpCall(settings.url||'',method||'get',settings.headers||{},settings.data||'').then(function(rawText){try{return JSON.parse(rawText);}catch(e){return rawText;}});},mostrarMensaje:function(title,msg,type){Android.showMessage(String(title||''),String(msg||''),String(type||'info'));}};
function _runButton(base64Payload){try{var payload=JSON.parse(atob(base64Payload));var data=payload.data||{};var row=data;var result;try{result=eval('(function(){'+payload.code+'\n})()');}catch(e){Android.onJsError(e.message||String(e));return;}if(result&&typeof result.then==='function'){result.catch(function(e){Android.onJsError(e?(e.message||String(e)):'Error asincrono');});}}catch(e){Android.onJsError(e.message||String(e));}}
</script></head><body></body></html>
        """.trimIndent()
    }
}
