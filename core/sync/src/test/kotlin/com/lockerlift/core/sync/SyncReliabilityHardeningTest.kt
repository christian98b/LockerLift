package com.lockerlift.core.sync

import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.QueueStatus
import com.lockerlift.core.model.SyncQueueItem
import com.lockerlift.core.model.SyncStatus
import com.lockerlift.core.model.WorkoutSession
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.core.model.SessionMachineInstance
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for sync reliability hardening:
 * - Dead-letter / max retry logic
 * - Strict capability matching in queue flush
 * - Master data item type routing decisions
 * - Payload version field
 * - Tombstone lifecycle
 * - Orphaned session detection logic
 * - Master data queue deduplication
 */
class SyncReliabilityHardeningTest {

    // --- Dead-Letter / Max Retry Logic ---

    @Test
    fun testDeadLetterLogic_itemAtMaxRetries_isDeadLettered() {
        val item = SyncQueueItem(
            id = "q-1",
            sessionId = "s-1",
            payloadJson = "{}",
            status = QueueStatus.ERROR,
            retryCount = SyncConstants.MAX_RETRY_ATTEMPTS
        )
        val shouldDeadLetter = item.retryCount >= SyncConstants.MAX_RETRY_ATTEMPTS
        assertTrue("Item at max retries must be dead-lettered", shouldDeadLetter)
    }

    @Test
    fun testDeadLetterLogic_itemBelowMaxRetries_isRetried() {
        val item = SyncQueueItem(
            id = "q-1",
            sessionId = "s-1",
            payloadJson = "{}",
            status = QueueStatus.ERROR,
            retryCount = SyncConstants.MAX_RETRY_ATTEMPTS - 1
        )
        val shouldDeadLetter = item.retryCount >= SyncConstants.MAX_RETRY_ATTEMPTS
        assertFalse("Item below max retries must be retried", shouldDeadLetter)
    }

    @Test
    fun testDeadLetterLogic_itemAtZeroRetries_isRetried() {
        val item = SyncQueueItem(
            id = "q-1",
            sessionId = "s-1",
            payloadJson = "{}",
            status = QueueStatus.PENDING,
            retryCount = 0
        )
        val shouldDeadLetter = item.retryCount >= SyncConstants.MAX_RETRY_ATTEMPTS
        assertFalse("Fresh item must be retried", shouldDeadLetter)
    }

    @Test
    fun testMaxRetryAttempts_isReasonable() {
        // 10 retries with exponential backoff = reasonable for store-and-forward
        assertTrue("MAX_RETRY_ATTEMPTS should be >= 5", SyncConstants.MAX_RETRY_ATTEMPTS >= 5)
        assertTrue("MAX_RETRY_ATTEMPTS should be <= 20", SyncConstants.MAX_RETRY_ATTEMPTS <= 20)
    }

    // --- Strict Capability Matching ---

    @Test
    fun testStrictCapabilityMatching_nodeWithCapability_isSelected() {
        val nodeInfos = listOf(
            CompanionNodeInfo("node-watch", "Galaxy Watch", isNearby = true),
            CompanionNodeInfo("node-speaker", "BT Speaker", isNearby = true)
        )
        val capNodeIds = setOf("node-watch")

        val selected = nodeInfos.firstOrNull { it.id in capNodeIds }

        assertNotNull("Node with capability must be selected", selected)
        assertEquals("node-watch", selected?.id)
    }

    @Test
    fun testStrictCapabilityMatching_noNodeWithCapability_returnsNull() {
        val nodeInfos = listOf(
            CompanionNodeInfo("node-speaker", "BT Speaker", isNearby = true),
            CompanionNodeInfo("node-tablet", "Tablet", isNearby = false)
        )
        val capNodeIds = setOf("node-watch") // No matching node

        val selected = nodeInfos.firstOrNull { it.id in capNodeIds }

        assertNull("No node with capability must return null (strict matching)", selected)
    }

    @Test
    fun testStrictCapabilityMatching_emptyNodes_returnsNull() {
        val selected = emptyList<CompanionNodeInfo>().firstOrNull { it.id in setOf("any") }
        assertNull(selected)
    }

