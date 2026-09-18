package com.lockerlift.mobile

import com.lockerlift.core.database.logic.WorkoutTrackingLogic
import com.lockerlift.core.model.*
import com.lockerlift.core.sync.SessionMachineInstancePayload
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WorkoutSessionPayload
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MobileWorkoutTrackingTest {

    @Test
    fun testSessionInstanceCreationAndOrder() {
        val sessionId = UUID.randomUUID().toString()
        val machineIds = listOf("mach-1", "mach-2", "mach-3")

        val instances = machineIds.mapIndexed { index, mId ->
            SessionMachineInstance(
                sessionId = sessionId,
                machineId = mId,
                executionOrder = index,
                customSettingsNote = "Setup for $mId"
            )
        }

        assertEquals(3, instances.size)
        assertEquals(0, instances[0].executionOrder)
        assertEquals(1, instances[1].executionOrder)
        assertEquals(2, instances[2].executionOrder)
        assertEquals("mach-1", instances[0].machineId)
        assertEquals("mach-3", instances[2].machineId)
    }

    @Test
    fun testSetLoggingAndVolumeCalculation() {
        val instanceId = "smi-100"
        val sets = mutableListOf<WorkoutSet>()

        sets.add(WorkoutSet(sessionMachineId = instanceId, setNumber = 1, reps = 10, weightKg = 80f))
        sets.add(WorkoutSet(sessionMachineId = instanceId, setNumber = 2, reps = 8, weightKg = 85f))
        sets.add(WorkoutSet(sessionMachineId = instanceId, setNumber = 3, reps = 6, weightKg = 90f))

        val totalReps = sets.sumOf { it.reps }
        val totalVolume = sets.sumOf { (it.weightKg * it.reps).toDouble() }

        assertEquals(24, totalReps)
        assertEquals(800.0 + 680.0 + 540.0, totalVolume, 0.001)
    }

    @Test
    fun testDoubleProgressionTriggerOnMobile() {
        val threshold = 12
        val increment = 2.5f

        // Under threshold: no progression
        assertFalse(WorkoutTrackingLogic.isProgressionProposed(reps = 10, threshold = threshold))
        assertEquals(80f, WorkoutTrackingLogic.calculateNextWeight(80f, increment, shouldProgress = false), 0.001f)

        // Threshold reached: progression proposed
        assertTrue(WorkoutTrackingLogic.isProgressionProposed(reps = 12, threshold = threshold))
        assertEquals(82.5f, WorkoutTrackingLogic.calculateNextWeight(80f, increment, shouldProgress = true), 0.001f)

        // Over threshold: progression proposed
        assertTrue(WorkoutTrackingLogic.isProgressionProposed(reps = 14, threshold = threshold))
        assertEquals(82.5f, WorkoutTrackingLogic.calculateNextWeight(80f, increment, shouldProgress = true), 0.001f)
    }

    @Test
    fun testTemplateConsolidationDetection() {
        val initialTemplateMachineIds = listOf("m1", "m2", "m3")

        // 1. Exact match: no variations
        val noVariationInstances = listOf(
            SessionMachineInstance(sessionId = "s1", machineId = "m1", executionOrder = 0, isSkipped = false),
            SessionMachineInstance(sessionId = "s1", machineId = "m2", executionOrder = 1, isSkipped = false),
            SessionMachineInstance(sessionId = "s1", machineId = "m3", executionOrder = 2, isSkipped = false)
        )
        val activeIds1 = noVariationInstances.filter { !it.isSkipped }.map { it.machineId }
        assertFalse(activeIds1 != initialTemplateMachineIds)

        // 2. Skipped station: variation detected
        val skippedInstances = listOf(
            SessionMachineInstance(sessionId = "s1", machineId = "m1", executionOrder = 0, isSkipped = false),
            SessionMachineInstance(sessionId = "s1", machineId = "m2", executionOrder = 1, isSkipped = true),
            SessionMachineInstance(sessionId = "s1", machineId = "m3", executionOrder = 2, isSkipped = false)
        )
        val activeIds2 = skippedInstances.filter { !it.isSkipped }.map { it.machineId }
        assertTrue(activeIds2 != initialTemplateMachineIds)

        // 3. Replaced station: variation detected
        val replacedInstances = listOf(
            SessionMachineInstance(sessionId = "s1", machineId = "m1", executionOrder = 0, isSkipped = false),
            SessionMachineInstance(sessionId = "s1", machineId = "m-replacement", executionOrder = 1, isSkipped = false),
            SessionMachineInstance(sessionId = "s1", machineId = "m3", executionOrder = 2, isSkipped = false)
        )
        val activeIds3 = replacedInstances.filter { !it.isSkipped }.map { it.machineId }
        assertTrue(activeIds3 != initialTemplateMachineIds)
    }

    @Test
    fun testBidirectionalPayloadSerialization() {
        val session = WorkoutSession(
            id = "sess-mobile-1",
            templateId = "tmpl-push",
            startTime = 1700000000000L,
            endTime = 1700003600000L,
            originDevice = "MOBILE",
            syncStatus = SyncStatus.PENDING_SYNC,
            notes = "Great phone session"
        )
        val machine = Machine(
            id = "mach-press",
            name = "Overhead Press",
            targetMuscleGroup = "Shoulders",
            machineSettingsNote = "Seat 4",
            defaultIncrementKg = 1.25f
        )
        val instance = SessionMachineInstance(
            id = "smi-1",
            sessionId = session.id,
            machineId = machine.id,
            executionOrder = 0,
            customSettingsNote = "Seat 4"
        )
        val set = WorkoutSet(
            id = "set-1",
            sessionMachineId = instance.id,
            setNumber = 1,
            reps = 10,
            weightKg = 50f
        )

        val payload = WorkoutSessionPayload(
            session = session,
            templateName = "Push Day",
            machineInstances = listOf(
                SessionMachineInstancePayload(
                    instance = instance,
                    machine = machine,
                    sets = listOf(set)
                )
            )
        )

        val encodedJson = SyncPayloadSerializer.encodeSessionPayload(payload)
        val decodedPayload = SyncPayloadSerializer.decodeSessionPayload(encodedJson)

        assertEquals(session.id, decodedPayload.session.id)
        assertEquals("MOBILE", decodedPayload.session.originDevice)
        assertEquals("Push Day", decodedPayload.templateName)
        assertEquals(1, decodedPayload.machineInstances.size)
        assertEquals(machine.name, decodedPayload.machineInstances[0].machine.name)
        assertEquals(50f, decodedPayload.machineInstances[0].sets[0].weightKg, 0.001f)
    }

    @Test
    fun testInWorkoutSetUpdateAndRenumbering() {
        val instanceId = "smi-test-1"
        val set1 = WorkoutSet(sessionMachineId = instanceId, setNumber = 1, weightKg = 70f, reps = 10)
        val set2 = WorkoutSet(sessionMachineId = instanceId, setNumber = 2, weightKg = 70f, reps = 10)
        val set3 = WorkoutSet(sessionMachineId = instanceId, setNumber = 3, weightKg = 70f, reps = 8)

        val initialSets = listOf(set1, set2, set3)

        // 1. In-workout edit of set 2: user corrects 70kg -> 75kg, 10 reps -> 9 reps
        val editedSets = WorkoutTrackingLogic.updateSetInList(
            sets = initialSets,
            targetIndex = 1,
            weightKg = 75f,
            reps = 9
        )
        assertEquals(3, editedSets.size)
        assertEquals(75f, editedSets[1].weightKg, 0.001f)
        assertEquals(9, editedSets[1].reps)
        assertEquals(2, editedSets[1].setNumber)

        // 2. In-workout deletion of set 1: set 2 and 3 should renumber to 1 and 2
        val remainingSets = WorkoutTrackingLogic.deleteSetAndRenumber(editedSets, targetIndex = 0)
        assertEquals(2, remainingSets.size)
        assertEquals(1, remainingSets[0].setNumber)
        assertEquals(75f, remainingSets[0].weightKg, 0.001f)
        assertEquals(9, remainingSets[0].reps)
        assertEquals(2, remainingSets[1].setNumber)
        assertEquals(70f, remainingSets[1].weightKg, 0.001f)
        assertEquals(8, remainingSets[1].reps)
    }

    @Test
    fun testSetTablePreviousPerformanceMatching() {
        val histSets = listOf(
            WorkoutSet(sessionMachineId = "old-smi-1", setNumber = 1, weightKg = 80f, reps = 10),
            WorkoutSet(sessionMachineId = "old-smi-1", setNumber = 2, weightKg = 80f, reps = 9),
            WorkoutSet(sessionMachineId = "old-smi-1", setNumber = 3, weightKg = 80f, reps = 7)
        )

        val lastHistSets = WorkoutTrackingLogic.extractLastSessionSetsFromDomain(histSets)
        assertEquals(3, lastHistSets.size)

        // Match for set 1
        val prev1 = lastHistSets.getOrNull(0)
        assertNotNull(prev1)
        assertEquals("80", WorkoutTrackingLogic.formatWeight(prev1!!.weightKg))
        assertEquals(10, prev1.reps)

        // Match for set 2
        val prev2 = lastHistSets.getOrNull(1)
        assertNotNull(prev2)
        assertEquals("80", WorkoutTrackingLogic.formatWeight(prev2!!.weightKg))
        assertEquals(9, prev2.reps)

        // Set 4 has no previous historical set
        val prev4 = lastHistSets.getOrNull(3)
        assertNull(prev4)
    }

    @Test
    fun testStationReorderingAndSwap() {
        val bench = Machine(id = "m-bench", name = "Bench Press", targetMuscleGroup = "Chest")
        val incline = Machine(id = "m-incline", name = "Incline Dumbbell Press", targetMuscleGroup = "Chest")
        val fly = Machine(id = "m-fly", name = "Cable Fly", targetMuscleGroup = "Chest")

        val inst0 = SessionMachineInstance(id = "inst-0", sessionId = "s", machineId = bench.id, executionOrder = 0)
        val inst1 = SessionMachineInstance(id = "inst-1", sessionId = "s", machineId = incline.id, executionOrder = 1)
        val inst2 = SessionMachineInstance(id = "inst-2", sessionId = "s", machineId = fly.id, executionOrder = 2)

        val instances = listOf(inst0, inst1, inst2)

        // Swap machine in inst1 (e.g. replaced incline dumbbells with machine chest press)
        val chestPress = Machine(id = "m-press", name = "Chest Press Machine", targetMuscleGroup = "Chest", machineSettingsNote = "Seat 5")
        val swapped = WorkoutTrackingLogic.swapMachinePreservingSets(instances, targetIndex = 1, newMachine = chestPress)
        assertEquals("inst-1", swapped[1].id)
        assertEquals("m-press", swapped[1].machineId)
        assertEquals("Seat 5", swapped[1].customSettingsNote)

        // Reorder: move fly (index 2) to the front (index 0)
        val reordered = WorkoutTrackingLogic.reorderInstances(swapped, fromIndex = 2, toIndex = 0)
        assertEquals("inst-2", reordered[0].id)
        assertEquals(0, reordered[0].executionOrder)
        assertEquals("inst-0", reordered[1].id)
        assertEquals(1, reordered[1].executionOrder)
        assertEquals("inst-1", reordered[2].id)
        assertEquals(2, reordered[2].executionOrder)
    }

    @Test
    fun testNextUnfinishedStationResolution() {
        val instA = SessionMachineInstance(id = "s-a", sessionId = "s", machineId = "ma", executionOrder = 0)
        val instB = SessionMachineInstance(id = "s-b", sessionId = "s", machineId = "mb", executionOrder = 1)
        val instC = SessionMachineInstance(id = "s-c", sessionId = "s", machineId = "mc", executionOrder = 2)
        val instances = listOf(instA, instB, instC)

        val set = WorkoutSet(sessionMachineId = "s-a", setNumber = 1, weightKg = 50f, reps = 10)
        val loggedSets = mapOf(
            "s-a" to listOf(set),
            "s-b" to emptyList(),
            "s-c" to emptyList()
        )

        val nextFromA = WorkoutTrackingLogic.findNextUnfinishedStationIndex(0, instances, loggedSets)
        assertEquals(1, nextFromA)

        // When B is also logged, next from A is C (index 2)
        val loggedSets2 = mapOf(
            "s-a" to listOf(set),
            "s-b" to listOf(set),
            "s-c" to emptyList()
        )
        val nextFromA2 = WorkoutTrackingLogic.findNextUnfinishedStationIndex(0, instances, loggedSets2)
        assertEquals(2, nextFromA2)
    }

    @Test
    fun testWeightStepperInteractionAndClamping() {
        val increment = 2.5f
        val (minusSteps, plusSteps) = WorkoutTrackingLogic.calculateWeightSteppers(increment)

        var weight = 20.0f

        // Apply +2.5
        val plus25 = plusSteps.first { it == 2.5f }
        weight = WorkoutTrackingLogic.clampWeight(weight + plus25)
        assertEquals(22.5f, weight, 0.001f)

        // Apply -5.0
        val minus5 = minusSteps.first { it == -5.0f }
        weight = WorkoutTrackingLogic.clampWeight(weight + minus5)
        assertEquals(17.5f, weight, 0.001f)

        // Apply negative stepping that goes below zero -> clamps to 0
        weight = WorkoutTrackingLogic.clampWeight(weight - 50f)
        assertEquals(0.0f, weight, 0.001f)

        // Formatting of steps ensures no character wrapping issues
        minusSteps.forEach { delta ->
            val label = WorkoutTrackingLogic.formatDelta(delta)
            assertTrue(label.startsWith("-"))
            assertFalse(label.contains(" "))
        }
        plusSteps.forEach { delta ->
            val label = WorkoutTrackingLogic.formatDelta(delta)
            assertTrue(label.startsWith("+"))
            assertFalse(label.contains(" "))
        }
    }
}
