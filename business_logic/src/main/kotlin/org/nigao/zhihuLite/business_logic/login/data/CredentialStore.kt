package org.nigao.zhihuLite.business_logic.login.data

/**
 * Stores the session credential.
 *
 * Exists as an interface for two reasons:
 *  1. the backing store must be swappable — the previous implementation wrote the session cookie to
 *     plain `SharedPreferences` (verified: `Settings()` on Android is
 *     `getSharedPreferences("<pkg>_preferences", 0)`), so anyone able to read the app's private
 *     directory could take over the account;
 *  2. login logic must be testable without an Android runtime — [InMemoryCredentialStore] and a
 *     fake let the session state machine run in a plain JVM test.
 *
 * Every method must be exception-safe: a credential store that throws on a corrupted or
 * undecryptable payload would make the app unusable until reinstall, so a failed read returns
 * `null` (treated as "not logged in") and a failed write is not allowed to crash the caller.
 */
interface CredentialStore {
    /** The stored session cookie, or null when absent/undecryptable. */
    fun read(): String?

    /** Persist [cookie], replacing any previous value. */
    fun write(cookie: String)

    /** Remove the stored credential, if any. */
    fun clear()
}

/**
 * Non-persistent [CredentialStore] for tests and previews.
 *
 * Also the natural target for a future "stay signed out for this run" mode.
 */
class InMemoryCredentialStore(initial: String? = null) : CredentialStore {
    private var value: String? = initial

    override fun read(): String? = value

    override fun write(cookie: String) {
        value = cookie
    }

    override fun clear() {
        value = null
    }
}
