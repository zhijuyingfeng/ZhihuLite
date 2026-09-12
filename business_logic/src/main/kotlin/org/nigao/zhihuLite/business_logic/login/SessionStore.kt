package org.nigao.zhihuLite.business_logic.login

import android.webkit.CookieManager
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.github.aakira.napier.Napier
import org.nigao.zhihuLite.business_logic.login.data.CredentialStore
import org.nigao.zhihuLite.business_logic.login.data.EncryptedCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth for the Zhihu session.
 *
 * Before this class existed the session lived in two places at once: the persisted cookie string in
 * multiplatform-settings and the live cookie in the Android [CookieManager] used by the network
 * layer. The two could disagree after process death (the WebView cookie store was never flushed,
 * while the app still booted "logged in" from SharedPreferences), which produced requests signed
 * with an empty `d_c0` and a permanent loading spinner. Every reader/writer now goes through here.
 *
 * The persisted cookie is the authoritative copy because it survives process death without relying
 * on the WebView cookie store; [cookieOrEmpty] additionally repairs the persisted copy from the
 * live cookie store when the persisted copy is missing or is not a real session.
 *
 * The credential now lives in a [CredentialStore] (encrypted, Keystore-backed) instead of plain
 * `SharedPreferences`. Installs that signed in before that change are migrated automatically on
 * first read, then the plaintext copy is deleted — reading the old location without deleting it
 * would leave the very thing we are trying to remove on disk.
 */
object SessionStore {

    /**
     * Key the cookie used to be stored under, in plaintext `Settings()`. Read once for migration
     * and then removed; public so a migration test can assert against the same name.
     */
    const val LEGACY_COOKIE_KEY = "Cookie"

    /** Backwards-compatible alias used internally. */
    private const val COOKIE_KEY = LEGACY_COOKIE_KEY

    /** Zhihu session cookie; its presence is the only reliable "logged in" signal. */
    private const val SESSION_COOKIE_NAME = "z_c0"

    private const val HOST = "https://www.zhihu.com"

    /**
     * Backing store. Replaceable so the session state machine can be exercised in a JVM test, and
     * so the encryption choice stays reversible.
     *
     * Set by [DefaultApplication] once the process starts; until then reads degrade to "no session"
     * instead of touching Android APIs that are unavailable in a unit test.
     */
    @Volatile
    var credentialStore: CredentialStore? = null

    /**
     * Legacy plaintext location (the old `Settings()`-backed `"Cookie"` entry). Read once for
     * migration, then cleared; nothing writes here any more.
     *
     * Read and clear are one abstraction on purpose: when they were separate, the clear path went
     * straight to `Settings()` and therefore could not be substituted — which meant "the plaintext
     * copy is actually deleted" was untestable, and that is precisely the property this change
     * exists to guarantee.
     */
    @Volatile
    var legacyStore: LegacyCookieStore? = null

    /** The store in use, created on demand; null when no Android context is available. */
    private fun store(): CredentialStore? = credentialStore

    private val _state = MutableStateFlow<SessionState>(SessionState.LoggedOut)

