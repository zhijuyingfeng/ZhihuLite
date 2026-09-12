package org.nigao.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_ui.login.CredentialStore
import org.nigao.zhihuLite.business_ui.login.InMemoryCredentialStore
import org.nigao.zhihuLite.business_ui.login.LegacyCookieStore
import org.nigao.zhihuLite.business_ui.login.SessionState
import org.nigao.zhihuLite.business_ui.login.SessionStore
import org.robolectric.RobolectricTestRunner

/**
 * Covers the session state machine and the plaintext → encrypted credential migration.
 *
 * This is the part of the credential change that can silently log everybody out: if the old
 * location is not read, existing installs appear signed out; if it is read but never removed, the
 * plaintext cookie stays on disk — the very thing the change exists to eliminate.
 *
 * The legacy location is injected as a lambda rather than exercising the real `Settings()`, because
 * `Settings()` resolves its context through an `androidx.startup` initializer that a plain JVM test
 * does not run. Injecting it also lets the test assert directly on "was the plaintext deleted".
 */
@RunWith(RobolectricTestRunner::class)
class SessionStoreMigrationTest {

    private val sessionCookie = "z_c0=session-token; d_c0=cookie-d_c0"

    private lateinit var store: CredentialStore

    /** Stand-in for the old plaintext preference entry. */
    private class FakeLegacyStore(var value: String? = null) : LegacyCookieStore {
        var clearCount = 0
            private set

        override fun read(): String? = value

        override fun clear() {
            clearCount++
            value = null
        }
    }

    private lateinit var legacy: FakeLegacyStore

    @Before
    fun setUp() {
        legacy = FakeLegacyStore()
        store = InMemoryCredentialStore()
        SessionStore.resetForTest(store = store, legacy = legacy)
    }

    @After
    fun tearDown() {
        SessionStore.resetForTest(store = null, legacy = null)
    }

    @Test
    fun readsTheEncryptedCredential() {
        store.write(sessionCookie)

        assertTrue(SessionStore.isLoggedIn())
        assertEquals(sessionCookie, SessionStore.cookieOrEmpty())
    }

    @Test
    fun migratesAPlaintextCookieIntoEncryptedStorageAndDeletesThePlaintext() {
        // Simulate an install that signed in before encryption existed.
        legacy.value = sessionCookie

        assertTrue("a migrated install must stay signed in", SessionStore.isLoggedIn())
        assertEquals(sessionCookie, store.read())
        assertNull("the plaintext copy must not survive the migration", legacy.value)
        assertEquals("the plaintext entry must be actively cleared", 1, legacy.clearCount)
    }

    @Test
    fun doesNotAdoptAPlaintextValueThatIsNotASession() {
        // Anything without z_c0 cannot sign requests, so it must not count as a login — this is
        // exactly the state that used to boot "logged in" and then spin forever.
        legacy.value = "d_c0=only-a-device-id"

        assertFalse(SessionStore.isLoggedIn())
        assertNull(store.read())
    }

    @Test
    fun logoutRemovesBothCopies() {
        legacy.value = sessionCookie
        SessionStore.isLoggedIn() // triggers the migration
        assertEquals(sessionCookie, store.read())

        SessionStore.logout()

        assertNull(store.read())
        assertNull(legacy.value)
        assertFalse(SessionStore.isLoggedIn())
    }

    @Test
    fun invalidateStopsReportingASession() {
        store.write(sessionCookie)

        SessionStore.invalidate("HTTP 401")

        assertEquals("", SessionStore.cookieOrEmpty())
        assertTrue(SessionStore.state.value is SessionState.Invalid)
    }

    @Test
    fun saveSessionPersistsAndPublishesLoggedIn() {
        SessionStore.saveSession(sessionCookie)

        assertEquals(sessionCookie, store.read())
        assertEquals(SessionState.LoggedIn(sessionCookie), SessionStore.state.value)
    }

    @Test
    fun missingCredentialMeansSignedOutWithoutThrowing() {
        assertFalse(SessionStore.isLoggedIn())
        assertEquals("", SessionStore.cookieOrEmpty())
    }
}
