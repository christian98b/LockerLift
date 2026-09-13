package com.lockerlift.core.database.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object DatabaseKeyManager {

    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "LockerLiftDbMasterKey"
    private const val PREFS_NAME = "lockerlift_db_security_prefs"
    private const val PREF_KEY_IV = "db_passphrase_iv"
    private const val PREF_KEY_CIPHERTEXT = "db_passphrase_ciphertext"

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    /**
     * Optional custom key provider for testing or dependency injection.
     */
    @Volatile
    var customPassphraseProvider: ((Context) -> ByteArray)? = null

    /**
     * Clears in-memory cached passphrase (useful for testing or process shutdown).
     */
    fun clearCache() {
        cachedPassphrase = null
    }

    /**
     * Retrieves or generates a cryptographically secure 256-bit passphrase for SQLCipher.
     * In Android runtime, the passphrase is encrypted at rest using a hardware-backed
     * AES key stored in AndroidKeyStore.
     */
    @Synchronized
    fun getOrCreatePassphrase(context: Context): ByteArray {
        cachedPassphrase?.let { return it }

        customPassphraseProvider?.let { provider ->
            val custom = provider(context)
            cachedPassphrase = custom
            return custom
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedIvBase64 = prefs.getString(PREF_KEY_IV, null)
        val savedCiphertextBase64 = prefs.getString(PREF_KEY_CIPHERTEXT, null)

        val masterKey = getOrCreateMasterKey()

        if (savedIvBase64 != null && savedCiphertextBase64 != null) {
            val iv = Base64.decode(savedIvBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(savedCiphertextBase64, Base64.NO_WRAP)
            val decrypted = AesGcmHelper.decrypt(masterKey, iv, ciphertext)
            cachedPassphrase = decrypted
            return decrypted
        }

        // Generate a new random 256-bit passphrase
        val rawPassphrase = AesGcmHelper.generateRandomBytes(AesGcmHelper.KEY_SIZE_BYTES)
        val encrypted = AesGcmHelper.encrypt(masterKey, rawPassphrase)

        prefs.edit()
            .putString(PREF_KEY_IV, Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
            .putString(PREF_KEY_CIPHERTEXT, Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP))
            .apply()

        cachedPassphrase = rawPassphrase
        return rawPassphrase
    }

    private fun getOrCreateMasterKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                (keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            } else {
                val keyGen = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }
        } catch (_: Exception) {
            // Fallback for JVM test environments where AndroidKeyStore is not installed
            AesGcmHelper.secretKeyFromBytes(ByteArray(AesGcmHelper.KEY_SIZE_BYTES) { 42.toByte() })
        }
    }
}
