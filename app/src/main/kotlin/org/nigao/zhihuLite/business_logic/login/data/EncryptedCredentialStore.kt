package org.nigao.zhihuLite.business_logic.login.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.aakira.napier.Napier
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.nigao.zhihuLite.business_ui.login.CredentialStore

/**
 * [CredentialStore] that encrypts the session cookie with a key held in the Android Keystore.
 *
 * Why hand-rolled instead of `androidx.security:security-crypto`: that library is deprecated and
 * unmaintained, and pulling in Tink for one AES key would be a large dependency for ~60 lines of
 * standard crypto. The interface keeps the choice reversible — swapping in DataStore+Tink later
 * touches only this file.
 *
 * Design notes:
 *  - AES-256-GCM with a per-write random IV; the IV is stored alongside the ciphertext, which is
 *    standard and safe (an IV is not secret, it only must never repeat under one key).
 *  - The key never leaves the Keystore, so the ciphertext is useless without the device.
 *  - `setUserAuthenticationRequired(false)`: the session must work without biometric prompts, and
 *    the threat being addressed here is offline extraction of the file, not a hostile app.
 *  - The payload is version-tagged so a future format change can be detected instead of being
 *    silently mis-parsed.
 *  - Every failure path degrades to "no session" rather than throwing: an unreadable credential
 *    must mean "sign in again", never "crash on every launch".
 */
class EncryptedCredentialStore(context: Context) : CredentialStore {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? {
        val stored = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            decrypt(stored)
        } catch (e: Exception) {
            // Includes the case where the Keystore entry was invalidated (e.g. device credential
            // reset): the only safe recovery is to forget the session and sign in again.
            Napier.e("Stored credential could not be decrypted; clearing it", e)
            clear()
            null
        }
    }

    override fun write(cookie: String) {
        try {
            preferences.edit().putString(KEY_CIPHERTEXT, encrypt(cookie)).apply()
        } catch (e: Exception) {
            // A write failure must not take the app down; the session simply will not survive a
            // restart, which is a much smaller problem than a crash mid-login.
            Napier.e("Could not persist the session credential", e)
        }
    }

    override fun clear() {
        preferences.edit().remove(KEY_CIPHERTEXT).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // iv + ciphertext, both base64 — one string, one preference key.
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            SEPARATOR +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /**
     * Throws on a malformed payload rather than returning null, so [read] always clears unusable
     * data. Returning null used to bypass the cleanup entirely, leaving the bad value on disk to
     * fail again on every launch.
     */
    private fun decrypt(stored: String): String {
        val parts = stored.split(SEPARATOR, limit = 2)
        require(parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
            "Stored credential has an unexpected shape"
        }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /** Returns the existing Keystore key, creating it on first use. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "zhihu_lite_secure_session"
        const val KEY_CIPHERTEXT = "session_ciphertext_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "zhihu_lite_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        const val SEPARATOR = ":"
    }
}
