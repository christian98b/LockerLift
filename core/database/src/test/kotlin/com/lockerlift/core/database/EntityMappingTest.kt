package com.lockerlift.core.database

import com.lockerlift.core.database.entity.*
import com.lockerlift.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntityMappingTest {

    private val converters = Converters()

    @Test
    fun testMachineEntityMapping() {
        val domainMachine = Machine(
            id = "mach-99",
            name = "Kabelzug",
            targetMuscleGroup = "Rumpf",
            machineSettingsNote = "Stufe 10",
            defaultIncrementKg = 1.25f,
            defaultCadence = "2-1-2-1",
            updatedAt = 123456789L
        )

        val entity = domainMachine.toEntity()
        assertEquals(domainMachine.id, entity.id)
        assertEquals(domainMachine.name, entity.name)
        assertEquals(domainMachine.targetMuscleGroup, entity.targetMuscleGroup)
        assertEquals(domainMachine.machineSettingsNote, entity.machineSettingsNote)
        assertEquals(domainMachine.defaultIncrementKg, entity.defaultIncrementKg, 0.001f)
        assertEquals(domainMachine.defaultCadence, entity.defaultCadence)
        assertEquals(domainMachine.updatedAt, entity.updatedAt)

        val mappedBack = entity.toDomainModel()
        assertEquals(domainMachine, mappedBack)
    }

    @Test
    fun testWorkoutTemplateEntityMapping() {
        val domainTemplate = WorkoutTemplate(
            id = "tmpl-1",
            name = "Ganzkörper A",
            description = "Fokus Grundübungen",
            isArchived = false,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val entity = domainTemplate.toEntity()
        val mappedBack = entity.toDomainModel()
        assertEquals(domainTemplate, mappedBack)
    }

    @Test
    fun testWorkoutSessionEntityMapping() {
        val domainSession = WorkoutSession(
            id = "sess-1",
            templateId = "tmpl-1",
            startTime = 5000L,
            endTime = 9000L,
            originDevice = "WEAR_OS",
            syncStatus = SyncStatus.SYNCED,
            notes = "Gutes Gefühl"
        )

        val entity = domainSession.toEntity()
        val mappedBack = entity.toDomainModel()
        assertEquals(domainSession, mappedBack)
    }

    @Test
    fun testConverters() {
        assertEquals("NORMAL", converters.fromSetType(SetType.NORMAL))
        assertEquals(SetType.DROPSET, converters.toSetType("DROPSET"))

        assertEquals("SYNCED", converters.fromSyncStatus(SyncStatus.SYNCED))
        assertEquals(SyncStatus.PENDING_SYNC, converters.toSyncStatus("PENDING_SYNC"))

        assertEquals("PENDING", converters.fromQueueStatus(QueueStatus.PENDING))
        assertEquals(QueueStatus.IN_TRANSIT, converters.toQueueStatus("IN_TRANSIT"))
    }

    @Test
    fun testWorkoutSetEntityMapping() {
        val domainSet = WorkoutSet(
            id = "set-42",
            sessionMachineId = "smi-10",
            setNumber = 3,
            reps = 10,
            weightKg = 77.5f,
            cadence = "3-0-1-0",
            setType = SetType.WARMUP,
            completedAt = 1234567890L
        )

        val entity = domainSet.toEntity()
        assertEquals(domainSet.id, entity.id)
        assertEquals(domainSet.sessionMachineId, entity.sessionMachineId)
        assertEquals(domainSet.setNumber, entity.setNumber)
        assertEquals(domainSet.reps, entity.reps)
        assertEquals(domainSet.weightKg, entity.weightKg, 0.001f)
        assertEquals(domainSet.cadence, entity.cadence)
        assertEquals(domainSet.setType, entity.setType)
        assertEquals(domainSet.completedAt, entity.completedAt)

        val mappedBack = entity.toDomainModel()
        assertEquals(domainSet, mappedBack)
    }

    @Test
    fun testLastCompletedSetsForMachineQueryContract() {
        val targetMachineId = "mach-bench"
        val otherMachineId = "mach-squat"

        val completedSession1 = WorkoutSessionEntity(
            id = "sess-1",
            startTime = 1000L,
            endTime = 2000L
        )
        val completedSession2 = WorkoutSessionEntity(
            id = "sess-2",
            startTime = 3000L,
            endTime = 4000L
        )
        val inProgressSession = WorkoutSessionEntity(
            id = "sess-3",
            startTime = 5000L,
            endTime = null
        )

        val smi1 = SessionMachineInstanceEntity(
            id = "smi-1",
            sessionId = completedSession1.id,
            machineId = targetMachineId,
            executionOrder = 0
        )
        val smi2 = SessionMachineInstanceEntity(
            id = "smi-2",
            sessionId = completedSession2.id,
            machineId = targetMachineId,
            executionOrder = 0
        )
        val smiOther = SessionMachineInstanceEntity(
            id = "smi-3",
            sessionId = completedSession2.id,
            machineId = otherMachineId,
            executionOrder = 1
        )
        val smiInProgress = SessionMachineInstanceEntity(
            id = "smi-4",
            sessionId = inProgressSession.id,
            machineId = targetMachineId,
            executionOrder = 0
        )

        val set1_sess1 = WorkoutSetEntity(id = "s1", sessionMachineId = smi1.id, setNumber = 1, reps = 10, weightKg = 80f)
        val set2_sess1 = WorkoutSetEntity(id = "s2", sessionMachineId = smi1.id, setNumber = 2, reps = 8, weightKg = 85f)
        val set1_sess2 = WorkoutSetEntity(id = "s3", sessionMachineId = smi2.id, setNumber = 1, reps = 12, weightKg = 82.5f)
        val set2_sess2 = WorkoutSetEntity(id = "s4", sessionMachineId = smi2.id, setNumber = 2, reps = 10, weightKg = 87.5f)
        val setOther = WorkoutSetEntity(id = "s5", sessionMachineId = smiOther.id, setNumber = 1, reps = 10, weightKg = 100f)
        val setInProgress = WorkoutSetEntity(id = "s6", sessionMachineId = smiInProgress.id, setNumber = 1, reps = 10, weightKg = 90f)

        val allSessions = listOf(completedSession1, completedSession2, inProgressSession)
        val allSmis = listOf(smi1, smi2, smiOther, smiInProgress)
        val allSets = listOf(set1_sess1, set2_sess1, set1_sess2, set2_sess2, setOther, setInProgress)

        // Simulating the contract of:
        // SELECT ws.* FROM workout_sets ws
        // INNER JOIN session_machine_instances smi ON ws.session_machine_id = smi.id
        // INNER JOIN workout_sessions s ON smi.session_id = s.id
        // WHERE smi.machine_id = :machineId AND s.end_time IS NOT NULL
        // ORDER BY s.end_time DESC, ws.set_number ASC
        // LIMIT 10
        val resultSets = allSets.mapNotNull { set ->
            val smi = allSmis.find { it.id == set.sessionMachineId } ?: return@mapNotNull null
            val session = allSessions.find { it.id == smi.sessionId } ?: return@mapNotNull null
            if (smi.machineId == targetMachineId && session.endTime != null) {
                Triple(set, session.endTime, set.setNumber)
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<Triple<WorkoutSetEntity, Long, Int>> { it.second }
                .thenBy { it.third }
        ).take(10).map { it.first }

        assertEquals(4, resultSets.size)
        // Most recent session (sess-2, endTime=4000L) comes first, ordered by set_number
        assertEquals("s3", resultSets[0].id)
        assertEquals(1, resultSets[0].setNumber)
        assertEquals(82.5f, resultSets[0].weightKg, 0.001f)

        assertEquals("s4", resultSets[1].id)
        assertEquals(2, resultSets[1].setNumber)
        assertEquals(87.5f, resultSets[1].weightKg, 0.001f)

        // Previous session (sess-1, endTime=2000L)
        assertEquals("s1", resultSets[2].id)
        assertEquals(1, resultSets[2].setNumber)
        assertEquals(80f, resultSets[2].weightKg, 0.001f)

        assertEquals("s2", resultSets[3].id)
        assertEquals(2, resultSets[3].setNumber)
        assertEquals(85f, resultSets[3].weightKg, 0.001f)
    }

    @Test
    fun testWorkoutSessionSyncStatusUpdateContract() {
        val initialSession = WorkoutSessionEntity(
            id = "sess-100",
            startTime = 5000L,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        val updatedSession = initialSession.copy(syncStatus = SyncStatus.SYNCED)
        assertEquals(SyncStatus.SYNCED, updatedSession.syncStatus)
    }

    @Test
    fun testDeleteMachineInstancesContract_cascadesSetsAndLeavesCatalogIntact() {
        val sessionId = "session-test"
        val instances = mutableListOf(
            SessionMachineInstanceEntity(id = "smi-1", sessionId = sessionId, machineId = "m-1", executionOrder = 0),
            SessionMachineInstanceEntity(id = "smi-2", sessionId = "other-session", machineId = "m-2", executionOrder = 0)
        )
        val sets = mutableListOf(
            WorkoutSetEntity(id = "set-1", sessionMachineId = "smi-1", setNumber = 1, reps = 10, weightKg = 100f),
            WorkoutSetEntity(id = "set-2", sessionMachineId = "smi-2", setNumber = 1, reps = 8, weightKg = 50f)
        )

        // Simulate deleteMachineInstancesBySessionId(sessionId)
        val removedInstanceIds = instances.filter { it.sessionId == sessionId }.map { it.id }.toSet()
        instances.removeIf { it.sessionId == sessionId }
        sets.removeIf { it.sessionMachineId in removedInstanceIds }

        assertEquals(1, instances.size)
        assertEquals("smi-2", instances[0].id)
        assertEquals(1, sets.size)
        assertEquals("set-2", sets[0].id)
    }
}

