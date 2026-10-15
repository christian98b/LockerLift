package com.lockerlift.core.model

import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.WorkoutSet
import org.junit.Assert.*
import org.junit.Test

class WorkoutLogicTest {

    @Test
    fun testFormatWeight() {
        assertEquals("80", WorkoutLogic.formatWeight(80.0f))
        assertEquals("82.5", WorkoutLogic.formatWeight(82.5f))
        assertEquals("0", WorkoutLogic.formatWeight(0.0f))
    }

    @Test
    fun testClamping() {
        assertEquals(0.0f, WorkoutLogic.clampWeight(-5f), 0.001f)
        assertEquals(1000.0f, WorkoutLogic.clampWeight(1200f), 0.001f)
        assertEquals(75.5f, WorkoutLogic.clampWeight(75.5f), 0.001f)

        assertEquals(1, WorkoutLogic.clampReps(0))
        assertEquals(1, WorkoutLogic.clampReps(-10))
        assertEquals(999, WorkoutLogic.clampReps(1000))
        assertEquals(12, WorkoutLogic.clampReps(12))
    }

    @Test
    fun testDoubleProgression() {
        assertTrue(WorkoutLogic.isProgressionProposed(12, threshold = 12))
        assertTrue(WorkoutLogic.isProgressionProposed(15, threshold = 12))
        assertFalse(WorkoutLogic.isProgressionProposed(11, threshold = 12))

        assertEquals(82.5f, WorkoutLogic.calculateNextWeight(80.0f, 2.5f, shouldProgress = true), 0.001f)
        assertEquals(80.0f, WorkoutLogic.calculateNextWeight(80.0f, 2.5f, shouldProgress = false), 0.001f)
    }

    @Test
    fun testInverseProgressionForAssistedMachines() {
        assertEquals(77.5f, WorkoutLogic.calculateNextWeight(80.0f, 2.5f, shouldProgress = true, weightDirection = WeightDirection.INVERSE), 0.001f)
        assertEquals(80.0f, WorkoutLogic.calculateNextWeight(80.0f, 2.5f, shouldProgress = false, weightDirection = WeightDirection.INVERSE), 0.001f)
        assertEquals(82.5f, WorkoutLogic.calculateNextWeight(80.0f, 2.5f, shouldProgress = true, weightDirection = WeightDirection.NORMAL), 0.001f)
        assertEquals(0.0f, WorkoutLogic.calculateNextWeight(1.0f, 2.5f, shouldProgress = true, weightDirection = WeightDirection.INVERSE), 0.001f)
    }

    @Test
    fun testValidateNewMachine() {
        val existing = listOf("Bench Press", "Squat")
        assertEquals(ValidationResult.EmptyName, WorkoutLogic.validateNewMachine("   ", existing))
        assertEquals(ValidationResult.DuplicateName, WorkoutLogic.validateNewMachine("bench press", existing))
        assertEquals(ValidationResult.Valid, WorkoutLogic.validateNewMachine("Deadlift", existing))
    }

    @Test
    fun testStationOperations() {
        val machine1 = Machine(id = "m1", name = "Bench", targetMuscleGroup = "Chest", machineSettingsNote = "Pin 3")
        val machine2 = Machine(id = "m2", name = "Incline Bench", targetMuscleGroup = "Chest", machineSettingsNote = "Pin 4")

        val initial = listOf(
            SessionMachineInstance(sessionId = "s1", machineId = machine1.id, executionOrder = 0, customSettingsNote = machine1.machineSettingsNote)
        )

        // Replace
        val replaced = WorkoutLogic.replaceMachineInSession(initial, 0, machine2)
        assertEquals("m2", replaced[0].machineId)
        assertEquals("Pin 4", replaced[0].customSettingsNote)

        // Skip toggle
        val skipped = WorkoutLogic.toggleSkipStation(replaced, 0)
        assertTrue(skipped[0].isSkipped)
        val unskipped = WorkoutLogic.toggleSkipStation(skipped, 0)
        assertFalse(unskipped[0].isSkipped)

        // Append
        val appended = WorkoutLogic.appendMachineToSession(initial, "s1", machine2)
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
        val updated = WorkoutLogic.updateSetInList(sets, targetIndex = 0, weightKg = 65f, reps = 12)
        assertEquals(2, updated.size)
        assertEquals(65f, updated[0].weightKg, 0.001f)
        assertEquals(12, updated[0].reps)
        assertEquals(1, updated[0].setNumber)
        assertEquals("2-0-2-0", updated[0].cadence)

        // Target index out of bounds should return original list
        val untouched = WorkoutLogic.updateSetInList(sets, targetIndex = 5, weightKg = 70f, reps = 10)
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
        val afterDelete = WorkoutLogic.deleteSetAndRenumber(sets, targetIndex = 1)
        assertEquals(2, afterDelete.size)
        assertEquals(1, afterDelete[0].setNumber)
        assertEquals(80f, afterDelete[0].weightKg, 0.001f)
        assertEquals(2, afterDelete[1].setNumber)
        assertEquals(90f, afterDelete[1].weightKg, 0.001f)

        // Delete first set
        val afterDeleteFirst = WorkoutLogic.deleteSetAndRenumber(afterDelete, targetIndex = 0)
        assertEquals(1, afterDeleteFirst.size)
        assertEquals(1, afterDeleteFirst[0].setNumber)
        assertEquals(90f, afterDeleteFirst[0].weightKg, 0.001f)

        // Invalid index returns original list
        val untouched = WorkoutLogic.deleteSetAndRenumber(sets, targetIndex = -1)
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
        val reordered = WorkoutLogic.reorderInstances(instances, fromIndex = 2, toIndex = 0)
        assertEquals(3, reordered.size)
        assertEquals("m3", reordered[0].machineId)
        assertEquals(0, reordered[0].executionOrder)
        assertEquals("m1", reordered[1].machineId)
        assertEquals(1, reordered[1].executionOrder)
        assertEquals("m2", reordered[2].machineId)
        assertEquals(2, reordered[2].executionOrder)

        // Invalid index
        val untouched = WorkoutLogic.reorderInstances(instances, fromIndex = 0, toIndex = 10)
        assertEquals(instances, untouched)
    }