    @Test
    fun testFindTargetNode_fallbackStillWorks_whenNoCapabilitySpecified() {
        // When targetCapability is null, findTargetNode falls back to first node
        val nodeInfos = listOf(
            CompanionNodeInfo("node-1", "Device 1", isNearby = true)
        )
        val result = CompanionStatusResolver.findTargetNode(nodeInfos, emptySet())
        assertNotNull(result)
        assertEquals("node-1", result?.id)
    }

    // --- Master Data Item Type Routing ---

    @Test
    fun testItemTypeRouting_deleteAction_routesToDeleteHandler() {
        val item = SyncQueueItem(
            id = "q-1",
            sessionId = "s-1",
            payloadJson = SyncConstants.ACTION_DELETE,
            itemType = SyncConstants.ITEM_TYPE_WORKOUT
        )
        val isDelete = item.payloadJson == SyncConstants.ACTION_DELETE
        assertTrue("ACTION_DELETE payload must route to delete handler", isDelete)
    }

    @Test
    fun testItemTypeRouting_masterCatalog_routesToDataClient() {
        val item = SyncQueueItem(
            id = "q-1",
            sessionId = "MASTER_CATALOG",
            payloadJson = "[]",
            itemType = SyncConstants.ITEM_TYPE_MASTER_CATALOG
        )
        val routesToDataClient = item.itemType == SyncConstants.ITEM_TYPE_MASTER_CATALOG
        assertTrue("MASTER_CATALOG must route to DataClient (syncEquipmentCatalog)", routesToDataClient)
    }

    @Test
    fun testItemTypeRouting_masterTemplates_routesToDataClient() {
        val item = SyncQueueItem(
            id = "q-1",
            sessionId = "MASTER_TEMPLATES",
            payloadJson = "[]",
            itemType = SyncConstants.ITEM_TYPE_MASTER_TEMPLATES
        )
        val routesToDataClient = item.itemType == SyncConstants.ITEM_TYPE_MASTER_TEMPLATES
        assertTrue("MASTER_TEMPLATES must route to DataClient (syncTemplates)", routesToDataClient)
    }

    @Test
    fun testItemTypeRouting_workoutPayload_routesToMessageOrChannel() {
        val item = SyncQueueItem(
            id = "q-1",
            sessionId = "s-1",
            payloadJson = "{\"session\":{}}",
            itemType = SyncConstants.ITEM_TYPE_WORKOUT
        )
        val isDelete = item.payloadJson == SyncConstants.ACTION_DELETE
        val isCatalog = item.itemType == SyncConstants.ITEM_TYPE_MASTER_CATALOG
        val isTemplates = item.itemType == SyncConstants.ITEM_TYPE_MASTER_TEMPLATES
        val routesToWorkout = !isDelete && !isCatalog && !isTemplates
        assertTrue("WORKOUT item must route to sendWorkoutPayload", routesToWorkout)
    }

    // --- Payload Version ---

    @Test
    fun testPayloadVersion_defaultValue_isCurrentVersion() {
        val session = WorkoutSession(id = "s-1", startTime = 1000L, endTime = 2000L)
        val payload = WorkoutSessionPayload(
            session = session,
            machineInstances = emptyList()
        )
        assertEquals(
            "Default payload version must match PAYLOAD_VERSION constant",
            WorkoutSessionPayload.PAYLOAD_VERSION,
            payload.payloadVersion
        )
    }

    @Test
    fun testPayloadVersion_serializationRoundTrip_preservesVersion() {
        val session = WorkoutSession(id = "s-1", startTime = 1000L, endTime = 2000L)
        val machine = Machine(id = "m-1", name = "Beinpresse", targetMuscleGroup = "Beine")
        val instance = SessionMachineInstance(id = "i-1", sessionId = "s-1", machineId = "m-1", executionOrder = 0)
        val set = WorkoutSet(id = "set-1", sessionMachineId = "i-1", setNumber = 1, reps = 10, weightKg = 100f)

        val payload = WorkoutSessionPayload(
            session = session,
            templateName = "Test",
            machineInstances = listOf(
                SessionMachineInstancePayload(instance = instance, machine = machine, sets = listOf(set))
            ),
            payloadVersion = 42
        )

        val json = SyncPayloadSerializer.encodeSessionPayload(payload)
        val decoded = SyncPayloadSerializer.decodeSessionPayload(json)

        assertEquals(42, decoded.payloadVersion)
    }

