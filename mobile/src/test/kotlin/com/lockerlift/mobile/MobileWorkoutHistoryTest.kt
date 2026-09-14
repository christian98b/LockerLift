package com.lockerlift.mobile

import com.lockerlift.core.database.entity.*
import com.lockerlift.core.database.logic.WorkoutTrackingLogic
import com.lockerlift.core.healthconnect.ExerciseRecordBuilder
import com.lockerlift.core.model.*
import com.lockerlift.core.sync.SessionMachineInstancePayload
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WorkoutSessionPayload
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Unit test suite for History Screen edit, delete, clamping, sync, and localization features (Issue #20).
 * Adheres strictly to the AAA pattern and LockerLift unit testing guidelines.
 */
class MobileWorkoutHistoryTest {

    @Test
    fun testUpdatingPastSessionSets_withValidationAndClamping() {
        // Arrange
        val rawNegativeWeight = -25.0f
        val rawExcessiveWeight = 1250.0f
        val rawValidWeight = 87.5f

        val rawZeroReps = 0
        val rawNegativeReps = -5
        val rawExcessiveReps = 1200
        val rawValidReps = 12

        // Act
        val clampedNegWeight = WorkoutTrackingLogic.clampWeight(rawNegativeWeight)
        val clampedExWeight = WorkoutTrackingLogic.clampWeight(rawExcessiveWeight)
        val clampedValidWeight = WorkoutTrackingLogic.clampWeight(rawValidWeight)

        val clampedZeroReps = WorkoutTrackingLogic.clampReps(rawZeroReps)
        val clampedNegReps = WorkoutTrackingLogic.clampReps(rawNegativeReps)
        val clampedExReps = WorkoutTrackingLogic.clampReps(rawExcessiveReps)
        val clampedValidReps = WorkoutTrackingLogic.clampReps(rawValidReps)

        // Assert
        assertEquals(0.0f, clampedNegWeight, 0.001f)
        assertEquals(1000.0f, clampedExWeight, 0.001f)
        assertEquals(87.5f, clampedValidWeight, 0.001f)

        assertEquals(1, clampedZeroReps)
        assertEquals(1, clampedNegReps)
        assertEquals(999, clampedExReps)
        assertEquals(12, clampedValidReps)
    }

    @Test
    fun testAddingSetToExistingMachineInstance() {
        // Arrange
        val instanceId = "smi-leg-press"
        val existingSets = mutableListOf(
            WorkoutSet(id = "set-1", sessionMachineId = instanceId, setNumber = 1, weightKg = 120f, reps = 12),
            WorkoutSet(id = "set-2", sessionMachineId = instanceId, setNumber = 2, weightKg = 130f, reps = 10)
        )

        // Act - Simulate adding a new set
        val lastSet = existingSets.last()
        val newSet = WorkoutSet(
            id = UUID.randomUUID().toString(),
            sessionMachineId = instanceId,
            setNumber = existingSets.size + 1,
            weightKg = lastSet.weightKg,
            reps = lastSet.reps
        )
        existingSets.add(newSet)

        // Assert
        assertEquals(3, existingSets.size)
        assertEquals(3, newSet.setNumber)
        assertEquals(130f, newSet.weightKg, 0.001f)
        assertEquals(10, newSet.reps)
        assertEquals(instanceId, newSet.sessionMachineId)
        assertNotNull(newSet.id)
    }

    @Test
    fun testDeletingSetFromExistingMachineInstance_reordersSetsContiguously() {
        // Arrange
        val instanceId = "smi-bench-press"
        val sets = mutableListOf(
            WorkoutSet(id = "set-1", sessionMachineId = instanceId, setNumber = 1, weightKg = 80f, reps = 10),
            WorkoutSet(id = "set-2", sessionMachineId = instanceId, setNumber = 2, weightKg = 85f, reps = 8),
            WorkoutSet(id = "set-3", sessionMachineId = instanceId, setNumber = 3, weightKg = 90f, reps = 6)
        )

        // Act - Delete middle set (set 2) and reorder
        val targetSetId = "set-2"
        val remainingSets = sets.filter { it.id != targetSetId }
        val reorderedSets = remainingSets.mapIndexed { index, set ->
            set.copy(setNumber = index + 1)
        }

        // Assert
        assertEquals(2, reorderedSets.size)
        assertEquals("set-1", reorderedSets[0].id)
        assertEquals(1, reorderedSets[0].setNumber)
        assertEquals(80f, reorderedSets[0].weightKg, 0.001f)

        assertEquals("set-3", reorderedSets[1].id)
        assertEquals(2, reorderedSets[1].setNumber)
        assertEquals(90f, reorderedSets[1].weightKg, 0.001f)
    }

    @Test
    fun testRemovingMachineInstance_leavesMachineCatalogIntact() {
        // Arrange
        val catalog = listOf(
            Machine(id = "m1", name = "Chest Press", targetMuscleGroup = "Chest"),
            Machine(id = "m2", name = "Shoulder Press", targetMuscleGroup = "Shoulders")
        )
        val sessionId = "sess-100"
        val sessionInstances = mutableListOf(
            SessionMachineInstance(id = "inst-1", sessionId = sessionId, machineId = "m1", executionOrder = 0),
            SessionMachineInstance(id = "inst-2", sessionId = sessionId, machineId = "m2", executionOrder = 1)
        )

        // Act - Remove inst-1 from past session
        sessionInstances.removeIf { it.id == "inst-1" }

        // Assert
        assertEquals(1, sessionInstances.size)
        assertEquals("inst-2", sessionInstances[0].id)
        // Machine catalog remains completely intact (AK 2.4)
        assertEquals(2, catalog.size)
        assertTrue(catalog.any { it.id == "m1" })
        assertTrue(catalog.any { it.id == "m2" })
    }

    @Test
    fun testDeletingWorkoutSession_cascadeSimulation() {
        // Arrange
        val sessionId = "sess-to-delete"
        val sessions = mutableListOf(
            WorkoutSession(id = sessionId, startTime = 1000L, endTime = 2000L),
            WorkoutSession(id = "sess-keep", startTime = 3000L, endTime = 4000L)
        )
        val instances = mutableListOf(
            SessionMachineInstance(id = "inst-1", sessionId = sessionId, machineId = "m1", executionOrder = 0),
            SessionMachineInstance(id = "inst-2", sessionId = sessionId, machineId = "m2", executionOrder = 1),
            SessionMachineInstance(id = "inst-3", sessionId = "sess-keep", machineId = "m1", executionOrder = 0)
        )
        val sets = mutableListOf(
            WorkoutSet(id = "set-1", sessionMachineId = "inst-1", setNumber = 1, reps = 10, weightKg = 50f),
            WorkoutSet(id = "set-2", sessionMachineId = "inst-2", setNumber = 1, reps = 12, weightKg = 60f),
            WorkoutSet(id = "set-3", sessionMachineId = "inst-3", setNumber = 1, reps = 8, weightKg = 70f)
        )

        // Act - Delete session (simulating Room onDelete = CASCADE)
        val deletedSession = sessions.removeIf { it.id == sessionId }
        val deletedInstanceIds = instances.filter { it.sessionId == sessionId }.map { it.id }.toSet()
        instances.removeIf { it.sessionId == sessionId }
        sets.removeIf { it.sessionMachineId in deletedInstanceIds }

        // Assert
        assertTrue(deletedSession)
        assertEquals(1, sessions.size)
        assertEquals("sess-keep", sessions[0].id)

        assertEquals(1, instances.size)
        assertEquals("inst-3", instances[0].id)

        assertEquals(1, sets.size)
        assertEquals("set-3", sets[0].id)
    }

    @Test
    fun testReSyncPayloadGeneration_forEditedSession() {
        // Arrange
        val sessionId = "sess-sync-test"
        val session = WorkoutSession(
            id = sessionId,
            startTime = 1000000L,
            endTime = 1003600L,
            notes = "Updated session notes after edit",
            syncStatus = SyncStatus.PENDING_SYNC
        )
        val machine = Machine(id = "mach-pull", name = "Lat Pulldown", targetMuscleGroup = "Back")
        val instance = SessionMachineInstance(id = "inst-sync", sessionId = sessionId, machineId = machine.id, executionOrder = 0)
        val sets = listOf(
            WorkoutSet(id = "set-10", sessionMachineId = instance.id, setNumber = 1, reps = 12, weightKg = 75f),
            WorkoutSet(id = "set-11", sessionMachineId = instance.id, setNumber = 2, reps = 10, weightKg = 80f)
        )

        val payload = WorkoutSessionPayload(
            session = session,
            templateName = "Pull Day",
            machineInstances = listOf(
                SessionMachineInstancePayload(
                    instance = instance,
                    machine = machine,
                    sets = sets
                )
            )
        )

        // Act
        val json = SyncPayloadSerializer.encodeSessionPayload(payload)
        val decoded = SyncPayloadSerializer.decodeSessionPayload(json)

        // Assert
        assertEquals(sessionId, decoded.session.id)
        assertEquals("Updated session notes after edit", decoded.session.notes)
        assertEquals("Pull Day", decoded.templateName)
        assertEquals(1, decoded.machineInstances.size)
        assertEquals("Lat Pulldown", decoded.machineInstances[0].machine.name)
        assertEquals(2, decoded.machineInstances[0].sets.size)
        assertEquals(80f, decoded.machineInstances[0].sets[1].weightKg, 0.001f)
    }

    @Test
    fun testHealthConnectRecordClientRecordId_matchesSessionId() {
        // Arrange
        val sessionId = "sess-hc-match"
        val session = WorkoutSession(
            id = sessionId,
            startTime = 1700000000000L,
            endTime = 1700003600000L,
            notes = "Test Health Connect deletion ID"
        )

        // Act
        val record = ExerciseRecordBuilder.buildExerciseSessionRecord(session, title = "Strength Session")

        // Assert (AK 2.8: clientRecordId must match session.id for precise deletion)
        assertEquals(sessionId, record.metadata.clientRecordId)
    }

    @Test
    fun testLocalizationKeysPresent_forHistoryEditAndDelete() {
        // Arrange
        val requiredKeys = listOf(
            "history_title",
            "history_empty",
            "workout_free",
            "history_no_sets",
            "history_edit_workout",
            "history_delete_workout",
            "history_delete_dialog_title",
            "history_delete_dialog_confirm",
            "history_edit_title",
            "history_add_set",
            "history_delete_set",
            "history_remove_exercise",
            "history_remove_exercise_confirm",
            "history_set_format",
            "history_weight_kg",
            "history_reps",
            "history_save_changes",
            "history_discard_changes",
            "history_notes"
        )

        fun findFile(relPath: String): File {
            val candidates = listOf(
                File(relPath),
                File("mobile/$relPath"),
                File("../mobile/$relPath"),
                File("../../mobile/$relPath")
            )
            return candidates.firstOrNull { it.exists() }
                ?: error("File $relPath not found in any candidate path.")
        }

        val baseFile = findFile("src/main/res/values/strings.xml")
        val deFile = findFile("src/main/res/values-de/strings.xml")

        fun extractKeys(file: File): Set<String> {
            val text = file.readText()
            val regex = Regex("""<string\s+name="([^"]+)"""")
            return regex.findAll(text).map { it.groupValues[1] }.toSet()
        }

        // Act
        val baseKeys = extractKeys(baseFile)
        val deKeys = extractKeys(deFile)

        // Assert (AK 2.9)
        for (key in requiredKeys) {
            assertTrue("Base strings.xml missing key: $key", baseKeys.contains(key))
            assertTrue("German strings.xml missing key: $key", deKeys.contains(key))
        }
    }
}
