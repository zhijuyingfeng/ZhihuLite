package org.nigao.zhihuLite.business_ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SESSION_COOKIE_NAME = "z_c0"
private const val HOST = "https://www.zhihu.com"

/** How often the cookie jar is inspected for a completed sign-in while the page is open. */
private const val SESSION_POLL_INTERVAL_MS = 500L

/** How long the user may sit on the sign-in page before an actionable error is shown. */
private const val AUTH_TIMEOUT_MS = 30_000L

/**
 * Sign-in WebView with a bounded lifetime and a session-cookie success signal.
 *
 * The previous implementation reported success only when `onLoadResource` saw an exactly equal
 * sign-in URL and the next page finished at exactly `https://www.zhihu.com/`; any redirect carrying
 * a query or fragment, or a differently shaped OAuth flow, left the user on the sign-in page
 * forever with no feedback. This version treats "the cookie jar now contains the `z_c0` session
 * cookie" as the success signal, keeps the page-URL checks only as the trigger for when to look,
 * polls until the cookie appears, and shows a retryable error after [AUTH_TIMEOUT_MS].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthWebView(
    url: String,
    onAuthComplete: (String) -> Unit,
    modifier: Modifier,
    timeoutMessage: String? = null,
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
                        onSessionCookie = { session.onSessionCookie(it) },
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

        // Bounded poll for the session cookie. It starts once the page flow says a sign-in is
        // underway; `attempt` changes only on retry, so page navigations cannot keep pushing the
        // timeout out and hide a stuck flow.
        LaunchedEffect(attempt) {
            withTimeoutOrNull(AUTH_TIMEOUT_MS) {
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
            if (!session.isCompleted) {
                // Written from inside the effect, but the effect is keyed on `attempt`, so this does
                // not restart the poll and can never trip Compose's self-invalidating-effect guard.
                session.onTimeout(timeoutMessage ?: "Sign-in timed out. Please try again.")
                onLog("Sign-in timed out waiting for the session cookie")
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
 * Sign-in detection state shared by the WebView callbacks and the polling effects.
 *
 * A plain state holder rather than a callback-local `var` so the WebView's main-thread callbacks and
 * the Compose effects observe the same snapshotted values, and so success is latched exactly once
 * (the previous unsynchronised `isLoggedIn` flag could double-report completion).
 */
private class SignInSession {

    /** Set once any sign-in page or resource is seen; the polling effect waits for this. */
    var isPageActive by mutableStateOf(false)
        private set

    /** Set when a session cookie was observed; the hand-off effect is keyed on this. */
    var isCompleted by mutableStateOf(false)
        private set

    /** User-visible failure text, non-null only while the error overlay should show. */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** The first session cookie observed, handed to the caller exactly once. */
    var completedCookie: String? by mutableStateOf(null)
        private set

    private var pageSignals = Channel<Unit>(Channel.CONFLATED)

    fun onPageActive() {
        isPageActive = true
        pageSignals.trySend(Unit)
    }

    suspend fun awaitPageActive() {
        if (isPageActive) return
        pageSignals.receive()
    }

    /** Latches the first session cookie; later observations (poll and page callback) are ignored. */
    fun onSessionCookie(cookie: String) {
        if (isCompleted) return
        completedCookie = cookie
        isCompleted = true
        errorMessage = null
    }

    fun onTimeout(message: String) {
        if (!isCompleted) errorMessage = message
    }

    /** Clears the error and re-arms the page signal for another explicit attempt. */
    fun resetForRetry() {
        errorMessage = null
        isPageActive = false
        pageSignals = Channel(Channel.CONFLATED)
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
    private val onSessionCookie: (String) -> Unit,
) : WebViewClient() {

    private var signInSeen = false

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (isSignInUrl(url)) {
            signInSeen = true
            onPageActive()
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
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
        if (request?.isForMainFrame == true) {
            onLog("Sign-in page failed to load: ${error?.description}")
        }
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