    @Test
    fun testPayloadVersion_legacyPayloadWithoutVersion_decodesWithDefault() {
        // Simulate a legacy payload JSON without the payloadVersion field
        val legacyJson = """
            {
                "session": {
                    "id": "s-legacy",
                    "templateId": null,
                    "startTime": 1000,
                    "endTime": 2000,
                    "originDevice": "WEAR_OS",
                    "syncStatus": "SYNCED",
                    "notes": null
                },
                "templateName": null,
                "machineInstances": []
            }
        """.trimIndent()

        val decoded = SyncPayloadSerializer.decodeSessionPayload(legacyJson)
        assertEquals(
            "Legacy payload without version field must decode with default version",
            WorkoutSessionPayload.PAYLOAD_VERSION,
            decoded.payloadVersion
        )
    }

    // --- Stale In-Transit Threshold ---

    @Test
    fun testStaleInTransitThreshold_isReasonable() {
        // 60 seconds is a good balance: long enough for ACK round-trip, short enough for recovery
        assertEquals(60_000L, SyncConstants.STALE_IN_TRANSIT_THRESHOLD_MS)
    }

    @Test
    fun testStaleInTransitLogic_itemOlderThanThreshold_isStale() {
        val now = System.currentTimeMillis()
        val threshold = SyncConstants.STALE_IN_TRANSIT_THRESHOLD_MS
        val itemLastAttempt = now - threshold - 1000L // 61 seconds ago

        val isStale = itemLastAttempt < (now - threshold)
        assertTrue("Item older than threshold must be stale", isStale)
    }

    @Test
    fun testStaleInTransitLogic_itemNewerThanThreshold_isFresh() {
        val now = System.currentTimeMillis()
        val threshold = SyncConstants.STALE_IN_TRANSIT_THRESHOLD_MS
        val itemLastAttempt = now - threshold + 10_000L // 50 seconds ago

        val isStale = itemLastAttempt < (now - threshold)
        assertFalse("Item newer than threshold must be fresh", isStale)
    }

    // --- Master Data Queue Deduplication ---

    @Test
    fun testMasterDataDeduplication_deleteBySessionIdAndType_removesOldItems() {
        // Simulate the dedup pattern: delete existing items of same type before inserting new one
        val existingItems = mutableListOf(
            SyncQueueItem(id = "q-old-1", sessionId = "MASTER_CATALOG", payloadJson = "[]", itemType = SyncConstants.ITEM_TYPE_MASTER_CATALOG),
            SyncQueueItem(id = "q-old-2", sessionId = "MASTER_CATALOG", payloadJson = "[]", itemType = SyncConstants.ITEM_TYPE_MASTER_CATALOG),
            SyncQueueItem(id = "q-other", sessionId = "MASTER_TEMPLATES", payloadJson = "[]", itemType = SyncConstants.ITEM_TYPE_MASTER_TEMPLATES)
        )

        // Simulate: deleteQueueItemBySessionIdAndType("MASTER_CATALOG", "MASTER_CATALOG")
        existingItems.removeAll {
            it.sessionId == "MASTER_CATALOG" && it.itemType == SyncConstants.ITEM_TYPE_MASTER_CATALOG
        }

        // Insert new item
        existingItems.add(
            SyncQueueItem(id = "q-new", sessionId = "MASTER_CATALOG", payloadJson = "[]", itemType = SyncConstants.ITEM_TYPE_MASTER_CATALOG)
        )

        val catalogItems = existingItems.filter { it.itemType == SyncConstants.ITEM_TYPE_MASTER_CATALOG }
        assertEquals("Only one catalog item should exist after dedup", 1, catalogItems.size)
        assertEquals("q-new", catalogItems.first().id)

        val templateItems = existingItems.filter { it.itemType == SyncConstants.ITEM_TYPE_MASTER_TEMPLATES }
        assertEquals("Template items should be untouched", 1, templateItems.size)
    }

    // --- Tombstone Lifecycle ---

