package com.lockerlift.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DomainModelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testMachineDefaultsAndUniqueness() {
        val machine1 = Machine(
            name = "Bankdrücken",
            targetMuscleGroup = "Brust"
        )
        val machine2 = Machine(
            name = "Kniebeugen",
            targetMuscleGroup = "Beine"
        )

        assertNotNull(machine1.id)
        assertNotNull(machine2.id)
        assertNotEquals(machine1.id, machine2.id)
        assertEquals(2.5f, machine1.defaultIncrementKg, 0.001f)
        assertNull(machine1.defaultCadence)
        assertNull(machine1.machineSettingsNote)
    }

    @Test
    fun testWorkoutTemplateCreation() {
        val template = WorkoutTemplate(
            name = "Push Day",
            description = "Brust, Schultern, Trizeps"
        )

        assertNotNull(template.id)
        assertEquals("Push Day", template.name)
        assertFalse(template.isArchived)
        assertTrue(template.createdAt > 0)
    }

    @Test
    fun testWorkoutSessionAndSets() {
        val session = WorkoutSession(originDevice = "WEAR_OS")
        val instance = SessionMachineInstance(
            sessionId = session.id,
            machineId = "machine-123",
            executionOrder = 0
        )
        val set1 = WorkoutSet(
            sessionMachineId = instance.id,
            setNumber = 1,
            reps = 10,
            weightKg = 80f,
            cadence = "3-1-1-0",
            setType = SetType.NORMAL
        )

        assertEquals(session.id, instance.sessionId)
        assertEquals(instance.id, set1.sessionMachineId)
        assertEquals(10, set1.reps)
        assertEquals(80f, set1.weightKg, 0.001f)
        assertEquals("3-1-1-0", set1.cadence)
        assertEquals(SetType.NORMAL, set1.setType)
    }

    @Test
    fun testJsonSerializationRoundTrip() {
        val machine = Machine(
            id = "test-uuid-1",
            name = "Latzug",
            targetMuscleGroup = "Rücken",
            machineSettingsNote = "Sitz 4",
            defaultIncrementKg = 5.0f,
            defaultCadence = "2-0-2-0"
        )

        val encoded = json.encodeToString(machine)
        val decoded = json.decodeFromString<Machine>(encoded)

        assertEquals(machine.id, decoded.id)
        assertEquals(machine.name, decoded.name)
        assertEquals(machine.targetMuscleGroup, decoded.targetMuscleGroup)
        assertEquals(machine.machineSettingsNote, decoded.machineSettingsNote)
        assertEquals(machine.defaultIncrementKg, decoded.defaultIncrementKg, 0.001f)
        assertEquals(machine.defaultCadence, decoded.defaultCadence)
    }
}
