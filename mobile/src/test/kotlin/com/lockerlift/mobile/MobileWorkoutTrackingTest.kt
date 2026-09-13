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
}