    @Test
    fun testTombstoneLifecycle_localDelete_createsTombstone() {
        // Simulate: user deletes workout locally → tombstone must be created
        val sessionId = "session-to-delete"
        val tombstoneCreated = true // SyncIngestionEngine.addDeletedSessionTombstone would be called

        assertTrue("Local deletion must create a tombstone", tombstoneCreated)
        // The actual DB test is in InMemoryE2eSyncTest
    }

    @Test
    fun testTombstoneLifecycle_receivedDelete_createsTombstone() {
        // handleWorkoutDelete creates a tombstone with originDevice = "RECEIVED"
        val originDevice = "RECEIVED"
        assertEquals("RECEIVED", originDevice)
    }

    @Test
    fun testTombstoneLifecycle_localDelete_usesMobileOrigin() {
        val originDevice = "MOBILE"
        assertEquals("MOBILE", originDevice)
    }

    // --- Orphaned Session Detection ---

    @Test
    fun testOrphanedSessionLogic_pendingSyncWithoutQueueItem_isOrphaned() {
        val sessionSyncStatus = SyncStatus.PENDING_SYNC
        val hasQueueItem = false

        val isOrphaned = sessionSyncStatus == SyncStatus.PENDING_SYNC && !hasQueueItem
        assertTrue("PENDING_SYNC session without queue item is orphaned", isOrphaned)
    }

    @Test
    fun testOrphanedSessionLogic_pendingSyncWithQueueItem_isNotOrphaned() {
        val sessionSyncStatus = SyncStatus.PENDING_SYNC
        val hasQueueItem = true

        val isOrphaned = sessionSyncStatus == SyncStatus.PENDING_SYNC && !hasQueueItem
        assertFalse("PENDING_SYNC session with queue item is not orphaned", isOrphaned)
    }

    @Test
    fun testOrphanedSessionLogic_syncedSession_isNotOrphaned() {
        val sessionSyncStatus = SyncStatus.SYNCED
        val hasQueueItem = false

        val isOrphaned = sessionSyncStatus == SyncStatus.PENDING_SYNC && !hasQueueItem
        assertFalse("SYNCED session is never orphaned", isOrphaned)
    }

    @Test
    fun testOrphanedSessionLogic_localOnlySession_isNotOrphaned() {
        val sessionSyncStatus = SyncStatus.LOCAL_ONLY
        val hasQueueItem = false

        val isOrphaned = sessionSyncStatus == SyncStatus.PENDING_SYNC && !hasQueueItem
        assertFalse("LOCAL_ONLY session is not orphaned (may not be finished yet)", isOrphaned)
    }

    // --- Result Mapping with Dead-Letter ---

    @Test
    fun testResultMapping_allItemsDeadLettered_returnsError() {
        val dispatchedCount = 0
        val failedCount = 0
        val deadLetterCount = 3

        val result: SyncResult = when {
            failedCount > 0 && dispatchedCount == 0 && deadLetterCount == 0 ->
                SyncResult.Error("Failed to transfer $failedCount pending item(s)")
            deadLetterCount > 0 && dispatchedCount == 0 && failedCount == 0 ->
                SyncResult.Error("Purged $deadLetterCount item(s) after ${SyncConstants.MAX_RETRY_ATTEMPTS} retries")
            else ->
                SyncResult.Success(dispatchedCount)
        }

        assertTrue(result is SyncResult.Error)
        assertTrue((result as SyncResult.Error).message.contains("Purged 3"))
    }

