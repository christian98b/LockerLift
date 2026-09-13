package com.lockerlift.wear

import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.SetType
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.wear.logic.ValidationResult
import com.lockerlift.wear.logic.WearWorkoutLogic
import org.junit.Assert.*
import org.junit.Test

class WearWorkoutLogicTest {

    @Test
    fun testFormatWeight_integerAndDecimal() {
        // Arrange & Act & Assert
        assertEquals("80", WearWorkoutLogic.formatWeight(80.0f))
        assertEquals("82.5", WearWorkoutLogic.formatWeight(82.5f))
        assertEquals("1.25", WearWorkoutLogic.formatWeight(1.25f))
        assertEquals("0", WearWorkoutLogic.formatWeight(0.0f))
    }

    @Test
    fun testExtractLastSessionSets_multipleSessions() {
        // Arrange: s1 & s2 from latest session (smi-latest), s3 & s4 from older session (smi-old)
        val s1 = WorkoutSetEntity(id = "1", sessionMachineId = "smi-latest", setNumber = 1, reps = 10, weightKg = 80f)
        val s2 = WorkoutSetEntity(id = "2", sessionMachineId = "smi-latest", setNumber = 2, reps = 9, weightKg = 80f)
        val s3 = WorkoutSetEntity(id = "3", sessionMachineId = "smi-old", setNumber = 1, reps = 12, weightKg = 77.5f)
        val s4 = WorkoutSetEntity(id = "4", sessionMachineId = "smi-old", setNumber = 2, reps = 10, weightKg = 77.5f)
        val allSets = listOf(s1, s2, s3, s4)

        // Act
        val result = WearWorkoutLogic.extractLastSessionSets(allSets)

        // Assert
        assertEquals(2, result.size)
        assertEquals("1", result[0].id)
        assertEquals("2", result[1].id)
    }

