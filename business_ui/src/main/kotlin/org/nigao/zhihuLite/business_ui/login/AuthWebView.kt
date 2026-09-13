package org.nigao.zhihuLite.business_ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SESSION_COOKIE_NAME = "z_c0"
private const val HOST = "https://www.zhihu.com"

/** How often the cookie jar is inspected for a completed sign-in while the page is open. */
private const val SESSION_POLL_INTERVAL_MS = 500L

/** How long the sign-in page may take to load before the blank screen is called out as a failure. */
private const val AUTH_LOAD_TIMEOUT_MS = 30_000L

/**
 * Sign-in WebView with a bounded lifetime and a session-cookie success signal.
 *
 * The previous implementation reported success only when `onLoadResource` saw an exactly equal
 * sign-in URL and the next page finished at exactly `https://www.zhihu.com/`; any redirect carrying
 * a query or fragment, or a differently shaped OAuth flow, left the user on the sign-in page
 * forever with no feedback. This version treats "the cookie jar now contains the `z_c0` session
 * cookie" as the success signal, keeps the page-URL checks only as the trigger for when to look, and
 * polls until the cookie appears however long that takes.
 *
 * The only timer left is on the *load*: signing in is something the reader does at their own pace
 * (typing a password, waiting for an SMS, scanning a code), so a clock on it fires at innocent
 * people — while a load that simply stalls is the case a timer is genuinely good for, because there
 * is nothing else to observe. Once a page has loaded the reader may take as long as they like; a page
 * that fails to load reports itself through `WebViewClient` and shows the same retryable error.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthWebView(
    url: String,
    onAuthComplete: (String) -> Unit,
    modifier: Modifier,
    loadErrorMessage: String? = null,
    retryLabel: String? = null,
    onLog: (String) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val session = remember { SignInSession() }
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }
    // Only increments on an explicit retry; page navigations must not reset the timeout clock.
    var attempt by remember { mutableIntStateOf(0) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.configureForSignIn()
                    webViewClient = AuthWebViewClient(
                        onLog = onLog,
                        onPageActive = { session.onPageActive() },
                        onPageLoaded = { session.onPageLoaded() },
                        onSessionCookie = { session.onSessionCookie(it) },
                        onLoadFailed = { session.onLoadFailed(it) },
                    )
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    webViewHolder.value = this
                    loadUrl(url)
                }
            },
            onRelease = { webView ->
                // AndroidView keeps the WebView alive while this composable is on screen; without
                // this cleanup every visit to the sign-in page leaked a WebView and its renderer.
                webViewHolder.value = null
                webView.stopLoading()
                webView.webViewClient = WebViewClient()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.clearCache(true)
                webView.removeAllViews()
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            },
        )

        // The cookie poll. It starts once the page flow says a sign-in is underway — that gate also
        // keeps a stale cookie from being mistaken for a fresh one while the sign-in screen clears
        // the old session — and then runs until the session cookie appears. No deadline: how long
        // signing in takes is the reader's business.
        LaunchedEffect(attempt) {
            session.awaitPageActive()
            while (!session.isCompleted) {
                val cookie = CookieManager.getInstance().getCookie(HOST)
                if (cookie != null && cookie.containsSessionCookie()) {
                    session.onSessionCookie(cookie)
                    break
                }
                delay(SESSION_POLL_INTERVAL_MS)
            }
        }

        // A page that never finishes loading would leave a blank WebView with no way forward, so that
        // one case keeps a deadline. It is re-armed by an explicit retry, and once any page has loaded
        // it no longer applies.
        LaunchedEffect(attempt) {
            withTimeoutOrNull(AUTH_LOAD_TIMEOUT_MS) { session.awaitPageLoaded() }
            if (!session.isPageLoaded) {
                session.onLoadFailed(
                    loadErrorMessage ?: "Could not load the sign-in page. Check your connection.",
                )
                onLog("Sign-in page did not finish loading within ${AUTH_LOAD_TIMEOUT_MS}ms")
            }
        }

        // Separate keyed on the latched completion, not on this effect's own writes. Flushes the
        // cookie journal first so a process death right after login cannot lose the session, then
        // hands the cookie off from a scope that outlives this composition.
        LaunchedEffect(session.isCompleted, session.completedCookie) {
            val cookie = session.completedCookie
            if (!session.isCompleted || cookie.isNullOrBlank()) return@LaunchedEffect
            CookieManager.getInstance().flush()
            onLog("Completing sign-in with the session cookie")
            coroutineScope.launch { onAuthComplete(cookie) }
        }

        session.errorMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(16.dp),
            ) {
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (retryLabel != null) {
                Button(
                    onClick = {
                        session.resetForRetry()
                        webViewHolder.value?.reload()
                        attempt++
                    },
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(retryLabel)
                }
            }
        }
    }
}

/**
 * Drives sign-in detection for [AuthWebView].
 *
 * Success needs the `z_c0` session cookie; the URL checks only decide *when* it is worth looking for
 * that cookie, which removes the exact-URL-equality failure mode described on [AuthWebView]. All
 * mutable state here is confined to the WebView's main thread, where the callbacks run.
 */
