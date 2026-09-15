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

    @Test
    fun testUpdateSetInList() {
        val sets = listOf(
            WorkoutSet(sessionMachineId = "smi-1", setNumber = 1, weightKg = 60f, reps = 10, cadence = "2-0-2-0"),
            WorkoutSet(sessionMachineId = "smi-1", setNumber = 2, weightKg = 60f, reps = 8, cadence = "2-0-2-0")
        )

        // Update set 1
        val updated = WorkoutTrackingLogic.updateSetInList(sets, targetIndex = 0, weightKg = 65f, reps = 12)
        assertEquals(2, updated.size)
        assertEquals(65f, updated[0].weightKg, 0.001f)
        assertEquals(12, updated[0].reps)
        assertEquals(1, updated[0].setNumber)
        assertEquals("2-0-2-0", updated[0].cadence)

        // Target index out of bounds should return original list
        val untouched = WorkoutTrackingLogic.updateSetInList(sets, targetIndex = 5, weightKg = 70f, reps = 10)
        assertEquals(sets, untouched)
    }

    @Test
    fun testDeleteSetAndRenumber() {
        val sets = listOf(
            WorkoutSet(sessionMachineId = "smi-1", setNumber = 1, weightKg = 80f, reps = 10),
            WorkoutSet(sessionMachineId = "smi-1", setNumber = 2, weightKg = 85f, reps = 8),
            WorkoutSet(sessionMachineId = "smi-1", setNumber = 3, weightKg = 90f, reps = 6)
        )

        // Delete middle set (set 2)
        val afterDelete = WorkoutTrackingLogic.deleteSetAndRenumber(sets, targetIndex = 1)
        assertEquals(2, afterDelete.size)
        assertEquals(1, afterDelete[0].setNumber)
        assertEquals(80f, afterDelete[0].weightKg, 0.001f)
        assertEquals(2, afterDelete[1].setNumber)
        assertEquals(90f, afterDelete[1].weightKg, 0.001f)

        // Delete first set
        val afterDeleteFirst = WorkoutTrackingLogic.deleteSetAndRenumber(afterDelete, targetIndex = 0)
        assertEquals(1, afterDeleteFirst.size)
        assertEquals(1, afterDeleteFirst[0].setNumber)
        assertEquals(90f, afterDeleteFirst[0].weightKg, 0.001f)

        // Invalid index returns original list
        val untouched = WorkoutTrackingLogic.deleteSetAndRenumber(sets, targetIndex = -1)
        assertEquals(sets, untouched)
    }

    @Test
    fun testReorderInstances() {
        val instances = listOf(
            SessionMachineInstance(sessionId = "s1", machineId = "m1", executionOrder = 0),
            SessionMachineInstance(sessionId = "s1", machineId = "m2", executionOrder = 1),
            SessionMachineInstance(sessionId = "s1", machineId = "m3", executionOrder = 2)
        )

        // Move m3 to index 0
        val reordered = WorkoutTrackingLogic.reorderInstances(instances, fromIndex = 2, toIndex = 0)
        assertEquals(3, reordered.size)
        assertEquals("m3", reordered[0].machineId)
        assertEquals(0, reordered[0].executionOrder)
        assertEquals("m1", reordered[1].machineId)
        assertEquals(1, reordered[1].executionOrder)
        assertEquals("m2", reordered[2].machineId)
        assertEquals(2, reordered[2].executionOrder)

        // Invalid index
        val untouched = WorkoutTrackingLogic.reorderInstances(instances, fromIndex = 0, toIndex = 10)
        assertEquals(instances, untouched)
    }

    @Test
    fun testRemoveInstanceFromSession() {
        val instances = listOf(
            SessionMachineInstance(sessionId = "s1", machineId = "m1", executionOrder = 0),
            SessionMachineInstance(sessionId = "s1", machineId = "m2", executionOrder = 1),
            SessionMachineInstance(sessionId = "s1", machineId = "m3", executionOrder = 2)
        )

        val removed = WorkoutTrackingLogic.removeInstanceFromSession(instances, targetIndex = 1)
        assertEquals(2, removed.size)
        assertEquals("m1", removed[0].machineId)
        assertEquals(0, removed[0].executionOrder)
        assertEquals("m3", removed[1].machineId)
        assertEquals(1, removed[1].executionOrder)
    }

    @Test
    fun testSwapMachinePreservingSets() {
        val originalMachine = Machine(id = "m1", name = "Bench Press", targetMuscleGroup = "Chest", machineSettingsNote = "Seat 2")
        val newMachine = Machine(id = "m2", name = "Incline Bench", targetMuscleGroup = "Chest", machineSettingsNote = "Seat 4")

        val instance = SessionMachineInstance(
            id = "smi-fixed-uuid",
            sessionId = "s1",
            machineId = originalMachine.id,
            executionOrder = 0,
            customSettingsNote = originalMachine.machineSettingsNote
        )

        val swapped = WorkoutTrackingLogic.swapMachinePreservingSets(listOf(instance), targetIndex = 0, newMachine = newMachine)
        assertEquals(1, swapped.size)
        // Verify that instance ID is preserved (preserving loggedSets map keys)
        assertEquals("smi-fixed-uuid", swapped[0].id)
        assertEquals("m2", swapped[0].machineId)
        assertEquals("Seat 4", swapped[0].customSettingsNote)
    }

    @Test
    fun testFindNextUnfinishedStationIndex() {
        val instances = listOf(
            SessionMachineInstance(id = "smi-1", sessionId = "s1", machineId = "m1", executionOrder = 0),
            SessionMachineInstance(id = "smi-2", sessionId = "s1", machineId = "m2", executionOrder = 1),
            SessionMachineInstance(id = "smi-3", sessionId = "s1", machineId = "m3", executionOrder = 2)
        )

        val set = WorkoutSet(sessionMachineId = "smi-1", setNumber = 1, weightKg = 60f, reps = 10)
        val loggedSets = mapOf(
            "smi-1" to listOf(set),
            "smi-2" to emptyList<WorkoutSet>(),
            "smi-3" to emptyList<WorkoutSet>()
        )

        // From station 0, next unfinished should be station 1
        val next = WorkoutTrackingLogic.findNextUnfinishedStationIndex(currentIndex = 0, instances = instances, loggedSets = loggedSets)
        assertEquals(1, next)

        // If station 2 is finished as well, wrap-around finds station 1
        val loggedSets2 = mapOf(
            "smi-1" to listOf(set),
            "smi-2" to emptyList(),
            "smi-3" to listOf(set)
        )
        val nextFrom2 = WorkoutTrackingLogic.findNextUnfinishedStationIndex(currentIndex = 2, instances = instances, loggedSets = loggedSets2)
        assertEquals(1, nextFrom2)

        // All finished
        val allFinishedSets = mapOf(
            "smi-1" to listOf(set),
            "smi-2" to listOf(set),
            "smi-3" to listOf(set)
        )
        val none = WorkoutTrackingLogic.findNextUnfinishedStationIndex(currentIndex = 0, instances = instances, loggedSets = allFinishedSets)
        assertNull(none)
    }
}
