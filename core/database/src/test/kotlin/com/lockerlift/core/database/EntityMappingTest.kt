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
}
