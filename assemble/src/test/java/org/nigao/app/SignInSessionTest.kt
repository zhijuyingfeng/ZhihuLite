package org.nigao.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_ui.login.SignInSession
import org.robolectric.RobolectricTestRunner

/**
 * The sign-in state machine's precedence rules.
 *
 * The screen used to show "sign-in is taking too long" after 30 seconds, which fires on anyone who
 * needs longer to type a password or answer an SMS, and which the reader answered with "这个超时逻辑
 * 意义大吗". Only a *load* failure is reported now, so the rules that matter are: a failure survives
 * until something succeeds, a success is never overwritten, and a retry re-arms both gates.
 */
@RunWith(RobolectricTestRunner::class)
class SignInSessionTest {

    @Test
    fun `a load failure is shown`() {
        val session = SignInSession()

        session.onLoadFailed("net::ERR_NAME_NOT_RESOLVED")

        assertEquals("net::ERR_NAME_NOT_RESOLVED", session.errorMessage)
    }

    @Test
    fun `a success is never overwritten by a later failure`() {
        val session = SignInSession()

        session.onSessionCookie("z_c0=abc")
        session.onLoadFailed("net::ERR_CONNECTION_RESET")

        assertTrue(session.isCompleted)
        assertNull("signing in worked; a failing resource on the way out must not say otherwise", session.errorMessage)
    }

    @Test
    fun `a success clears a failure that arrived first`() {
        val session = SignInSession()

        session.onLoadFailed("HTTP 502")
        session.onSessionCookie("z_c0=abc")

        assertNull(session.errorMessage)
        assertEquals("z_c0=abc", session.completedCookie)
    }

    @Test
    fun `the cookie is latched exactly once`() {
        val session = SignInSession()

        session.onSessionCookie("z_c0=first")
        session.onSessionCookie("z_c0=second")

        assertEquals("z_c0=first", session.completedCookie)
    }

    @Test
    fun `waiting for the page returns as soon as it has loaded`() = runBlocking {
        val session = SignInSession()

        session.onPageLoaded()

        assertTrue(withTimeoutOrNull(1_000) { session.awaitPageLoaded() } != null)
    }

    @Test
    fun `a retry re-arms both gates and clears the error`() = runBlocking {
        val session = SignInSession()
        session.onPageActive()
        session.onPageLoaded()
        session.onLoadFailed("HTTP 500")

        session.resetForRetry()

        assertNull(session.errorMessage)
        assertFalse(session.isPageLoaded)
        assertFalse(session.isPageActive)
        // Both waits are freshly armed: neither returns until its gate fires again.
        assertNull("the load gate must wait again after a retry", withTimeoutOrNull(200) { session.awaitPageLoaded() })
        assertNull("the page gate must wait again after a retry", withTimeoutOrNull(200) { session.awaitPageActive() })
        // ...and both fire again on the new attempt.
        session.onPageLoaded()
        assertTrue(withTimeoutOrNull(1_000) { session.awaitPageLoaded() } != null)
    }

    @Test
    fun `a failure is not latched into a success`() = runBlocking {
        val session = SignInSession()
        session.onLoadFailed("net::ERR_INTERNET_DISCONNECTED")

        delay(50)

        assertFalse("a failed load is not a completed sign-in", session.isCompleted)
        assertNull(session.completedCookie)
    }
}