    @Test
    fun testExtractLastSessionSets_empty() {
        // Arrange & Act
        val result = WearWorkoutLogic.extractLastSessionSets(emptyList())

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun testFormatHistoricalSetsString_formatting() {
        // Arrange
        val s1 = WorkoutSetEntity(id = "1", sessionMachineId = "smi-1", setNumber = 1, reps = 10, weightKg = 80f)
        val s2 = WorkoutSetEntity(id = "2", sessionMachineId = "smi-1", setNumber = 2, reps = 9, weightKg = 80f)
        val sets = listOf(s1, s2)

        // Act
        val formatted = WearWorkoutLogic.formatHistoricalSetsString(sets)

        // Assert
        assertEquals("80 kg × 10, 80 kg × 9", formatted)
    }

    @Test
    fun testFormatHistoricalSetsString_empty() {
        // Arrange & Act
        val formatted = WearWorkoutLogic.formatHistoricalSetsString(emptyList())

        // Assert
        assertNull(formatted)
    }

    @Test
    fun testFormatPerformanceSummary_withPrefixes() {
        // Arrange
        val s1 = WorkoutSetEntity(id = "1", sessionMachineId = "smi-1", setNumber = 1, reps = 10, weightKg = 80f)
        val s2 = WorkoutSetEntity(id = "2", sessionMachineId = "smi-1", setNumber = 2, reps = 9, weightKg = 80f)
        val sets = listOf(s1, s2)

        // Act
        val enSummary = WearWorkoutLogic.formatPerformanceSummary(sets, prefix = "Last: ")
        val deSummary = WearWorkoutLogic.formatPerformanceSummary(sets, prefix = "Zuletzt: ")

        // Assert
        assertEquals("Last: 80 kg × 10, 80 kg × 9", enSummary)
        assertEquals("Zuletzt: 80 kg × 10, 80 kg × 9", deSummary)
    }

    @Test
    fun testFormatDomainPerformanceSummary() {
        // Arrange
        val s1 = WorkoutSet(sessionMachineId = "smi-1", setNumber = 1, reps = 12, weightKg = 82.5f, setType = SetType.NORMAL)
        val s2 = WorkoutSet(sessionMachineId = "smi-1", setNumber = 2, reps = 10, weightKg = 87.5f, setType = SetType.NORMAL)
        val sets = listOf(s1, s2)

        // Act
        val summary = WearWorkoutLogic.formatDomainPerformanceSummary(sets)

        // Assert
        assertEquals("Last: 82.5 kg × 12, 87.5 kg × 10", summary)
    }

    @Test
    fun testDoubleProgressionCalculation() {
        // Arrange & Act & Assert
        assertFalse(WearWorkoutLogic.isProgressionProposed(10))
        assertFalse(WearWorkoutLogic.isProgressionProposed(11))
        assertTrue(WearWorkoutLogic.isProgressionProposed(12))
        assertTrue(WearWorkoutLogic.isProgressionProposed(15))

        assertEquals(82.5f, WearWorkoutLogic.calculateNextWeight(80f, 2.5f, shouldProgress = true), 0.001f)
        assertEquals(80.0f, WearWorkoutLogic.calculateNextWeight(80f, 2.5f, shouldProgress = false), 0.001f)
    }

    @Test
    fun testValidateNewMachine_scenarios() {
        // Arrange
        val existing = listOf("Chest Press", "Lat Pulldown", "Leg Press")

        // Act & Assert
        assertEquals(ValidationResult.EmptyName, WearWorkoutLogic.validateNewMachine("", existing))
        assertEquals(ValidationResult.EmptyName, WearWorkoutLogic.validateNewMachine("   ", existing))
        assertEquals(ValidationResult.DuplicateName, WearWorkoutLogic.validateNewMachine("Chest Press", existing))
        assertEquals(ValidationResult.DuplicateName, WearWorkoutLogic.validateNewMachine("chest press", existing))
        assertEquals(ValidationResult.DuplicateName, WearWorkoutLogic.validateNewMachine("  LAT PULLDOWN  ", existing))
        assertEquals(ValidationResult.Valid, WearWorkoutLogic.validateNewMachine("Incline Bench Press", existing))
    }

    @Test
    fun testReplaceMachineInSession() {
        // Arrange
        val inst0 = SessionMachineInstance(sessionId = "sess", machineId = "m0", executionOrder = 0)
        val inst1 = SessionMachineInstance(sessionId = "sess", machineId = "m1", executionOrder = 1, customSettingsNote = "Old Note")
        val inst2 = SessionMachineInstance(sessionId = "sess", machineId = "m2", executionOrder = 2)
        val instances = listOf(inst0, inst1, inst2)

        val replacementMachine = Machine(
            id = "m-replacement",
            name = "Incline Dumbbell Press",
            targetMuscleGroup = "Chest",
            machineSettingsNote = "Seat 4"
        )

        // Act
        val updated = WearWorkoutLogic.replaceMachineInSession(instances, targetIndex = 1, newMachine = replacementMachine)

        // Assert
        assertEquals(3, updated.size)
        assertEquals("m0", updated[0].machineId)
        assertEquals("m-replacement", updated[1].machineId)
        assertEquals("Seat 4", updated[1].customSettingsNote)
        assertEquals(1, updated[1].executionOrder)
        assertEquals(inst1.id, updated[1].id)
        assertEquals("m2", updated[2].machineId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testReplaceMachineInSession_invalidIndex() {
        // Arrange
        val instances = emptyList<SessionMachineInstance>()
        val dummy = Machine(name = "Dummy", targetMuscleGroup = "Chest")

        // Act
        WearWorkoutLogic.replaceMachineInSession(instances, targetIndex = 0, newMachine = dummy)
    }

    @Test
    fun testAppendMachineToSession() {
        // Arrange
        val inst0 = SessionMachineInstance(sessionId = "sess", machineId = "m0", executionOrder = 0)
        val inst1 = SessionMachineInstance(sessionId = "sess", machineId = "m1", executionOrder = 1)
        val instances = listOf(inst0, inst1)

        val newMachine = Machine(
            id = "m-new",
            name = "Tricep Extension",
            targetMuscleGroup = "Arms",
            machineSettingsNote = "Rope attachment"
        )

        // Act
        val newInstance = WearWorkoutLogic.appendMachineToSession(instances, sessionId = "sess", machine = newMachine)

        // Assert
        assertEquals("sess", newInstance.sessionId)
        assertEquals("m-new", newInstance.machineId)
        assertEquals(2, newInstance.executionOrder)
        assertEquals("Rope attachment", newInstance.customSettingsNote)
        assertFalse(newInstance.isSkipped)
    }

    @Test
    fun testToggleSkipStation() {
        // Arrange
        val inst0 = SessionMachineInstance(sessionId = "sess", machineId = "m0", executionOrder = 0, isSkipped = false)
        val inst1 = SessionMachineInstance(sessionId = "sess", machineId = "m1", executionOrder = 1, isSkipped = false)
        val instances = listOf(inst0, inst1)

        // Act: Skip station 0
        val skipped = WearWorkoutLogic.toggleSkipStation(instances, targetIndex = 0)
        assertTrue(skipped[0].isSkipped)
        assertFalse(skipped[1].isSkipped)

        // Act: Unskip station 0
        val unskipped = WearWorkoutLogic.toggleSkipStation(skipped, targetIndex = 0)
        assertFalse(unskipped[0].isSkipped)
        assertFalse(unskipped[1].isSkipped)
    }

    @Test
    fun testDeterminePrefillWeightAndReps_withCurrentSets() {
        // Arrange
        val currentSets = listOf(
            WorkoutSet(sessionMachineId = "smi", setNumber = 1, reps = 10, weightKg = 80f, setType = SetType.NORMAL),
            WorkoutSet(sessionMachineId = "smi", setNumber = 2, reps = 8, weightKg = 85f, setType = SetType.NORMAL)
        )
        val historicalSets = listOf(
            WorkoutSetEntity(id = "h1", sessionMachineId = "smi-hist", setNumber = 1, reps = 12, weightKg = 75f)
        )

        // Act
        val (weight, reps) = WearWorkoutLogic.determinePrefillWeightAndReps(currentSets, historicalSets)

        // Assert: should use last set from current session
        assertEquals(85f, weight, 0.001f)
        assertEquals(8, reps)
    }

    @Test
    fun testDeterminePrefillWeightAndReps_emptyCurrentSetsWithHistorical() {
        // Arrange
        val currentSets = emptyList<WorkoutSet>()
        val historicalSets = listOf(
            WorkoutSetEntity(id = "h1", sessionMachineId = "smi-hist", setNumber = 1, reps = 12, weightKg = 82.5f),
            WorkoutSetEntity(id = "h2", sessionMachineId = "smi-hist", setNumber = 2, reps = 10, weightKg = 87.5f)
        )

        // Act
        val (weight, reps) = WearWorkoutLogic.determinePrefillWeightAndReps(currentSets, historicalSets)

        // Assert: should prefill from first set of last completed session
        assertEquals(82.5f, weight, 0.001f)
        assertEquals(12, reps)
    }

    @Test
    fun testDeterminePrefillWeightAndReps_allEmpty() {
        // Arrange & Act
        val (weight, reps) = WearWorkoutLogic.determinePrefillWeightAndReps(emptyList(), emptyList(), defaultWeight = 50f, defaultReps = 12)

        // Assert: should fall back to provided defaults
        assertEquals(50f, weight, 0.001f)
        assertEquals(12, reps)
    }
}
