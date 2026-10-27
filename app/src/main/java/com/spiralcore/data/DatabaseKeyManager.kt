package com.spiralcore.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns the random SQLCipher key and wraps it with an AES-GCM key stored in Android Keystore.
 *
 * The SQLCipher passphrase is not stored in preferences or source code. Only the AES-GCM
 * ciphertext and IV are stored in app-private preferences. If the Keystore key becomes
 * invalid or is lost, this class fails closed instead of creating a new key and opening
 * the existing encrypted database with a different key.
 */
internal class DatabaseKeyManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getOrCreateDatabaseKey(): ByteArray {
        val wrapped = preferences.getString(WRAPPED_KEY, null)
        val iv = preferences.getString(WRAPPED_IV, null)

        if (wrapped != null || iv != null) {
            require(!wrapped.isNullOrBlank() && !iv.isNullOrBlank()) {
                "Database key metadata is incomplete; refusing to generate a replacement key"
            }
            return unwrap(
                Base64.decode(wrapped, Base64.NO_WRAP),
                Base64.decode(iv, Base64.NO_WRAP),
            )
        }

        val key = ByteArray(DATABASE_KEY_BYTES).also(SecureRandom()::nextBytes)
        val (ciphertext, nonce) = wrap(key)
        val committed = preferences.edit()
            .putString(WRAPPED_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(WRAPPED_IV, Base64.encodeToString(nonce, Base64.NO_WRAP))
            .commit()
        if (!committed) {
            key.fill(0)
            throw IllegalStateException("Unable to persist wrapped database key")
        }
        return key
    }

    @Synchronized
    fun deleteWrappedKey() {
        check(preferences.edit().remove(WRAPPED_KEY).remove(WRAPPED_IV).commit()) {
            "Unable to remove wrapped database key metadata"
        }
    }

    private fun wrap(plainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        val encrypted = cipher.doFinal(plainKey)
        return encrypted to cipher.iv
    }

    private fun unwrap(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        // A wrapped key without its Keystore wrapping key is unrecoverable. Do not silently
        // create a replacement because that would make the current SQLCipher database unreadable.
        check(
            preferences.getString(WRAPPED_KEY, null) == null &&
                preferences.getString(WRAPPED_IV, null) == null,
        ) { "Database wrapping key is missing; recovery is required" }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "database_key_metadata"
        const val WRAPPED_KEY = "wrapped_database_key"
        const val WRAPPED_IV = "wrapped_database_key_iv"
        const val KEY_ALIAS = "spiralcore.database.key.wrap.v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DATABASE_KEY_BYTES = 32
        const val GCM_TAG_BITS = 128
    }
}
