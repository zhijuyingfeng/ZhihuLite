package org.nigao.zhihuLite.business_ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel

/**
 * Sign-in detection state shared by the WebView callbacks and the Compose effects.
 *
 * A plain state holder rather than a callback-local `var` so the WebView's main-thread callbacks and
 * the Compose effects observe the same snapshotted values, and so success is latched exactly once
 * (the previous unsynchronised `isLoggedIn` flag could double-report completion).
 *
 * It is public — rather than private to `AuthWebView` — for the app module's suite: the precedence
 * rules here are worth pinning down without a WebView (a late load failure must not overwrite a
 * completed sign-in, and a retry has to re-arm *both* gates).
 */
class SignInSession {

    /** Set once any sign-in page or resource is seen; the cookie poll waits for this. */
    var isPageActive by mutableStateOf(false)
        private set

    /** Set when a page finished loading; the load watchdog waits for this and then stops mattering. */
    var isPageLoaded by mutableStateOf(false)
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
    private var loadSignals = Channel<Unit>(Channel.CONFLATED)

    fun onPageActive() {
        isPageActive = true
        pageSignals.trySend(Unit)
    }

    suspend fun awaitPageActive() {
        if (isPageActive) return
        pageSignals.receive()
    }

    fun onPageLoaded() {
        isPageLoaded = true
        loadSignals.trySend(Unit)
    }

    suspend fun awaitPageLoaded() {
        if (isPageLoaded) return
        loadSignals.receive()
    }

    /** Latches the first session cookie; later observations (poll and page callback) are ignored. */
    fun onSessionCookie(cookie: String) {
        if (isCompleted) return
        completedCookie = cookie
        isCompleted = true
        // A failure that arrived first is no longer true.
        errorMessage = null
    }

    /**
     * The page could not be loaded, or never finished loading.
     *
     * Ignored once signed in: a failing resource on the way out must not claim that sign-in failed.
     */
    fun onLoadFailed(message: String) {
        if (!isCompleted) errorMessage = message
    }

    /** Clears the error and re-arms both gates for another explicit attempt. */
    fun resetForRetry() {
        errorMessage = null
        isPageActive = false
        isPageLoaded = false
        pageSignals = Channel(Channel.CONFLATED)
        loadSignals = Channel(Channel.CONFLATED)
    }
}