private class AuthWebViewClient(
    private val onLog: (String) -> Unit,
    private val onPageActive: () -> Unit,
    private val onPageLoaded: () -> Unit,
    private val onSessionCookie: (String) -> Unit,
    private val onLoadFailed: (String) -> Unit,
) : WebViewClient() {

    private var signInSeen = false

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (isSignInUrl(url)) {
            signInSeen = true
            onPageActive()
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        onPageLoaded()
        if (isSignInUrl(url)) signInSeen = true
        // Fallback for flows where the sign-in request itself is never observed: once the user is
        // back on the home page, or sign-in was seen at all, check the cookie jar.
        if (signInSeen || isHomeUrl(url)) {
            onPageActive()
            completeIfSessionCookie(url)
        }
    }

    override fun onLoadResource(view: WebView?, url: String?) {
        // Prefix match rather than equality: Zhihu appends query strings and fragments.
        if (isSignInUrl(url)) {
            signInSeen = true
            onPageActive()
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        // Only the document itself: a failing image or script does not stop anyone signing in.
        if (request?.isForMainFrame != true) return
        val description = error?.description?.toString().orEmpty()
        onLog("Sign-in page failed to load: $description")
        onLoadFailed(description)
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        if (request?.isForMainFrame != true) return
        val statusCode = errorResponse?.statusCode ?: return
        if (statusCode < 400) return
        onLog("Sign-in page returned HTTP $statusCode")
        onLoadFailed("HTTP $statusCode")
    }

    private fun completeIfSessionCookie(url: String?) {
        val cookie = CookieManager.getInstance().getCookie(url ?: HOST) ?: return
        if (!cookie.containsSessionCookie()) return
        onSessionCookie(cookie)
    }

    private fun isSignInUrl(url: String?): Boolean =
        url?.contains("oauth/sign_in") == true || url?.contains("/signin") == true

    private fun isHomeUrl(url: String?): Boolean = url?.trimEnd('/') == HOST
}

private fun String.containsSessionCookie(): Boolean =
    split(';').any { it.trimStart().startsWith("$SESSION_COOKIE_NAME=") }

/**
 * Cookie acceptance and hardening for the sign-in flow.
 *
 * `setAcceptCookie(true)` is re-asserted because some devices/OEM builds ship it disabled, which
 * makes sign-in appear to succeed while no cookie is ever stored. Third-party cookies are required
 * because sign-in page resources are cross-site; that call is valid from API 21 and the app's
 * minSdk is 26, so no version guard is needed. File access, form autofill, mixed content and
 * geolocation are explicitly off because the sign-in page does not need them.
 */
private fun WebSettings.configureForSignIn() {
    javaScriptEnabled = true
    // domStorageEnabled covers the localStorage/sessionStorage the sign-in page uses; the older
    // databaseEnabled (WebSQL) is deprecated and unnecessary.
    domStorageEnabled = true
    allowFileAccess = false
    allowContentAccess = false
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    mediaPlaybackRequiresUserGesture = true
    setSupportZoom(false)
    builtInZoomControls = false
    displayZoomControls = false
}
