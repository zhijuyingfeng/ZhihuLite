package org.nigao.zhihu_lite.utils.JsEvaluator

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import android.webkit.WebSettings
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resumeWithException

actual object JsEvaluator {
    private var webViewRef = WeakReference<WebView>(null)
    private val requestChannel = Channel<JsRequest>(Channel.UNLIMITED)
    private var coroutineScope: CoroutineScope? = null
    private var initializationLatch: CompletableJob? = null

    suspend fun initialize(application: Application) = coroutineScope {
        if (coroutineScope != null) return@coroutineScope

        initializationLatch = Job()

        coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + initializationLatch!!)

        withContext(Dispatchers.Main) {
            if (webViewRef.get() == null) {
                val webView = WebView(application).apply {
                    with(settings) {
                        @SuppressLint("SetJavaScriptEnabled")
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            coroutineScope!!.launch {
                                requestChannel.consumeEach { request ->
                                    try {
                                        val result = executeJavaScript(request.code)
                                        request.continuation.resume(result) { cause, _, _ -> }
                                    } catch (e: Exception) {
                                        request.continuation.resumeWithException(e)
                                    }
                                }
                            }
                        }
                    }

                    loadUrl("https://www.zhihu.com")
                }
                webViewRef = WeakReference(webView)
            }
        }
    }

    private suspend fun executeJavaScript(jsCode: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val webView = webViewRef.get()?: run {
                continuation.resumeWithException(IllegalStateException("WebView not initialized"))
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                webView.stopLoading()
            }
            webView.evaluateJavascript(jsCode) { result ->
                try {
                    if (result == null) {
                        continuation.resumeWithException(NullPointerException("WebView returned null"))
                        return@evaluateJavascript
                    }
                    val processed = result
                        .removeSurrounding("\"")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                    if (processed.startsWith("{\"error\":\"")) {
                        val error = processed.substringAfter("\"error\":\"").substringBefore("\"")
                        continuation.resumeWithException(IllegalStateException("JS Error: $error"))
                    } else {
                        continuation.resume(processed) { cause, _, _ -> }
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    actual suspend fun evaluate(script: String): String {
        return suspendCancellableCoroutine { continuation ->
            if (coroutineScope == null || !coroutineScope!!.isActive) {
                continuation.resumeWithException(IllegalStateException("Singleton not initialized"))
                return@suspendCancellableCoroutine
            }
            requestChannel.trySend(JsRequest(script, continuation))
        }
    }

    fun destroy() {
        coroutineScope?.cancel("Singleton destroyed")
        coroutineScope = null
        initializationLatch?.cancel()

        webViewRef.get()?.destroy()
        webViewRef.clear()
    }

    private data class JsRequest(
        val code: String,
        val continuation: CancellableContinuation<String>
    )
}