    @Test
    fun testRemoveInstanceFromSession() {
        val instances = listOf(
            SessionMachineInstance(sessionId = "s1", machineId = "m1", executionOrder = 0),
            SessionMachineInstance(sessionId = "s1", machineId = "m2", executionOrder = 1),
            SessionMachineInstance(sessionId = "s1", machineId = "m3", executionOrder = 2)
        )

        val removed = WorkoutLogic.removeInstanceFromSession(instances, targetIndex = 1)
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

        val swapped = WorkoutLogic.swapMachinePreservingSets(listOf(instance), targetIndex = 0, newMachine = newMachine)
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
        val next = WorkoutLogic.findNextUnfinishedStationIndex(currentIndex = 0, instances = instances, loggedSets = loggedSets)
        assertEquals(1, next)

        // If station 2 is finished as well, wrap-around finds station 1
        val loggedSets2 = mapOf(
            "smi-1" to listOf(set),
            "smi-2" to emptyList(),
            "smi-3" to listOf(set)
        )
        val nextFrom2 = WorkoutLogic.findNextUnfinishedStationIndex(currentIndex = 2, instances = instances, loggedSets = loggedSets2)
        assertEquals(1, nextFrom2)

        // All finished
        val allFinishedSets = mapOf(
            "smi-1" to listOf(set),
            "smi-2" to listOf(set),
            "smi-3" to listOf(set)
        )
        val none = WorkoutLogic.findNextUnfinishedStationIndex(currentIndex = 0, instances = instances, loggedSets = allFinishedSets)
        assertNull(none)
    }

    @Test
    fun testCalculateWeightSteppers() {
        // Default 2.5 kg increment
        val (minus25, plus25) = WorkoutLogic.calculateWeightSteppers(2.5f)
        assertEquals(listOf(-5.0f, -2.5f, -1.25f), minus25)
        assertEquals(listOf(1.25f, 2.5f, 5.0f), plus25)

        // 1.25 kg increment (plate loaded / microplates)
        val (minus125, plus125) = WorkoutLogic.calculateWeightSteppers(1.25f)
        assertEquals(listOf(-5.0f, -2.5f, -1.25f), minus125)
        assertEquals(listOf(1.25f, 2.5f, 5.0f), plus125)

        // 5.0 kg increment (heavy stacks)
        val (minus5, plus5) = WorkoutLogic.calculateWeightSteppers(5.0f)
        assertEquals(listOf(-10.0f, -5.0f, -2.5f), minus5)
        assertEquals(listOf(2.5f, 5.0f, 10.0f), plus5)

        // 1.0 kg increment (dumbbells)
        val (minus1, plus1) = WorkoutLogic.calculateWeightSteppers(1.0f)
        assertEquals(listOf(-5.0f, -2.0f, -1.0f), minus1)
        assertEquals(listOf(1.0f, 2.0f, 5.0f), plus1)

        // 0.5 kg increment
        val (minus05, plus05) = WorkoutLogic.calculateWeightSteppers(0.5f)
        assertEquals(listOf(-2.0f, -1.0f, -0.5f), minus05)
        assertEquals(listOf(0.5f, 1.0f, 2.0f), plus05)

        // Zero or negative fallback to 2.5 kg
        val (minusFallback, plusFallback) = WorkoutLogic.calculateWeightSteppers(0f)
        assertEquals(listOf(-5.0f, -2.5f, -1.25f), minusFallback)
        assertEquals(listOf(1.25f, 2.5f, 5.0f), plusFallback)
    }

    @Test
    fun testFormatDelta() {
        assertEquals("+5", WorkoutLogic.formatDelta(5.0f))
        assertEquals("-5", WorkoutLogic.formatDelta(-5.0f))
        assertEquals("+2.5", WorkoutLogic.formatDelta(2.5f))
        assertEquals("-2.5", WorkoutLogic.formatDelta(-2.5f))
        assertEquals("+1.25", WorkoutLogic.formatDelta(1.25f))
        assertEquals("-1.25", WorkoutLogic.formatDelta(-1.25f))
        assertEquals("0", WorkoutLogic.formatDelta(0.0f))
    }
}
