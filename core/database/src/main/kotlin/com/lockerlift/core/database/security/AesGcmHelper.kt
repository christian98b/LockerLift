package com.lockerlift.core.database.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AesGcmEncryptedData(
    val iv: ByteArray,
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AesGcmEncryptedData
        return iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}

object AesGcmHelper {

    private const val ALGORITHM_AES = "AES"
    private const val TRANSFORMATION_AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    const val KEY_SIZE_BYTES = 32

    private val secureRandom = SecureRandom()

    /**
     * Generates cryptographically secure random bytes of specified size.
     */
    fun generateRandomBytes(size: Int = KEY_SIZE_BYTES): ByteArray {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    /**
     * Generates a random AES-256 SecretKey.
     */
    fun generateSecretKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance(ALGORITHM_AES)
        keyGen.init(256, secureRandom)
        return keyGen.generateKey()
    }

    /**
     * Reconstructs a SecretKey from raw 32-byte array.
     */
    fun secretKeyFromBytes(rawKey: ByteArray): SecretKey {
        require(rawKey.size == KEY_SIZE_BYTES) { "Secret key must be exactly $KEY_SIZE_BYTES bytes (256 bits)" }
        return SecretKeySpec(rawKey, ALGORITHM_AES)
    }

    /**
     * Encrypts plaintext using AES-GCM with a provider-generated IV.
     *
     * AndroidKeyStore keys require the provider to generate the IV when
     * randomized encryption is enabled (the secure default).
     */
    fun encrypt(secretKey: SecretKey, plaintext: ByteArray): AesGcmEncryptedData {
        val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext = cipher.doFinal(plaintext)
        return AesGcmEncryptedData(iv = cipher.iv, ciphertext = ciphertext)
    }

    /**
     * Decrypts AES-GCM ciphertext using the given IV and SecretKey.
     */
    fun decrypt(secretKey: SecretKey, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        return cipher.doFinal(ciphertext)
    }
}
