package com.lockerlift.core.sync

import com.lockerlift.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SyncPayloadSerializerTest {

    @Test
    fun testWorkoutSessionPayloadRoundTrip() {
        val session = WorkoutSession(
            id = "sess-100",
            templateId = "tmpl-200",
            startTime = 1000000L,
            endTime = 1003600L,
            originDevice = "WEAR_OS",
            syncStatus = SyncStatus.PENDING_SYNC,
            notes = "Super Training"
        )

        val machine = Machine(
            id = "mach-1",
            name = "Beinpresse",
            targetMuscleGroup = "Beine",
            machineSettingsNote = "Stufe 5",
            defaultIncrementKg = 10f
        )

        val instance = SessionMachineInstance(
            id = "inst-1",
            sessionId = session.id,
            machineId = machine.id,
            executionOrder = 0,
            isSkipped = false
        )

        val set1 = WorkoutSet(
            id = "set-1",
            sessionMachineId = instance.id,
            setNumber = 1,
            reps = 12,
            weightKg = 120f,
            cadence = "3-1-1-0",
            setType = SetType.NORMAL
        )

        val set2 = WorkoutSet(
            id = "set-2",
            sessionMachineId = instance.id,
            setNumber = 2,
            reps = 10,
            weightKg = 130f,
            cadence = "3-1-1-0",
            setType = SetType.NORMAL
        )

        val payload = WorkoutSessionPayload(
            session = session,
            templateName = "Beintag",
            machineInstances = listOf(
                SessionMachineInstancePayload(
                    instance = instance,
                    machine = machine,
                    sets = listOf(set1, set2)
                )
            )
        )

        val serializedJson = SyncPayloadSerializer.encodeSessionPayload(payload)
        assertNotNull(serializedJson)
        assertTrue(serializedJson.contains("Beinpresse"))
        assertTrue(serializedJson.contains("Beintag"))

        val deserializedPayload = SyncPayloadSerializer.decodeSessionPayload(serializedJson)
        assertEquals(payload.session.id, deserializedPayload.session.id)
        assertEquals(payload.templateName, deserializedPayload.templateName)
        assertEquals(1, deserializedPayload.machineInstances.size)

        val firstInstance = deserializedPayload.machineInstances.first()
        assertEquals(machine.name, firstInstance.machine.name)
        assertEquals(2, firstInstance.sets.size)
        assertEquals(120f, firstInstance.sets[0].weightKg, 0.001f)
        assertEquals(12, firstInstance.sets[0].reps)
        assertEquals(130f, firstInstance.sets[1].weightKg, 0.001f)
        assertEquals(10, firstInstance.sets[1].reps)
    }
}
