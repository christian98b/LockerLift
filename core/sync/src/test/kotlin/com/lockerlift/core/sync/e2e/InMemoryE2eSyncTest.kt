package com.lockerlift.core.sync.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.SessionMachineInstanceEntity
import com.lockerlift.core.database.entity.SyncQueueEntity
import com.lockerlift.core.database.entity.WorkoutSessionEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.entity.WorkoutTemplateEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.QueueStatus
import com.lockerlift.core.model.SetType
import com.lockerlift.core.model.SyncStatus
import com.lockerlift.core.model.WorkoutSession
import com.lockerlift.core.model.WorkoutTemplate
import com.lockerlift.core.sync.SessionMachineInstancePayload
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncIngestionEngine
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WorkoutSessionPayload
import com.lockerlift.core.sync.WorkoutTemplatePayload
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In-Memory End-to-End Synchronization Test Suite.
 *
 * Simulates complete bidirectional data replication between Phone and Wear OS:
 * - Room In-Memory Databases for Phone and Watch
 * - Master Data replication (Catalog & Templates)
 * - Bidirectional Workout Session Transfer
 * - Safe Machine Reconciliation & Deduplication (avoiding SQLite constraint collisions)
 * - ACK Handshake & Sync Queue Purge
 * - Double Progression historical data parity
 * - Zombie workout rejection
 *
 * NOTE: Skipped by default in standard builds. Explicitly enabled via -PrunE2eSync=true or RUN_E2E_SYNC=true.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InMemoryE2eSyncTest {

    private lateinit var phoneDb: LockerLiftDatabase
    private lateinit var watchDb: LockerLiftDatabase

    @Before
    fun checkExplicitE2eFlag() {
        val isExplicitlyEnabled = System.getProperty("runE2eSync") == "true" ||
                System.getenv("RUN_E2E_SYNC") == "true" ||
                System.getProperty("RUN_E2E_SYNC") == "true"

        Assume.assumeTrue(
            "In-Memory E2E Sync Tests are disabled by default. Enable explicitly with -PrunE2eSync=true or RUN_E2E_SYNC=true.",
            isExplicitlyEnabled
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        phoneDb = LockerLiftDatabase.createInMemory(context)
        watchDb = LockerLiftDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        if (::phoneDb.isInitialized) phoneDb.close()
        if (::watchDb.isInitialized) watchDb.close()
    }

    @Test
    fun testWorkoutSessionSync_fromWatchToPhone_persistsFullGraphAndAcknowledges() = runBlocking {
        // 1. Arrange: Watch creates an ad-hoc machine and tracks a finished workout session
        val machine = MachineEntity(
            id = "mach-watch-latzug",
            name = "Latzug Kabel",
            targetMuscleGroup = "Rücken",
            machineSettingsNote = "Stift 5, Griff eng",
            defaultIncrementKg = 2.5f,
            defaultCadence = "2-0-2-0",
            updatedAt = 1000L
        )
        watchDb.machineDao().insertMachine(machine)

        val sessionId = "session-watch-001"
        val sessionEntity = WorkoutSessionEntity(
            id = sessionId,
            templateId = null,
            startTime = 10000L,
            endTime = 13600L,
            rating = 5,
            notes = "Starkes Rückentraining auf der Watch",
            syncStatus = SyncStatus.PENDING
        )

        val instanceEntity = SessionMachineInstanceEntity(
            id = "inst-watch-1",
            sessionId = sessionId,
            machineId = machine.id,
            orderInSession = 0
        )

        val sets = listOf(
            WorkoutSetEntity(
                id = "set-w1",
                sessionMachineId = instanceEntity.id,
                setNumber = 1,
                weightKg = 50.0f,
                reps = 10,
                setType = SetType.NORMAL,
                completedAt = 11000L
            ),
            WorkoutSetEntity(
                id = "set-w2",
                sessionMachineId = instanceEntity.id,
                setNumber = 2,
                weightKg = 55.0f,
                reps = 8,
                setType = SetType.NORMAL,
                completedAt = 12000L
            ),
            WorkoutSetEntity(
                id = "set-w3",
                sessionMachineId = instanceEntity.id,
                setNumber = 3,
                weightKg = 55.0f,
                reps = 7,
                setType = SetType.NORMAL,
                completedAt = 13000L
            )
        )

        watchDb.workoutSessionDao().upsertFullSession(sessionEntity, listOf(instanceEntity), sets)

        // Simulate Watch adding session to sync queue
        val payload = WorkoutSessionPayload(
            session = sessionEntity.toDomainModel(),
            machineInstances = listOf(
                SessionMachineInstancePayload(
                    instance = instanceEntity.toDomainModel(),
                    machine = machine.toDomainModel(),
                    sets = sets.map { it.toDomainModel() }
                )
            ),
            templateName = null
        )
        val payloadJson = SyncPayloadSerializer.encodeSessionPayload(payload)
        watchDb.syncQueueDao().enqueue(
            SyncQueueEntity(
                sessionId = sessionId,
                payloadJson = payloadJson,
                status = QueueStatus.PENDING,
                attemptCount = 0,
                createdAt = 13601L
            )
        )

        // 2. Act (Transmit): Phone receives payload and ingests it via SyncIngestionEngine
        val result = SyncIngestionEngine.ingestWorkoutPayload(phoneDb, payloadJson, isMobile = true)
        assertTrue(result is SyncIngestionEngine.IngestionResult.Success)

        // 3. Assert (Phone DB): Complete graph correctly persisted with status SYNCED
        val phoneSessionWithDetails = phoneDb.workoutSessionDao().getSessionWithDetailsById(sessionId)
        assertNotNull("Phone should have ingested the session", phoneSessionWithDetails)
        assertEquals(SyncStatus.SYNCED, phoneSessionWithDetails!!.session.syncStatus)
        assertEquals(1, phoneSessionWithDetails.instances.size)

        val phoneInstance = phoneSessionWithDetails.instances.first()
        assertEquals("Latzug Kabel", phoneInstance.machine.name)
        assertEquals(3, phoneInstance.sets.size)
        assertEquals(50.0f, phoneInstance.sets[0].weightKg, 0.001f)
        assertEquals(10, phoneInstance.sets[0].reps)
        assertEquals(55.0f, phoneInstance.sets[1].weightKg, 0.001f)
        assertEquals(8, phoneInstance.sets[1].reps)

        // 4. Act (ACK Loopback): Phone dispatches ACK back to watch
        SyncIngestionEngine.handleWorkoutAck(watchDb, sessionId)

        // 5. Assert (Watch DB): Status marked SYNCED and queue item purged
        val watchSession = watchDb.workoutSessionDao().getSessionWithDetailsById(sessionId)
        assertNotNull(watchSession)
        assertEquals(SyncStatus.SYNCED, watchSession!!.session.syncStatus)

        val queueItem = watchDb.syncQueueDao().getQueueItemBySessionId(sessionId)
        assertNull("Queue item should be deleted after successful ACK", queueItem)
    }

    @Test
    fun testWorkoutSessionSync_fromPhoneToWatch_persistsFullGraphAndAcknowledges() = runBlocking {
        // 1. Arrange: Phone tracks workout session with 2 machines
        val m1 = MachineEntity(id = "m-phone-1", name = "Bankdrücken Langhantel", targetMuscleGroup = "Brust", defaultIncrementKg = 2.5f, updatedAt = 1L)
        val m2 = MachineEntity(id = "m-phone-2", name = "Schrägbankdrücken Kurzhantel", targetMuscleGroup = "Brust", defaultIncrementKg = 2.0f, updatedAt = 1L)
        phoneDb.machineDao().insertMachines(listOf(m1, m2))

        val sessionId = "session-phone-002"
        val session = WorkoutSessionEntity(id = sessionId, startTime = 20000L, endTime = 23000L, rating = 4, syncStatus = SyncStatus.PENDING)
        val inst1 = SessionMachineInstanceEntity(id = "inst-p1", sessionId = sessionId, machineId = m1.id, orderInSession = 0)
        val inst2 = SessionMachineInstanceEntity(id = "inst-p2", sessionId = sessionId, machineId = m2.id, orderInSession = 1)
        val setsM1 = listOf(
            WorkoutSetEntity(id = "s-p1", sessionMachineId = inst1.id, setNumber = 1, weightKg = 80.0f, reps = 8, setType = SetType.NORMAL),
            WorkoutSetEntity(id = "s-p2", sessionMachineId = inst1.id, setNumber = 2, weightKg = 80.0f, reps = 7, setType = SetType.NORMAL)
        )
        val setsM2 = listOf(
            WorkoutSetEntity(id = "s-p3", sessionMachineId = inst2.id, setNumber = 1, weightKg = 30.0f, reps = 10, setType = SetType.NORMAL)
        )

        phoneDb.workoutSessionDao().upsertFullSession(session, listOf(inst1, inst2), setsM1 + setsM2)

        val payload = WorkoutSessionPayload(
            session = session.toDomainModel(),
            machineInstances = listOf(
                SessionMachineInstancePayload(instance = inst1.toDomainModel(), machine = m1.toDomainModel(), sets = setsM1.map { it.toDomainModel() }),
                SessionMachineInstancePayload(instance = inst2.toDomainModel(), machine = m2.toDomainModel(), sets = setsM2.map { it.toDomainModel() })
            ),
            templateName = "Brust Hypertrophie"
        )
        val payloadJson = SyncPayloadSerializer.encodeSessionPayload(payload)
        phoneDb.syncQueueDao().enqueue(SyncQueueEntity(sessionId = sessionId, payloadJson = payloadJson, status = QueueStatus.PENDING, createdAt = 23001L))

        // 2. Act: Watch ingests payload
        val result = SyncIngestionEngine.ingestWorkoutPayload(watchDb, payloadJson, isMobile = false)
        assertTrue(result is SyncIngestionEngine.IngestionResult.Success)

        // 3. Assert (Watch DB): Machines inserted and session graph intact
        val watchSessionWithDetails = watchDb.workoutSessionDao().getSessionWithDetailsById(sessionId)
        assertNotNull(watchSessionWithDetails)
        assertEquals(SyncStatus.SYNCED, watchSessionWithDetails!!.session.syncStatus)
        assertEquals(2, watchSessionWithDetails.instances.size)

        // 4. Act (ACK loopback): Watch ACKs to phone
        SyncIngestionEngine.handleWorkoutAck(phoneDb, sessionId)

        // 5. Assert (Phone DB): Sync status updated and queue item purged
        val phoneSession = phoneDb.workoutSessionDao().getSessionWithDetailsById(sessionId)
        assertEquals(SyncStatus.SYNCED, phoneSession!!.session.syncStatus)
        assertNull(phoneDb.syncQueueDao().getQueueItemBySessionId(sessionId))
    }

    @Test
    fun testMasterDataSync_catalogAndTemplatesReplicatedToWatchPreservingOrder() = runBlocking {
        // 1. Arrange: Phone creates 3 machines and a workout template
        val m1 = Machine(id = "m-1", name = "Kniebeugen", targetMuscleGroup = "Beine")
        val m2 = Machine(id = "m-2", name = "Bankdrücken", targetMuscleGroup = "Brust")
        val m3 = Machine(id = "m-3", name = "Kreuzheben", targetMuscleGroup = "Rücken")
        val catalog = listOf(m1, m2, m3)

        val template = WorkoutTemplate(id = "tmpl-gk", name = "Ganzkörper Klassiker", description = "3 Grundübungen")
        val templatePayload = WorkoutTemplatePayload(
            template = template,
            machineIdsInOrder = listOf(m1.id, m2.id, m3.id)
        )

        val catalogJson = SyncPayloadSerializer.encodeMachines(catalog)
        val templatesJson = SyncPayloadSerializer.encodeTemplates(listOf(templatePayload))

        // 2. Act: Ingest into Watch DB in proper order (catalog first, then templates)
        val machineCount = SyncIngestionEngine.ingestEquipmentCatalog(watchDb, catalogJson)
        assertEquals(3, machineCount)

        val templateCount = SyncIngestionEngine.ingestWorkoutTemplates(watchDb, templatesJson)
        assertEquals(1, templateCount)

        // 3. Assert (Watch DB): Template and cross references preserve exact sort order
        val watchTemplateWithMachines = watchDb.workoutTemplateDao().getTemplateWithMachinesById(template.id)
        assertNotNull(watchTemplateWithMachines)
        assertEquals("Ganzkörper Klassiker", watchTemplateWithMachines!!.template.name)

        val orderedMachines = watchDb.workoutTemplateDao().getMachinesForTemplateOrdered(template.id)
        assertEquals(3, orderedMachines.size)
        assertEquals("Kniebeugen", orderedMachines[0].name)
        assertEquals("Bankdrücken", orderedMachines[1].name)
        assertEquals("Kreuzheben", orderedMachines[2].name)
    }

    @Test
    fun testMachineReconciliation_whenWatchAndPhoneHaveSameMachineNameWithDifferentIds_preventsConstraintCrashAndRemaps() = runBlocking {
        // 1. Arrange: Phone already has "Beinpresse 45°" with ID "phone-bp"
        val phoneMachine = MachineEntity(
            id = "phone-bp",
            name = "Beinpresse 45°",
            targetMuscleGroup = "Beine",
            defaultIncrementKg = 5.0f,
            updatedAt = 100L
        )
        phoneDb.machineDao().insertMachine(phoneMachine)

        // Watch created an ad-hoc machine with the same name but UUID "watch-bp"
        val watchMachine = Machine(
            id = "watch-bp",
            name = "Beinpresse 45°",
            targetMuscleGroup = "Beine",
            defaultIncrementKg = 5.0f
        )
        val session = WorkoutSession(id = "sess-reconcile", startTime = 1000L, endTime = 2000L)
        val instance = com.lockerlift.core.model.SessionMachineInstance(
            id = "inst-reconcile",
            sessionId = session.id,
            machineId = watchMachine.id,
            orderInSession = 0
        )
        val sets = listOf(
            com.lockerlift.core.model.WorkoutSet(
                id = "set-r1",
                sessionMachineId = instance.id,
                setNumber = 1,
                weightKg = 120.0f,
                reps = 12
            )
        )

        val payload = WorkoutSessionPayload(
            session = session,
            machineInstances = listOf(
                SessionMachineInstancePayload(instance = instance, machine = watchMachine, sets = sets)
            ),
            templateName = null
        )
        val payloadJson = SyncPayloadSerializer.encodeSessionPayload(payload)

        // 2. Act: Ingest into Phone DB
        val result = SyncIngestionEngine.ingestWorkoutPayload(phoneDb, payloadJson, isMobile = true)
        assertTrue(result is SyncIngestionEngine.IngestionResult.Success)

        // 3. Assert: Phone DB does NOT create a duplicate machine or throw SQLite unique constraint exception
        val allMachines = phoneDb.machineDao().getAllMachines()
        assertEquals(1, allMachines.size)
        assertEquals("phone-bp", allMachines.first().id)

        // Instance must be remapped to "phone-bp"
        val sessionWithDetails = phoneDb.workoutSessionDao().getSessionWithDetailsById(session.id)
        assertNotNull(sessionWithDetails)
        assertEquals("phone-bp", sessionWithDetails!!.instances.first().instance.machineId)
        assertEquals("phone-bp", sessionWithDetails.instances.first().machine.id)
    }

    @Test
    fun testHistoricalDataParity_forDoubleProgressionAfterSync() = runBlocking {
        // 1. Arrange: Workout on Watch with 2 completed sets for machine "m-dp"
        val machine = MachineEntity(id = "m-dp", name = "Schulterdrücken", targetMuscleGroup = "Schultern", updatedAt = 1L)
        watchDb.machineDao().insertMachine(machine)

        val session = WorkoutSessionEntity(id = "s-dp", startTime = 100L, endTime = 500L, syncStatus = SyncStatus.PENDING)
        val instance = SessionMachineInstanceEntity(id = "i-dp", sessionId = session.id, machineId = machine.id, orderInSession = 0)
        val sets = listOf(
            WorkoutSetEntity(id = "s1", sessionMachineId = instance.id, setNumber = 1, weightKg = 40.0f, reps = 12, completedAt = 200L),
            WorkoutSetEntity(id = "s2", sessionMachineId = instance.id, setNumber = 2, weightKg = 40.0f, reps = 12, completedAt = 300L)
        )
        watchDb.workoutSessionDao().upsertFullSession(session, listOf(instance), sets)

        val payload = WorkoutSessionPayload(
            session = session.toDomainModel(),
            machineInstances = listOf(
                SessionMachineInstancePayload(instance = instance.toDomainModel(), machine = machine.toDomainModel(), sets = sets.map { it.toDomainModel() })
            ),
            templateName = null
        )
        val payloadJson = SyncPayloadSerializer.encodeSessionPayload(payload)

        // 2. Act: Ingest into Phone
        SyncIngestionEngine.ingestWorkoutPayload(phoneDb, payloadJson, isMobile = true)

        // 3. Assert: Both devices retrieve identical past performance sets for Double Progression
        val watchHistoricalSets = watchDb.workoutSessionDao().getLastCompletedSetsForMachine(machine.id)
        val phoneHistoricalSets = phoneDb.workoutSessionDao().getLastCompletedSetsForMachine(machine.id)

        assertEquals(2, watchHistoricalSets.size)
        assertEquals(2, phoneHistoricalSets.size)
        assertEquals(watchHistoricalSets[0].weightKg, phoneHistoricalSets[0].weightKg, 0.001f)
        assertEquals(watchHistoricalSets[0].reps, phoneHistoricalSets[0].reps)
        assertEquals(watchHistoricalSets[1].weightKg, phoneHistoricalSets[1].weightKg, 0.001f)
        assertEquals(watchHistoricalSets[1].reps, phoneHistoricalSets[1].reps)
    }

    @Test
    fun testZombieWorkoutRejection_onMobileDoesNotResurrectDeletedSession() = runBlocking {
        // 1. Arrange: Session deleted on mobile, recorded in queue as ACTION_DELETE
        val deletedSessionId = "session-deleted-locally"
        phoneDb.syncQueueDao().enqueue(
            SyncQueueEntity(
                sessionId = deletedSessionId,
                payloadJson = SyncConstants.ACTION_DELETE,
                status = QueueStatus.PENDING,
                attemptCount = 0,
                createdAt = 1000L
            )
        )

        val machine = Machine(id = "m-zombie", name = "Dip Barren", targetMuscleGroup = "Trizeps")
        val payload = WorkoutSessionPayload(
            session = WorkoutSession(id = deletedSessionId, startTime = 100L, endTime = 200L),
            machineInstances = listOf(
                SessionMachineInstancePayload(
                    instance = com.lockerlift.core.model.SessionMachineInstance(id = "i-z", sessionId = deletedSessionId, machineId = machine.id, orderInSession = 0),
                    machine = machine,
                    sets = emptyList()
                )
            ),
            templateName = null
        )
        val payloadJson = SyncPayloadSerializer.encodeSessionPayload(payload)

        // 2. Act: Incoming payload from watch arrives on phone
        val result = SyncIngestionEngine.ingestWorkoutPayload(phoneDb, payloadJson, isMobile = true)

        // 3. Assert: Rejected as Zombie, session is NOT resurrected in Phone DB
        assertTrue("Incoming payload for locally deleted session must be rejected", result is SyncIngestionEngine.IngestionResult.RejectedZombie)
        assertNull("Session must not be created in Phone DB", phoneDb.workoutSessionDao().getSessionWithDetailsById(deletedSessionId))
    }

    @Test
    fun testWorkoutDeletionSync_removesSessionAndQueueAcrossDevices() = runBlocking {
        // 1. Arrange: Session exists on Watch
        val machine = MachineEntity(id = "m-del", name = "Wadenheben", targetMuscleGroup = "Waden", updatedAt = 1L)
        watchDb.machineDao().insertMachine(machine)

        val sessionId = "session-to-delete"
        val session = WorkoutSessionEntity(id = sessionId, startTime = 100L, endTime = 200L, syncStatus = SyncStatus.SYNCED)
        watchDb.workoutSessionDao().insertSession(session)
        watchDb.syncQueueDao().enqueue(SyncQueueEntity(sessionId = sessionId, payloadJson = "{}", status = QueueStatus.IN_TRANSIT, createdAt = 1L))

        // 2. Act: Delete instruction arrives on Watch
        SyncIngestionEngine.handleWorkoutDelete(watchDb, sessionId)

        // 3. Assert: Session and queue item deleted
        assertNull(watchDb.workoutSessionDao().getSessionWithDetailsById(sessionId))
        assertNull(watchDb.syncQueueDao().getQueueItemBySessionId(sessionId))
    }
}
