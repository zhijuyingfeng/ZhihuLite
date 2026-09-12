package org.nigao.zhihuLite.business_ui.login

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get

/**
 * Public facade over [SessionStore], kept so existing call sites (`EventReporter`, `App`,
 * `LogInScreen`) keep compiling while the store implementation can change freely.
 *
 * Everything here delegates to the store: the session must have exactly one owner, otherwise the
 * persisted cookie and the live WebView cookie silently drift apart again.
 */
object LogInManager {

    /**
     * `true` when a usable session is available, i.e. a persisted cookie carrying the `z_c0`
     * session cookie. Delegates to [SessionStore.isLoggedIn] so a stored-but-unusable cookie (which
     * would sign every request with an empty `d_c0` and spin forever) is not reported as logged in.
     */
    fun isLoggedIn(): Boolean = SessionStore.isLoggedIn()

    /**
     * Persists the cookie obtained from the sign-in WebView; see [SessionStore.saveSession].
     */
    fun logIn(cookie: String) {
        require(cookie.isNotBlank())
        SessionStore.saveSession(cookie)
    }

    /**
     * Returns the persisted session cookie, or `""` when there is none. Read the session through
     * here (or [SessionStore.cookieOrEmpty]) instead of calling the settings factory directly, so
     * the call cannot crash before the settings context is initialised.
     */
    fun cookie(): String = SessionStore.cookieOrEmpty()

    /**
     * Logs the user out: clears the persisted session (backing store) and the WebView cookie store,
     * then publishes [SessionState.LoggedOut]. Callers are responsible for navigating to
     * the sign-in destination afterwards, or may simply navigate to the logout destination, which
     * performs both.
     */
    fun logOut() {
        SessionStore.logout()
    }

    /**
     * Flags the session as rejected by the server; see [SessionStore.invalidate]. Exposed here so
     * the network layer has one obvious entry point without depending on the store directly.
     */
    fun invalidate(reason: String) {
        SessionStore.invalidate(reason)
    }
}
