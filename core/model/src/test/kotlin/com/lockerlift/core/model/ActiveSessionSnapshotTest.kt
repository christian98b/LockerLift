package com.lockerlift.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSessionSnapshotTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun sampleSnapshot(): ActiveSessionSnapshot {
        val instance = SessionMachineInstance(
            id = "inst-1",
            sessionId = "session-1",
            machineId = "machine-1",
            executionOrder = 0
        )
        val set = WorkoutSet(
            id = "set-1",
            sessionMachineId = "inst-1",
            setNumber = 1,
            reps = 10,
            weightKg = 80f
        )
        return ActiveSessionSnapshot(
            sessionId = "session-1",
            templateId = "template-1",
            templateName = "Push Day",
            startTimeMillis = 1_700_000_000_000L,
            originDevice = "WEAR_OS",
            initialMachines = listOf(
                Machine(id = "machine-1", name = "Bench Press", targetMuscleGroup = "Chest")
            ),
            instances = listOf(instance),
            setsByInstance = listOf(InstanceSets("inst-1", listOf(set))),
            pausedAtMillis = 1_700_000_030_000L,
            totalPausedMillis = 30_000L
        )
    }

    @Test
    fun testSerialization_whenEncodingSnapshot_roundTripsLosslessly() {
        // Arrange
        val snapshot = sampleSnapshot()

        // Act
        val encoded = json.encodeToString(ActiveSessionSnapshot.serializer(), snapshot)
        val decoded = json.decodeFromString(ActiveSessionSnapshot.serializer(), encoded)

        // Assert
        assertEquals(snapshot, decoded)
    }

    @Test
    fun testSerialization_whenDecodingJsonWithoutOptionalFields_appliesDefaults() {
        // Arrange: minimal payload, only mandatory fields present
        val payload = """
            {"sessionId":"s1","startTimeMillis":42,"originDevice":"MOBILE"}
        """.trimIndent()

        // Act
        val decoded = json.decodeFromString(ActiveSessionSnapshot.serializer(), payload)

        // Assert
        assertEquals("s1", decoded.sessionId)
        assertEquals(42L, decoded.startTimeMillis)
        assertEquals("MOBILE", decoded.originDevice)
        assertNull(decoded.templateId)
        assertNull(decoded.templateName)
        assertTrue(decoded.instances.isEmpty())
        assertTrue(decoded.setsByInstance.isEmpty())
        assertNull(decoded.pausedAtMillis)
        assertEquals(0L, decoded.totalPausedMillis)
    }

    @Test
    fun testSetsMapFromSnapshot_whenSnapshotHasSets_buildsInstanceKeyedMap() {
        // Arrange
        val snapshot = sampleSnapshot()

        // Act
        val map = ActiveSessionSnapshotCodec.setsMapFromSnapshot(snapshot)

        // Assert
        assertEquals(1, map.size)
        assertEquals(1, map["inst-1"]?.size)
        assertEquals(80f, map["inst-1"]?.first()?.weightKg ?: 0f, 0.001f)
    }

    @Test
    fun testSnapshotWithSets_whenReplacingSetsMap_roundTripsThroughCodec() {
        // Arrange
        val snapshot = sampleSnapshot().copy(setsByInstance = emptyList())
        val sets = mapOf(
            "inst-1" to listOf(
                WorkoutSet(
                    id = "set-2",
                    sessionMachineId = "inst-1",
                    setNumber = 1,
                    reps = 12,
                    weightKg = 82.5f
                )
            )
        )

        // Act
        val updated = ActiveSessionSnapshotCodec.snapshotWithSets(snapshot, sets)
        val map = ActiveSessionSnapshotCodec.setsMapFromSnapshot(updated)

        // Assert
        assertEquals(sets, map)
        assertEquals(82.5f, map["inst-1"]?.first()?.weightKg ?: 0f, 0.001f)
    }
}
