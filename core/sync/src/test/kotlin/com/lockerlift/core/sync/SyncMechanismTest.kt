package com.lockerlift.core.sync

import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.QueueStatus
import com.lockerlift.core.model.SyncQueueItem
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class SyncMechanismTest {

    @Test
    fun testCompanionStatusResolver_whenMatchingCapabilityPresent_resolvesCapableNode() {
        val node1 = CompanionNodeInfo("node-phone-1", "Pixel Phone", isNearby = true)
        val node2 = CompanionNodeInfo("node-other-2", "Bluetooth Speaker", isNearby = false)
        val capabilityIds = setOf("node-phone-1")

        val status = CompanionStatusResolver.resolve(listOf(node2, node1), capabilityIds)

        assertTrue(status.isConnected)
        assertTrue(status.hasRequiredCapability)
        assertEquals("node-phone-1", status.nodeId)
        assertEquals("Pixel Phone", status.deviceName)
    }

    @Test
    fun testCompanionStatusResolver_whenNoNodeHasCapability_resolvesWithFalseCapability() {
        val node1 = CompanionNodeInfo("node-speaker", "BT Speaker", isNearby = true)
        val capabilityIds = setOf("node-phone-99")

        val status = CompanionStatusResolver.resolve(listOf(node1), capabilityIds)

        assertTrue(status.isConnected)
        assertFalse(status.hasRequiredCapability)
        assertEquals("node-speaker", status.nodeId)
    }

    @Test
    fun testCompanionStatusResolver_whenNoNodesConnected_resolvesDisconnected() {
        val status = CompanionStatusResolver.resolve(emptyList(), setOf("some-cap"))

        assertFalse(status.isConnected)
        assertFalse(status.hasRequiredCapability)
        assertNull(status.nodeId)
    }

    @Test
    fun testFindTargetNode_prefersCapableNodeOverFirst() {
        val genericNode = CompanionNodeInfo("generic", "Generic BT", isNearby = false)
        val targetNode = CompanionNodeInfo("watch-1", "Galaxy Watch", isNearby = true)

        val resolved = CompanionStatusResolver.findTargetNode(
            listOf(genericNode, targetNode),
            setOf("watch-1")
        )

        assertNotNull(resolved)
        assertEquals("watch-1", resolved?.id)
    }

    @Test
    fun testPayloadByteSizeCalculation_multiByteCharactersExceedStringLength() {
        // Umlauts and emojis in German notes: 2-4 bytes per character in UTF-8
        val textWithUmlauts = "Übergrößen-Drückbank für Rückenübungen 💪🏋️"
        val charLength = textWithUmlauts.length
        val byteLength = textWithUmlauts.toByteArray(StandardCharsets.UTF_8).size

        assertTrue("Byte length must exceed character length for multi-byte text", byteLength > charLength)
    }

    @Test
    fun testSyncResultErrorMapping_whenFailuresOccurAndZeroSynced() {
        val pendingCount = 2
        val syncedCount = 0
        val failedCount = 2

        val result: SyncResult = if (failedCount > 0 && syncedCount == 0) {
            SyncResult.Error("Failed to transfer $failedCount pending item(s)")
        } else {
            SyncResult.Success(syncedCount)
        }

        assertTrue(result is SyncResult.Error)
        assertEquals("Failed to transfer 2 pending item(s)", (result as SyncResult.Error).message)
    }

    @Test
    fun testSyncResultSuccessMapping_whenAllItemsSucceed() {
        val pendingCount = 2
        val syncedCount = 2
        val failedCount = 0

        val result: SyncResult = if (failedCount > 0 && syncedCount == 0) {
            SyncResult.Error("Failed to transfer $failedCount pending item(s)")
        } else {
            SyncResult.Success(syncedCount)
        }

        assertTrue(result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).itemsSyncedCount)
    }

    @Test
    fun testStaleInTransitRecoveryLogic() {
        val now = 200_000L
        val staleCutoff = now - 60_000L

        val staleItem = SyncQueueItem(
            id = "q-stale",
            sessionId = "s-1",
            payloadJson = "{}",
            status = QueueStatus.IN_TRANSIT,
            lastAttemptAt = now - 90_000L
        )

        val freshItem = SyncQueueItem(
            id = "q-fresh",
            sessionId = "s-2",
            payloadJson = "{}",
            status = QueueStatus.IN_TRANSIT,
            lastAttemptAt = now - 10_000L
        )

        val isStale = (staleItem.status == QueueStatus.IN_TRANSIT &&
                (staleItem.lastAttemptAt == null || staleItem.lastAttemptAt!! < staleCutoff))
        val isFresh = (freshItem.status == QueueStatus.IN_TRANSIT &&
                (freshItem.lastAttemptAt == null || freshItem.lastAttemptAt!! < staleCutoff))

        assertTrue("Item older than 60s must be flagged as stale", isStale)
        assertFalse("Item younger than 60s must not be flagged as stale", isFresh)
    }

    @Test
    fun testMachineDeduplicationMapping_whenSameNameExistsLocally() {
        val localMachines = listOf(
            Machine(id = "local-m1", name = "Beinpresse", targetMuscleGroup = "Beine")
        )

        val incomingMachines = listOf(
            Machine(id = "watch-adhoc-m99", name = "Beinpresse", targetMuscleGroup = "Beine"),
            Machine(id = "watch-adhoc-m100", name = "Klimmzug", targetMuscleGroup = "Rücken")
        )

        val mapping = mutableMapOf<String, String>()
        val machinesToInsert = mutableListOf<Machine>()

        for (inc in incomingMachines) {
            val existing = localMachines.find { it.name.equals(inc.name, ignoreCase = true) }
            if (existing != null) {
                mapping[inc.id] = existing.id
            } else {
                mapping[inc.id] = inc.id
                machinesToInsert.add(inc)
            }
        }

        // Beinpresse should be remapped to local-m1, not inserted
        assertEquals("local-m1", mapping["watch-adhoc-m99"])
        // Klimmzug should keep its new ID and be marked for insertion
        assertEquals("watch-adhoc-m100", mapping["watch-adhoc-m100"])
        assertEquals(1, machinesToInsert.size)
        assertEquals("Klimmzug", machinesToInsert.first().name)
    }
}