    /**
     * Observable session state, consumed by [org.nigao.zhihuLite.App] to route back to login when
     * the network layer reports an expired/refused session, and by future UI that wants to react to
     * logout. It is seeded immediately, so collectors never wait for a first emission.
     */
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        // Defaults; DefaultApplication overrides the credential store with the encrypted one.
        if (legacyStore == null) legacyStore = SettingsLegacyCookieStore()
    }

    /**
     * Returns the persisted cookie string, or `""` when there is nothing usable stored.
     *
     * Never throws. The multiplatform-settings `Settings()` factory dereferences the context that
     * androidx.startup installs, so calling it before `Application.onCreate` (Compose Preview,
     * plain JVM unit tests) used to crash with a `NullPointerException`; that path now degrades to
     * "no session" instead of taking the caller down.
     *
     * As a side effect this repairs a persisted cookie that is missing, or that is not a real
     * session, from the live WebView cookie store. That repair is what stops an install from
     * booting "logged in" from SharedPreferences with nothing usable to sign requests with.
     */
    fun cookieOrEmpty(): String {
        if (_state.value is SessionState.Invalid) return ""
        val stored = persistedCookie()
        if (stored != null && stored.containsSessionCookie()) return stored
        return repairFromWebViewCookies() ?: ""
    }

    /**
     * `true` when a usable session cookie is present.
     *
     * A stored string that does not contain the `z_c0` session cookie is not treated as a session:
     * it cannot sign the `x-zse-96` header, so believing it would boot the UI into a feed that never
     * loads. When the stored copy is unusable this also repairs itself from the live WebView cookie
     * store, so an already-logged-in install is not forced through the sign-in page again.
     */
    fun isLoggedIn(): Boolean = cookieOrEmpty().containsSessionCookie()

    /**
     * Persists a freshly obtained session cookie and marks the session valid.
     *
     * The caller is expected to have flushed the WebView cookie store first ([AuthWebView] does
     * this); persistence here is what makes the session survive process death.
     */
    fun saveSession(cookie: String) {
        require(cookie.isNotBlank()) { "Refusing to persist an empty session cookie" }
        writeCookie(cookie)
    }

    /**
     * Clears the persisted session and the live WebView cookie store, then publishes
     * [SessionState.LoggedOut].
     *
     * Both halves matter: clearing only the persisted copy would leave the WebView cookie journal
     * able to resurrect the session through [cookieOrEmpty]'s repair path (or keep sending it on
     * feed requests), and clearing only the WebView would leave the next launch booting logged in.
     */
    fun clearSession() {
        clearPersistedCookie()
        clearWebViewCookies()
    }

    /**
     * Full logout: [clearSession] plus a cookie flush so the removal reaches disk before the process
     * can be killed.
     */
    fun logout() {
        clearSession()
        flushWebViewCookies()
    }

    /**
     * Marks the session as invalid at the supplied [reason] and publishes [SessionState.Invalid].
     *
     * This is the contract for the network layer: call it on HTTP 401/403, or when a response is
     * not JSON because the API answered with a login wall. The persisted cookie is deliberately
     * left in place (a transient 403 must not silently delete credentials) but [isLoggedIn] reports
     * `false` while invalidated, so nothing keeps signing requests as if the session were good.
     * [beginLogin] or [saveSession] clears the flag.
     */
    fun invalidate(reason: String) {
        Napier.w("Session invalid: $reason")
        _state.value = SessionState.Invalid(reason)
    }

    /** Clears a previous [invalidate] before a new sign-in attempt, returning to [SessionState.LoggedOut]. */
    fun beginLogin() {
        if (_state.value !is SessionState.LoggedIn) {
            _state.value = SessionState.LoggedOut
        }
    }

    /**
     * Restores the live WebView cookie jar from the persisted cookie.
     *
     * Utility for flows that need to rehydrate a WebView from the authoritative cookie (for example
     * a future in-app article reader) after the cookie journal was lost. Not used by the sign-in
     * screen: that path clears cookies instead, so a stale session cannot masquerade as a new login.
     */
    fun syncWebViewCookies() {
        val cookie = cookieOrEmpty()
        if (cookie.isBlank()) return
        try {
            CookieManager.getInstance().setCookie(HOST, cookie)
        } catch (e: Exception) {
            Napier.w("Could not sync cookies into the WebView: ${e.message}")
        }
    }

    /**
     * Persists the live WebView cookie when it is a real session and the persisted copy is not;
     * returns that cookie, or `null` when the cookie store has nothing usable either.
     */
    private fun repairFromWebViewCookies(): String? {
        val live = cookieFromWebView() ?: return null
        if (!live.containsSessionCookie()) return null
        if (live != persistedCookie()) writeCookie(live)
        return live
    }

    /** Clears every cookie (and any running cookie writes) from the WebView cookie store. */
    private fun clearWebViewCookies() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
        } catch (e: Exception) {
            Napier.w("Could not clear WebView cookies: ${e.message}")
        }
    }

    /** Flushes the WebView cookie journal so cookie writes/removals survive process death. */
    fun flushWebViewCookies() {
        try {
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            Napier.w("Could not flush WebView cookies: ${e.message}")
        }
    }

    private fun cookieFromWebView(): String? = try {
        CookieManager.getInstance().getCookie(HOST)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun persistedCookie(): String? {
        val store = store() ?: return null
        store.read()?.takeIf { it.isNotBlank() }?.let { return it }
        // Nothing (usable) in the encrypted store: migrate a pre-encryption install, if any.
        return migrateLegacyCookie(store)
    }

    /**
     * Moves a plaintext cookie written by an older version into the encrypted store.
     *
     * Returns the cookie so this read behaves as if the migration had always been in place, and
     * removes the plaintext entry so the credential does not remain readable on disk.
     */
    private fun migrateLegacyCookie(store: CredentialStore): String? {
        val legacy = legacyStore?.read()
        if (legacy.isNullOrBlank() || !legacy.containsSessionCookie()) return null

        Napier.i("Migrating a plaintext session cookie into encrypted storage")
        store.write(legacy)
        clearLegacyCookie()
        return legacy
    }

    private fun clearLegacyCookie() {
        // Not fatal if this fails: the encrypted copy is already in place. Worst case the plaintext
        // entry survives until the next successful write clears it.
        legacyStore?.clear()
    }

    private fun writeCookie(cookie: String) {
        val store = store()
        if (store == null) {
            Napier.w("No credential store available; the session will not be persisted")
            _state.value = SessionState.LoggedIn(cookie)
            return
        }
        store.write(cookie)
        clearLegacyCookie()
        _state.value = SessionState.LoggedIn(cookie)
    }

    private fun clearPersistedCookie() {
        store()?.clear()
        clearLegacyCookie()
        _state.value = SessionState.LoggedOut
    }


    /** Test seam: restores the defaults so a JVM test can drive the state machine with fakes. */
    /** Test seam: restores the defaults so a JVM test can drive the state machine with fakes. */
    fun resetForTest(
        store: CredentialStore? = null,
        legacy: LegacyCookieStore? = null,
    ) {
        credentialStore = store
        legacyStore = legacy ?: SettingsLegacyCookieStore()
        _state.value = SessionState.LoggedOut
    }

    private fun String.containsSessionCookie(): Boolean =
        split(';').any { it.trimStart().startsWith("$SESSION_COOKIE_NAME=") }
}

