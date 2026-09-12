package org.nigao.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_logic.login.data.EncryptedCredentialStore
import org.robolectric.RobolectricTestRunner

/**
 * Contract tests for [EncryptedCredentialStore] that do **not** require a working Keystore.
 *
 * Scope note, stated plainly because it matters: Robolectric's `AndroidKeyStore` is a stub, so real
 * AES/GCM encryption cannot be exercised in a JVM test — `Cipher` throws and the store degrades by
 * design. Those two degraded behaviours are what this file asserts:
 *
 *  - a write that cannot be encrypted must not crash the caller (worst case: the session does not
 *    survive a restart, which is far better than crashing mid-login);
 *  - a payload that cannot be decrypted must be treated as "signed out" *and removed*, so a
 *    corrupted value cannot fail again on every launch.
 *
 * Not covered here (requires a device/emulator): that the Keystore key is created, that a round
 * trip returns the cookie, and that a fresh instance can read what an earlier one wrote. Those are
 * listed in docs/REFACTOR_PLAN.md §7.7 as device-verification items.
 */
@RunWith(RobolectricTestRunner::class)
class EncryptedCredentialStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs
        get() = context.getSharedPreferences("zhihu_lite_secure_session", Context.MODE_PRIVATE)

    @Test
    fun readingAnEmptyStoreReturnsNull() {
        assertNull(EncryptedCredentialStore(context).read())
    }

    @Test
    fun aWriteThatCannotBeEncryptedDoesNotThrow() {
        val store = EncryptedCredentialStore(context)

        // No try/catch on purpose: the store is contractually not allowed to propagate failures.
        store.write("z_c0=session-token; d_c0=cookie-d_c0")

        // Whether anything was stored depends on Keystore availability; what must hold is that the
        // call returned normally and that a subsequent read is still safe.
        assertNull(store.read())
    }

    @Test
    fun clearOnAnEmptyStoreIsHarmless() {
        val store = EncryptedCredentialStore(context)

        store.clear()

        assertNull(store.read())
    }

    @Test
    fun anUndecryptablePayloadIsTreatedAsSignedOutAndRemoved() {
        // This is the corruption path: a value we cannot interpret must not be reported as a
        // session, and must not be left behind to fail again on every launch.
        prefs.edit().putString("session_ciphertext_v1", "not-a-valid-payload").commit()
        assertTrue(prefs.contains("session_ciphertext_v1"))

        val result = EncryptedCredentialStore(context).read()

        assertNull(result)
        assertNull(
            "an unusable credential must be cleared, not retried forever",
            prefs.getString("session_ciphertext_v1", null),
        )
    }

    @Test
    fun aPayloadWithTheWrongShapeIsAlsoCleared() {
        // Right-ish shape (iv:ciphertext) but no valid key material behind it.
        prefs.edit().putString("session_ciphertext_v1", "AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAA").commit()

        assertNull(EncryptedCredentialStore(context).read())
        assertNull(prefs.getString("session_ciphertext_v1", null))
    }
}
