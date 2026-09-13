package com.lockerlift.core.database.logic

import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.WorkoutSet
import org.junit.Assert.*
import org.junit.Test

class WorkoutTrackingLogicTest {

    @Test
    fun testFormatWeight() {
        assertEquals("80", WorkoutTrackingLogic.formatWeight(80.0f))
        assertEquals("82.5", WorkoutTrackingLogic.formatWeight(82.5f))
        assertEquals("0", WorkoutTrackingLogic.formatWeight(0.0f))
    }

    @Test
    fun testClamping() {
        assertEquals(0.0f, WorkoutTrackingLogic.clampWeight(-5f), 0.001f)
        assertEquals(1000.0f, WorkoutTrackingLogic.clampWeight(1200f), 0.001f)
        assertEquals(75.5f, WorkoutTrackingLogic.clampWeight(75.5f), 0.001f)

        assertEquals(1, WorkoutTrackingLogic.clampReps(0))
        assertEquals(1, WorkoutTrackingLogic.clampReps(-10))
        assertEquals(999, WorkoutTrackingLogic.clampReps(1000))
        assertEquals(12, WorkoutTrackingLogic.clampReps(12))
    }

    @Test
    fun testDoubleProgression() {
        assertTrue(WorkoutTrackingLogic.isProgressionProposed(12, threshold = 12))
        assertTrue(WorkoutTrackingLogic.isProgressionProposed(15, threshold = 12))
        assertFalse(WorkoutTrackingLogic.isProgressionProposed(11, threshold = 12))

        assertEquals(82.5f, WorkoutTrackingLogic.calculateNextWeight(80.0f, 2.5f, shouldProgress = true), 0.001f)
        assertEquals(80.0f, WorkoutTrackingLogic.calculateNextWeight(80.0f, 2.5f, shouldProgress = false), 0.001f)
    }

    @Test
    fun testValidateNewMachine() {
        val existing = listOf("Bench Press", "Squat")
        assertEquals(ValidationResult.EmptyName, WorkoutTrackingLogic.validateNewMachine("   ", existing))
        assertEquals(ValidationResult.DuplicateName, WorkoutTrackingLogic.validateNewMachine("bench press", existing))
        assertEquals(ValidationResult.Valid, WorkoutTrackingLogic.validateNewMachine("Deadlift", existing))
    }

    @Test
    fun testStationOperations() {
        val machine1 = Machine(id = "m1", name = "Bench", targetMuscleGroup = "Chest", machineSettingsNote = "Pin 3")
        val machine2 = Machine(id = "m2", name = "Incline Bench", targetMuscleGroup = "Chest", machineSettingsNote = "Pin 4")

        val initial = listOf(
            SessionMachineInstance(sessionId = "s1", machineId = machine1.id, executionOrder = 0, customSettingsNote = machine1.machineSettingsNote)
        )

        // Replace
        val replaced = WorkoutTrackingLogic.replaceMachineInSession(initial, 0, machine2)
        assertEquals("m2", replaced[0].machineId)
        assertEquals("Pin 4", replaced[0].customSettingsNote)

        // Skip toggle
        val skipped = WorkoutTrackingLogic.toggleSkipStation(replaced, 0)
        assertTrue(skipped[0].isSkipped)
        val unskipped = WorkoutTrackingLogic.toggleSkipStation(skipped, 0)
        assertFalse(unskipped[0].isSkipped)

        // Append
        val appended = WorkoutTrackingLogic.appendMachineToSession(initial, "s1", machine2)
        assertEquals(1, appended.executionOrder)
        assertEquals("m2", appended.machineId)
    }
}