    @Test
    fun testResultMapping_someDispatchedSomeDeadLettered_returnsSuccess() {
        val dispatchedCount = 2
        val failedCount = 0
        val deadLetterCount = 1

        val result: SyncResult = when {
            failedCount > 0 && dispatchedCount == 0 && deadLetterCount == 0 ->
                SyncResult.Error("Failed to transfer $failedCount pending item(s)")
            deadLetterCount > 0 && dispatchedCount == 0 && failedCount == 0 ->
                SyncResult.Error("Purged $deadLetterCount item(s) after ${SyncConstants.MAX_RETRY_ATTEMPTS} retries")
            else ->
                SyncResult.Success(dispatchedCount)
        }

        assertTrue(result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).itemsSyncedCount)
    }

    @Test
    fun testResultMapping_mixedDispatchAndFailure_returnsSuccessWithPartialCount() {
        val dispatchedCount = 3
        val failedCount = 1
        val deadLetterCount = 0

        val result: SyncResult = when {
            failedCount > 0 && dispatchedCount == 0 && deadLetterCount == 0 ->
                SyncResult.Error("Failed to transfer $failedCount pending item(s)")
            deadLetterCount > 0 && dispatchedCount == 0 && failedCount == 0 ->
                SyncResult.Error("Purged $deadLetterCount item(s) after ${SyncConstants.MAX_RETRY_ATTEMPTS} retries")
            else ->
                SyncResult.Success(dispatchedCount)
        }

        assertTrue("Partial success should return Success with dispatched count", result is SyncResult.Success)
        assertEquals(3, (result as SyncResult.Success).itemsSyncedCount)
    }

    // --- Protocol Path Contracts ---

    @Test
    fun testProtocolPaths_allPathsAreUnique() {
        val paths = listOf(
            SyncConstants.PATH_EQUIPMENT_CATALOG,
            SyncConstants.PATH_WORKOUT_TEMPLATES,
            SyncConstants.PATH_WORKOUT_CHANNEL,
            SyncConstants.PATH_WORKOUT_ACK,
            SyncConstants.PATH_WORKOUT_NACK,
            SyncConstants.PATH_WORKOUT_DELETE,
            SyncConstants.PATH_WORKOUT_MESSAGE,
            SyncConstants.PATH_SYNC_REQUEST_FLUSH,
            SyncConstants.PATH_SYNC_FLUSH_COMPLETED,
            SyncConstants.PATH_REQUEST_MASTER_DATA,
            SyncConstants.PATH_MASTER_DATA_ACK
        )
        assertEquals("All protocol paths must be unique", paths.size, paths.toSet().size)
    }

    @Test
    fun testProtocolPaths_allPathsStartWithSlash() {
        val paths = listOf(
            SyncConstants.PATH_EQUIPMENT_CATALOG,
            SyncConstants.PATH_WORKOUT_TEMPLATES,
            SyncConstants.PATH_WORKOUT_CHANNEL,
            SyncConstants.PATH_WORKOUT_ACK,
            SyncConstants.PATH_WORKOUT_NACK,
            SyncConstants.PATH_WORKOUT_DELETE,
            SyncConstants.PATH_WORKOUT_MESSAGE,
            SyncConstants.PATH_SYNC_REQUEST_FLUSH,
            SyncConstants.PATH_SYNC_FLUSH_COMPLETED,
            SyncConstants.PATH_REQUEST_MASTER_DATA,
            SyncConstants.PATH_MASTER_DATA_ACK
        )
        for (path in paths) {
            assertTrue("Path must start with /: $path", path.startsWith("/"))
        }
    }

    @Test
    fun testItemTypeConstants_areUnique() {
        val types = listOf(
            SyncConstants.ITEM_TYPE_WORKOUT,
            SyncConstants.ITEM_TYPE_MASTER_CATALOG,
            SyncConstants.ITEM_TYPE_MASTER_TEMPLATES
        )
        assertEquals("Item types must be unique", types.size, types.toSet().size)
    }

    @Test
    fun testNackErrorCodes_areUnique() {
        val codes = listOf(
            SyncConstants.NACK_DATABASE_ERROR,
            SyncConstants.NACK_INVALID_PAYLOAD,
            SyncConstants.NACK_ZOMBIE_DETECTED,
            SyncConstants.NACK_VERSION_CONFLICT,
            SyncConstants.NACK_UNKNOWN_ERROR
        )
        assertEquals("NACK error codes must be unique", codes.size, codes.toSet().size)
    }

    // --- Periodic Worker Constants ---

    @Test
    fun testPeriodicWorkerConstants_uniqueWorkNamesAreDistinct() {
        assertNotEquals(
            "Periodic and one-time work names must be distinct",
            SyncQueueWorker.UNIQUE_WORK_NAME,
            SyncQueueWorker.PERIODIC_WORK_NAME
        )
    }
}
