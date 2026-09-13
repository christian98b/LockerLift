package com.lockerlift.core.database.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException

class AesGcmHelperTest {

    @Test
    fun testAesGcmEncryptAndDecryptSuccess() {
        val key = AesGcmHelper.generateSecretKey()
        val originalPlaintext = "LockerLift Privacy Secret Data 12345!@#$".toByteArray(Charsets.UTF_8)

        val encrypted = AesGcmHelper.encrypt(key, originalPlaintext)
        val decrypted = AesGcmHelper.decrypt(key, encrypted.iv, encrypted.ciphertext)

        assertArrayEquals(originalPlaintext, decrypted)
        assertEquals(String(originalPlaintext, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
        assertEquals(12, encrypted.iv.size)
    }

    @Test
    fun testAesGcmDecryptWithDifferentKeyFails() {
        val key1 = AesGcmHelper.generateSecretKey()
        val key2 = AesGcmHelper.generateSecretKey()
        val originalPlaintext = "Confidential Workout Passphrase".toByteArray(Charsets.UTF_8)

        val encrypted = AesGcmHelper.encrypt(key1, originalPlaintext)

        assertThrows(GeneralSecurityException::class.java) {
            AesGcmHelper.decrypt(key2, encrypted.iv, encrypted.ciphertext)
        }
    }

    @Test
    fun testAesGcmDecryptWithCorruptedCiphertextFails() {
        val key = AesGcmHelper.generateSecretKey()
        val originalPlaintext = "Integrity check message".toByteArray(Charsets.UTF_8)

        val encrypted = AesGcmHelper.encrypt(key, originalPlaintext)
        val corruptedCiphertext = encrypted.ciphertext.clone()
        corruptedCiphertext[0] = (corruptedCiphertext[0].toInt() xor 0xFF).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            AesGcmHelper.decrypt(key, encrypted.iv, corruptedCiphertext)
        }
    }

    @Test
    fun testAesGcmDecryptWithCorruptedIvFails() {
        val key = AesGcmHelper.generateSecretKey()
        val originalPlaintext = "IV integrity message".toByteArray(Charsets.UTF_8)

        val encrypted = AesGcmHelper.encrypt(key, originalPlaintext)
        val corruptedIv = encrypted.iv.clone()
        corruptedIv[0] = (corruptedIv[0].toInt() xor 0xFF).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            AesGcmHelper.decrypt(key, corruptedIv, encrypted.ciphertext)
        }
    }

    @Test
    fun testKeyGenerationAndReconstruction() {
        val randomKeyBytes = AesGcmHelper.generateRandomBytes(32)
        assertEquals(32, randomKeyBytes.size)

        val reconstructedKey = AesGcmHelper.secretKeyFromBytes(randomKeyBytes)
        assertEquals("AES", reconstructedKey.algorithm)
        assertArrayEquals(randomKeyBytes, reconstructedKey.encoded)
    }

    @Test
    fun testUniqueIvsForSuccessiveEncryptions() {
        val key = AesGcmHelper.generateSecretKey()
        val plaintext = "Identical Data".toByteArray(Charsets.UTF_8)

        val enc1 = AesGcmHelper.encrypt(key, plaintext)
        val enc2 = AesGcmHelper.encrypt(key, plaintext)

        assertFalse(enc1.iv.contentEquals(enc2.iv))
        assertFalse(enc1.ciphertext.contentEquals(enc2.ciphertext))

        assertArrayEquals(plaintext, AesGcmHelper.decrypt(key, enc1.iv, enc1.ciphertext))
        assertArrayEquals(plaintext, AesGcmHelper.decrypt(key, enc2.iv, enc2.ciphertext))
    }
}