/**
 * The pre-encryption credential location: one plaintext `Settings()` entry.
 *
 * Implemented as an interface so a test can substitute it and assert that the plaintext copy is
 * really removed after migration.
 */
interface LegacyCookieStore {
    fun read(): String?
    fun clear()
}

/**
 * Real [LegacyCookieStore]: the plaintext `Settings()` entry used before credentials were encrypted.
 *
 * Every method swallows failures: before `Application.onCreate` the multiplatform-settings context
 * does not exist (Compose Preview, JVM tests), and a missing legacy cookie must simply mean "nothing
 * to migrate".
 */
class SettingsLegacyCookieStore : LegacyCookieStore {
    override fun read(): String? = try {
        Settings().get<String>(SessionStore.LEGACY_COOKIE_KEY)
    } catch (e: Exception) {
        null
    }

    override fun clear() {
        try {
            Settings().remove(SessionStore.LEGACY_COOKIE_KEY)
        } catch (e: Exception) {
            Napier.w("Could not remove the legacy cookie: ${e.message}")
        }
    }
}

/**
 * What the app currently believes about the session.
 *
 * A sealed hierarchy rather than a pair of booleans so "invalid" (was logged in, server refused)
 * can never be confused with "logged out" (never signed in) by whoever renders the start screen.
 */
sealed interface SessionState {

    /** No usable cookie is persisted. */
    data object LoggedOut : SessionState

    /** A cookie containing the `z_c0` session cookie is persisted; [cookie] is the raw string. */
    data class LoggedIn(val cookie: String) : SessionState

    /**
     * The stored session was rejected (HTTP 401/403 or a login-wall body). [reason] is for logging
     * and future user-facing copy; the persisted cookie is kept until a new login overwrites it.
     */
    data class Invalid(val reason: String) : SessionState
}
