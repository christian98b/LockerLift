package com.lockerlift.wear

import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.SetType
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.core.database.logic.ValidationResult
import com.lockerlift.wear.logic.WearWorkoutLogic
import com.lockerlift.wear.logic.WorkoutPauseState
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

    @Test
    fun testClampWeight_boundaries() {
        assertEquals(0.0f, WearWorkoutLogic.clampWeight(-10f), 0.001f)
        assertEquals(0.0f, WearWorkoutLogic.clampWeight(0.0f), 0.001f)
        assertEquals(80.0f, WearWorkoutLogic.clampWeight(80.0f), 0.001f)
        assertEquals(1000.0f, WearWorkoutLogic.clampWeight(1000.0f), 0.001f)
        assertEquals(1000.0f, WearWorkoutLogic.clampWeight(1500.0f), 0.001f)
    }

    @Test
    fun testClampReps_boundaries() {
        assertEquals(1, WearWorkoutLogic.clampReps(-5))
        assertEquals(1, WearWorkoutLogic.clampReps(0))
        assertEquals(1, WearWorkoutLogic.clampReps(1))
        assertEquals(12, WearWorkoutLogic.clampReps(12))
        assertEquals(999, WearWorkoutLogic.clampReps(999))
        assertEquals(999, WearWorkoutLogic.clampReps(2000))
    }

    @Test
    fun testInitializeSessionInstances_withTemplateMachines() {
        // Arrange
        val m1 = Machine(id = "m1", name = "Chest Press", targetMuscleGroup = "Chest", machineSettingsNote = "Seat 5")
        val m2 = Machine(id = "m2", name = "Incline Fly", targetMuscleGroup = "Chest")
        val machines = listOf(m1, m2)

        // Act
        val instances = WearWorkoutLogic.initializeSessionInstances(machines, "session-123")

        // Assert
        assertEquals(2, instances.size)
        assertEquals("session-123", instances[0].sessionId)
        assertEquals("m1", instances[0].machineId)
        assertEquals(0, instances[0].executionOrder)
        assertEquals("Seat 5", instances[0].customSettingsNote)
        assertEquals("session-123", instances[1].sessionId)
        assertEquals("m2", instances[1].machineId)
        assertEquals(1, instances[1].executionOrder)
        assertNull(instances[1].customSettingsNote)
    }

    @Test
    fun testInitializeSessionInstances_nullOrEmptyForFreeWorkout() {
        // Act & Assert for null and empty template lists (Free Workout path)
        assertTrue(WearWorkoutLogic.initializeSessionInstances(null, "session-free").isEmpty())
        assertTrue(WearWorkoutLogic.initializeSessionInstances(emptyList(), "session-free").isEmpty())
    }

    @Test
    fun testFreeWorkoutWorkflow_startsEmptyAndAppendsMachines() {
        // Arrange: Start a free workout session with no pre-configured machines
        val sessionId = "free-workout-session"
        val instances = WearWorkoutLogic.initializeSessionInstances(null, sessionId)
        assertTrue(instances.isEmpty())

        // Act: User selects an ad-hoc machine during free workout
        val machine = Machine(id = "m-ad-hoc", name = "Cable Lateral Raise", targetMuscleGroup = "Shoulders")
        val newInstance = WearWorkoutLogic.appendMachineToSession(instances, sessionId, machine)

        // Assert
        assertEquals(sessionId, newInstance.sessionId)
        assertEquals("m-ad-hoc", newInstance.machineId)
        assertEquals(0, newInstance.executionOrder)
        assertFalse(newInstance.isSkipped)
    }

    // ==========================================
    // Rest Timer Calculation Tests (AK 3.9)
    // ==========================================

    @Test
    fun testAdjustRestSeconds_withinBounds() {
        // Arrange
        val initial = 90
        val step = WearWorkoutLogic.REST_ADJUSTMENT_STEP_SECONDS

        // Act & Assert
        assertEquals(105, WearWorkoutLogic.adjustRestSeconds(initial, step))
        assertEquals(75, WearWorkoutLogic.adjustRestSeconds(initial, -step))
    }

    @Test
    fun testAdjustRestSeconds_lowerBoundClamping() {
        // Arrange & Act & Assert: MIN_REST_SECONDS is 5
        assertEquals(5, WearWorkoutLogic.adjustRestSeconds(10, -15))
        assertEquals(5, WearWorkoutLogic.adjustRestSeconds(5, -15))
        assertEquals(5, WearWorkoutLogic.adjustRestSeconds(0, -100))
    }

    @Test
    fun testAdjustRestSeconds_upperBoundClamping() {
        // Arrange & Act & Assert: MAX_REST_SECONDS is 600
        assertEquals(600, WearWorkoutLogic.adjustRestSeconds(590, 20))
        assertEquals(600, WearWorkoutLogic.adjustRestSeconds(600, 15))
        assertEquals(600, WearWorkoutLogic.adjustRestSeconds(700, 50))
    }

    // ==========================================
    // In-Workout Set Update Tests (AK 3.11)
    // ==========================================

    @Test
    fun testUpdateSetInList_updatesTargetWithClamping() {
        // Arrange
        val s1 = WorkoutSet(id = "s1", sessionMachineId = "smi", setNumber = 1, reps = 10, weightKg = 80f, setType = SetType.NORMAL)
        val s2 = WorkoutSet(id = "s2", sessionMachineId = "smi", setNumber = 2, reps = 8, weightKg = 85f, setType = SetType.NORMAL)
        val sets = listOf(s1, s2)

        // Act: update set at index 1 with new weight & reps
        val updated = WearWorkoutLogic.updateSetInList(sets, targetIndex = 1, weightKg = 87.5f, reps = 9)

        // Assert
        assertEquals(2, updated.size)
        assertEquals(s1, updated[0])
        assertEquals("s2", updated[1].id)
        assertEquals(87.5f, updated[1].weightKg, 0.001f)
        assertEquals(9, updated[1].reps)
        assertEquals(2, updated[1].setNumber)
    }

    @Test
    fun testUpdateSetInList_clampsWeightAndReps() {
        // Arrange
        val s1 = WorkoutSet(id = "s1", sessionMachineId = "smi", setNumber = 1, reps = 10, weightKg = 80f, setType = SetType.NORMAL)
        val sets = listOf(s1)

        // Act: negative weight clamped to 0f, 0 reps clamped to 1
        val updatedLow = WearWorkoutLogic.updateSetInList(sets, targetIndex = 0, weightKg = -50f, reps = 0)
        assertEquals(0.0f, updatedLow[0].weightKg, 0.001f)
        assertEquals(1, updatedLow[0].reps)

        // Act: excessive values clamped to max bounds
        val updatedHigh = WearWorkoutLogic.updateSetInList(sets, targetIndex = 0, weightKg = 1500f, reps = 2000)
        assertEquals(1000.0f, updatedHigh[0].weightKg, 0.001f)
        assertEquals(999, updatedHigh[0].reps)
    }

    @Test
    fun testUpdateSetInList_invalidIndex_returnsUnmodified() {
        // Arrange
        val s1 = WorkoutSet(id = "s1", sessionMachineId = "smi", setNumber = 1, reps = 10, weightKg = 80f, setType = SetType.NORMAL)
        val sets = listOf(s1)

        // Act & Assert
        assertSame(sets, WearWorkoutLogic.updateSetInList(sets, targetIndex = -1, weightKg = 90f, reps = 10))
        assertSame(sets, WearWorkoutLogic.updateSetInList(sets, targetIndex = 5, weightKg = 90f, reps = 10))
    }

    @Test
    fun testUpdateSetById_updatesCorrectSet() {
        // Arrange
        val s1 = WorkoutSet(id = "s1", sessionMachineId = "smi", setNumber = 1, reps = 10, weightKg = 80f, setType = SetType.NORMAL)
        val s2 = WorkoutSet(id = "s2", sessionMachineId = "smi", setNumber = 2, reps = 8, weightKg = 85f, setType = SetType.NORMAL)
        val sets = listOf(s1, s2)

        // Act
        val updated = WearWorkoutLogic.updateSetById(sets, setId = "s1", weightKg = 82.5f, reps = 12)

        // Assert
        assertEquals(82.5f, updated[0].weightKg, 0.001f)
        assertEquals(12, updated[0].reps)
        assertEquals(s2, updated[1])

        // Unknown ID returns unmodified
        assertSame(sets, WearWorkoutLogic.updateSetById(sets, setId = "unknown", weightKg = 90f, reps = 10))
    }

    // ==========================================
    // In-Workout Set Deletion & Renumbering Tests (AK 3.12)
    // ==========================================

    @Test
    fun testDeleteSetAndRenumber_middleSet() {
        // Arrange: 3 sets with set numbers 1, 2, 3
        val s1 = WorkoutSet(id = "s1", sessionMachineId = "smi", setNumber = 1, reps = 10, weightKg = 80f, setType = SetType.NORMAL)
        val s2 = WorkoutSet(id = "s2", sessionMachineId = "smi", setNumber = 2, reps = 8, weightKg = 85f, setType = SetType.NORMAL)
        val s3 = WorkoutSet(id = "s3", sessionMachineId = "smi", setNumber = 3, reps = 6, weightKg = 90f, setType = SetType.NORMAL)
        val sets = listOf(s1, s2, s3)

        // Act: Delete middle set (index 1)
        val remaining = WearWorkoutLogic.deleteSetAndRenumber(sets, targetIndex = 1)

        // Assert: Subsequent sets renumbered sequentially (1, 2)
        assertEquals(2, remaining.size)
        assertEquals("s1", remaining[0].id)
        assertEquals(1, remaining[0].setNumber)
        assertEquals(80f, remaining[0].weightKg, 0.001f)

        assertEquals("s3", remaining[1].id)
        assertEquals(2, remaining[1].setNumber) // renumbered from 3 to 2
        assertEquals(90f, remaining[1].weightKg, 0.001f)
    }

    @Test
    fun testDeleteSetAndRenumber_firstSet() {
        // Arrange
        val s1 = WorkoutSet(id = "s1", sessionMachineId = "smi", setNumber = 1, reps = 10, weightKg = 80f, setType = SetType.NORMAL)
        val s2 = WorkoutSet(id = "s2", sessionMachineId = "smi", setNumber = 2, reps = 8, weightKg = 85f, setType = SetType.NORMAL)
        val sets = listOf(s1, s2)

        // Act: Delete first set
        val remaining = WearWorkoutLogic.deleteSetAndRenumber(sets, targetIndex = 0)

        // Assert: remaining set renumbered to 1
        assertEquals(1, remaining.size)
        assertEquals("s2", remaining[0].id)
        assertEquals(1, remaining[0].setNumber)
    }

    @Test
    fun testDeleteSetAndRenumber_lastSet() {
        // Arrange
        val s1 = WorkoutSet(id = "s1", sessionMachineId = "smi", setNumber = 1, reps = 10, weightKg = 80f, setType = SetType.NORMAL)
        val s2 = WorkoutSet(id = "s2", sessionMachineId = "smi", setNumber = 2, reps = 8, weightKg = 85f, setType = SetType.NORMAL)
        val sets = listOf(s1, s2)

        // Act: Delete last set
        val remaining = WearWorkoutLogic.deleteSetAndRenumber(sets, targetIndex = 1)

        // Assert
        assertEquals(1, remaining.size)
        assertEquals("s1", remaining[0].id)
        assertEquals(1, remaining[0].setNumber)
    }

    @Test
    fun testDeleteSetAndRenumber_invalidIndex_returnsUnmodified() {
        // Arrange
        val s1 = WorkoutSet(id = "s1", sessionMachineId = "smi", setNumber = 1, reps = 10, weightKg = 80f, setType = SetType.NORMAL)
        val sets = listOf(s1)

        // Act & Assert
        assertSame(sets, WearWorkoutLogic.deleteSetAndRenumber(sets, targetIndex = -1))
        assertSame(sets, WearWorkoutLogic.deleteSetAndRenumber(sets, targetIndex = 3))
    }

    @Test
    fun testDeleteSetById_deletesAndRenumbers() {
        // Arrange
        val s1 = WorkoutSet(id = "s1", sessionMachineId = "smi", setNumber = 1, reps = 10, weightKg = 80f, setType = SetType.NORMAL)
        val s2 = WorkoutSet(id = "s2", sessionMachineId = "smi", setNumber = 2, reps = 8, weightKg = 85f, setType = SetType.NORMAL)
        val sets = listOf(s1, s2)

        // Act
        val remaining = WearWorkoutLogic.deleteSetById(sets, setId = "s1")

        // Assert
        assertEquals(1, remaining.size)
        assertEquals("s2", remaining[0].id)
        assertEquals(1, remaining[0].setNumber)

        // Unknown ID returns unmodified
        assertSame(sets, WearWorkoutLogic.deleteSetById(sets, setId = "nonexistent"))
    }

    // ==========================================
    // Pause / Resume State Transition Tests (AK 3.1, AK 3.2, AK 3.3)
    // ==========================================

    @Test
    fun testPauseWorkout_transitionsFromActiveToPaused() {
        // Arrange
        val initial = WorkoutPauseState(isPaused = false, pausedAtMillis = null, totalPausedDurationMillis = 0L)

        // Act
        val paused = WearWorkoutLogic.pauseWorkout(initial, currentTimeMillis = 1000L)

        // Assert
        assertTrue(paused.isPaused)
        assertEquals(1000L, paused.pausedAtMillis)
        assertEquals(0L, paused.totalPausedDurationMillis)
    }

    @Test
    fun testPauseWorkout_alreadyPaused_isIdempotent() {
        // Arrange
        val paused = WorkoutPauseState(isPaused = true, pausedAtMillis = 1000L, totalPausedDurationMillis = 500L)

        // Act
        val result = WearWorkoutLogic.pauseWorkout(paused, currentTimeMillis = 2000L)

        // Assert: pausedAtMillis should remain the original pause timestamp
        assertEquals(1000L, result.pausedAtMillis)
        assertEquals(500L, result.totalPausedDurationMillis)
    }

    @Test
    fun testResumeWorkout_transitionsFromPausedToActive_accumulatesDuration() {
        // Arrange: paused at 1000L
        val paused = WorkoutPauseState(isPaused = true, pausedAtMillis = 1000L, totalPausedDurationMillis = 0L)

        // Act: resumed at 3500L (2500ms paused)
        val resumed = WearWorkoutLogic.resumeWorkout(paused, currentTimeMillis = 3500L)

        // Assert
        assertFalse(resumed.isPaused)
        assertNull(resumed.pausedAtMillis)
        assertEquals(2500L, resumed.totalPausedDurationMillis)
    }

    @Test
    fun testResumeWorkout_multiplePauses_accumulatesTotalDuration() {
        // Arrange
        var state = WorkoutPauseState()

        // 1st pause: 1000 to 3000 (duration = 2000)
        state = WearWorkoutLogic.pauseWorkout(state, 1000L)
        state = WearWorkoutLogic.resumeWorkout(state, 3000L)
        assertEquals(2000L, state.totalPausedDurationMillis)

        // 2nd pause: 5000 to 8000 (duration = 3000)
        state = WearWorkoutLogic.pauseWorkout(state, 5000L)
        state = WearWorkoutLogic.resumeWorkout(state, 8000L)
        assertEquals(5000L, state.totalPausedDurationMillis)
    }

    @Test
    fun testResumeWorkout_whenNotPaused_isIdempotent() {
        // Arrange
        val active = WorkoutPauseState(isPaused = false, pausedAtMillis = null, totalPausedDurationMillis = 1000L)

        // Act & Assert
        assertSame(active, WearWorkoutLogic.resumeWorkout(active, currentTimeMillis = 5000L))
    }

    @Test
    fun testCalculateAdjustedStartTime_accountingForPause() {
        // Arrange: 60s workout from 10_000 to 70_000, with 15s pause
        val start = 10_000L
        val end = 70_000L
        val paused = 15_000L

        // Act: Adjusted start shifts forward by 15s -> 25_000L
        // Effective active duration: 70_000 - 25_000 = 45_000L (exactly 60s - 15s)
        val adjustedStart = WearWorkoutLogic.calculateAdjustedStartTime(start, end, paused)

        // Assert
        assertEquals(25_000L, adjustedStart)
        assertEquals(45_000L, end - adjustedStart)
    }

    @Test
    fun testCalculateAdjustedStartTime_zeroPause() {
        // Arrange & Act & Assert
        assertEquals(10_000L, WearWorkoutLogic.calculateAdjustedStartTime(10_000L, 50_000L, 0L))
    }

    @Test
    fun testCalculateAdjustedStartTime_clampedWhenPauseExceedsElapsed() {
        // Arrange: pause duration exceeds total session elapsed
        val start = 10_000L
        val end = 20_000L // 10s elapsed
        val pause = 30_000L // 30s reported pause

        // Act & Assert: adjusted start should not exceed end time
        val adjusted = WearWorkoutLogic.calculateAdjustedStartTime(start, end, pause)
        assertEquals(end, adjusted)
    }

    // ==========================================
    // Station Navigation Tests (AK 3.5)
    // ==========================================

    @Test
    fun testFindNextStationIndex_and_hasNextStation() {
        // Arrange: 3 stations, station 1 is skipped
        val i0 = SessionMachineInstance(sessionId = "s", machineId = "m0", executionOrder = 0, isSkipped = false)
        val i1 = SessionMachineInstance(sessionId = "s", machineId = "m1", executionOrder = 1, isSkipped = true)
        val i2 = SessionMachineInstance(sessionId = "s", machineId = "m2", executionOrder = 2, isSkipped = false)
        val instances = listOf(i0, i1, i2)

        // Act & Assert from station 0: next non-skipped is station 2 (skipping 1)
        assertTrue(WearWorkoutLogic.hasNextStation(0, instances))
        assertEquals(2, WearWorkoutLogic.findNextStationIndex(0, instances))

        // Act & Assert from station 2: no next station
        assertFalse(WearWorkoutLogic.hasNextStation(2, instances))
        assertEquals(-1, WearWorkoutLogic.findNextStationIndex(2, instances))

        // All remaining skipped
        val allNextSkipped = listOf(i0, i1)
        assertFalse(WearWorkoutLogic.hasNextStation(0, allNextSkipped))
        assertEquals(-1, WearWorkoutLogic.findNextStationIndex(0, allNextSkipped))
    }
}
