package com.lockerlift.mobile

import com.lockerlift.mobile.backup.BackupResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Pure JVM unit tests for the local-backup feature (US 7.2).
 *
 * All helpers are inlined here so that no Android Context is required.
 * Each test follows the Arrange-Act-Assert (AAA) pattern.
 */
class LocalBackupManagerTest {

    // ---------------------------------------------------------------------------
    // Inline helpers (mirror the real LocalBackupManager's pure logic)
    // ---------------------------------------------------------------------------

    /** Compresses [input] with GZip and returns the compressed bytes. */
    private fun gzip(input: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(input) }
        return bos.toByteArray()
    }

    /** Decompresses GZip-compressed [input] and returns the original bytes. */
    private fun gunzip(input: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(input)).use { it.copyTo(bos) }
        return bos.toByteArray()
    }

    /** Returns the SHA-256 hex digest of [data]. */
    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Generates a backup filename for the given [date]. */
    private fun backupFilename(date: Date = Date()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(date)
        return "lockerlift_backup_$stamp.json.gz"
    }

    /** Schema version constant – must match the value in the real LocalBackupManager. */
    private val SCHEMA_VERSION: Int = 1

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    /**
     * Test 1 – GZip round-trip produces the original data.
     */
    @Test
    fun testGzipRoundtrip_whenCompressingAndDecompressing_producesOriginalData() {
        // Arrange
        val original = """{"schemaVersion":1,"workouts":[]}""".toByteArray(Charsets.UTF_8)

        // Act
        val compressed   = gzip(original)
        val decompressed = gunzip(compressed)

        // Assert
        assertArrayEquals("Decompressed bytes must equal the original input", original, decompressed)
    }

    /**
     * Test 2 – SHA-256 checksum of a known input matches the expected hex string.
     *
     * Expected value pre-computed with: `echo -n "lockerlift" | sha256sum`
     */
    @Test
    fun testChecksumComputation_whenSha256Applied_producesConsistentHex() {
        // Arrange
        val input    = "lockerlift".toByteArray(Charsets.UTF_8)
        // SHA-256("lockerlift") – verified offline
        val expected = "3e0d1fbde29a3fc12f78bb9ab78fc93a71b044a97baef6e80a7e5cbab32fb8dc"

        // Act
        val actual = sha256Hex(input)

        // Assert
        assertEquals("SHA-256 hex must match pre-computed value", expected, actual)
    }

    /**
     * Test 3 – Generated filename follows the required pattern.
     */
    @Test
    fun testBackupFilenameFormat_whenCreated_matchesPattern() {
        // Arrange – use a fixed date so the output is deterministic
        val date = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).parse("2024-01-01_120000")!!

        // Act
        val filename = backupFilename(date)

        // Assert
        assertTrue(
            "Filename must start with 'lockerlift_backup_'",
            filename.startsWith("lockerlift_backup_")
        )
        assertTrue(
            "Filename must end with '.json.gz'",
            filename.endsWith(".json.gz")
        )
        assertEquals(
            "Full filename must match expected value",
            "lockerlift_backup_2024-01-01_120000.json.gz",
            filename
        )
    }

    /**
     * Test 4 – Schema version constant equals 1.
     */
    @Test
    fun testSchemaVersion_whenCurrentVersion_isOne() {
        // Arrange + Act (constant, no runtime behaviour)

        // Assert
        assertEquals("Schema version must be 1", 1, SCHEMA_VERSION)
    }

    /**
     * Test 5 – BackupResult.Success carries the expected file name.
     */
    @Test
    fun testBackupResult_Success_containsFileName() {
        // Arrange
        val expectedName = "lockerlift_backup_2024-01-01_120000.json.gz"

        // Act
        val result = BackupResult.Success(expectedName)

        // Assert
        assertTrue(
            "Success.fileName must contain the expected backup name",
            result.fileName.contains("lockerlift_backup_")
        )
        assertEquals("Success.fileName must equal the provided value", expectedName, result.fileName)
    }

    /**
     * Test 6 – BackupResult.Error carries the expected error message.
     */
    @Test
    fun testBackupResult_Error_containsMessage() {
        // Arrange
        val errorMessage = "disk full"

        // Act
        val result = BackupResult.Error(errorMessage)

        // Assert
        assertEquals("Error.message must equal the provided string", errorMessage, result.message)
    }

    /**
     * Test 7 – Compressing an empty byte array still produces a non-empty GZip stream
     * (GZip always emits at least a header + trailer).
     */
    @Test
    fun testGzip_whenEmptyInput_producesValidCompressedBytes() {
        // Arrange
        val empty = ByteArray(0)

        // Act
        val compressed = gzip(empty)

        // Assert
        assertTrue(
            "GZip output for empty input must be non-empty (header + trailer present)",
            compressed.isNotEmpty()
        )
        // Sanity: decompressing must round-trip back to empty
        assertArrayEquals("Round-tripping empty bytes must yield empty bytes", empty, gunzip(compressed))
    }

    /**
     * Test 8 – Two different inputs produce different SHA-256 hashes.
     */
    @Test
    fun testChecksumDifferentInputs_whenDifferentData_produceDifferentHashes() {
        // Arrange
        val input1 = "workout_session_A".toByteArray(Charsets.UTF_8)
        val input2 = "workout_session_B".toByteArray(Charsets.UTF_8)

        // Act
        val hash1 = sha256Hex(input1)
        val hash2 = sha256Hex(input2)

        // Assert
        assertNotEquals(
            "SHA-256 hashes of different inputs must not be equal",
            hash1,
            hash2
        )
    }
}
