package com.lockerlift.core.sync

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CompanionStatusResolverTest {

    @Test
    fun testResolve_emptyNodesList_returnsDisconnected() {
        val status = CompanionStatusResolver.resolve(
            connectedNodes = emptyList(),
            capabilityNodeIds = setOf("node-1")
        )

        assertFalse(status.isConnected)
        assertNull(status.deviceName)
        assertNull(status.nodeId)
        assertFalse(status.isNearby)
        assertFalse(status.hasRequiredCapability)
    }

    @Test
    fun testResolve_matchingCapabilityNode_returnsConnectedWithCapability() {
        val nodes = listOf(
            CompanionNodeInfo(id = "phone-1", displayName = "Pixel 8 Pro", isNearby = true),
            CompanionNodeInfo(id = "other-2", displayName = "Tablet", isNearby = false)
        )
        val capNodes = setOf("phone-1")

        val status = CompanionStatusResolver.resolve(nodes, capNodes)

        assertTrue(status.isConnected)
        assertEquals("Pixel 8 Pro", status.deviceName)
        assertEquals("phone-1", status.nodeId)
        assertTrue(status.isNearby)
        assertTrue(status.hasRequiredCapability)
    }

    @Test
    fun testResolve_connectedNodeWithoutCapability_returnsConnectedWithoutCapability() {
        val nodes = listOf(
            CompanionNodeInfo(id = "watch-1", displayName = "Galaxy Watch 6", isNearby = true)
        )
        val capNodes = emptySet<String>()

        val status = CompanionStatusResolver.resolve(nodes, capNodes)

        assertTrue(status.isConnected)
        assertEquals("Galaxy Watch 6", status.deviceName)
        assertEquals("watch-1", status.nodeId)
        assertTrue(status.isNearby)
        assertFalse(status.hasRequiredCapability)
    }

    @Test
    fun testFindTargetNode_findsCapabilityNodeFirst() {
        val nodes = listOf(
            CompanionNodeInfo(id = "fallback-node", displayName = "Fallback", isNearby = false),
            CompanionNodeInfo(id = "target-node", displayName = "Preferred Watch", isNearby = true)
        )
        val capNodes = setOf("target-node")

        val target = CompanionStatusResolver.findTargetNode(nodes, capNodes)

        assertNotNull(target)
        assertEquals("target-node", target?.id)
        assertEquals("Preferred Watch", target?.displayName)
    }

    @Test
    fun testFindTargetNode_noMatchingCapability_fallsBackToFirstNode() {
        val nodes = listOf(
            CompanionNodeInfo(id = "first-node", displayName = "First", isNearby = true),
            CompanionNodeInfo(id = "second-node", displayName = "Second", isNearby = false)
        )
        val capNodes = setOf("non-existent-node")

        val target = CompanionStatusResolver.findTargetNode(nodes, capNodes)

        assertNotNull(target)
        assertEquals("first-node", target?.id)
    }

    @Test
    fun testFindTargetNode_emptyList_returnsNull() {
        val target = CompanionStatusResolver.findTargetNode(emptyList(), setOf("cap-1"))
        assertNull(target)
    }

    @Test
    fun testSyncUtilsFormatSyncTimestamp_zeroOrNegative_returnsNever() {
        assertEquals("Never", SyncUtils.formatSyncTimestamp(0L, "Never", Locale.US))
        assertEquals("Nie", SyncUtils.formatSyncTimestamp(-100L, "Nie", Locale.GERMAN))
    }

    @Test
    fun testSyncUtilsFormatSyncTimestamp_positiveTimestamp_formatsProperly() {
        val timestamp = 1710000000000L // 2024-03-09
        val formatted = SyncUtils.formatSyncTimestamp(timestamp, "Never", Locale.US)
        assertNotEquals("Never", formatted)
        assertTrue(formatted.isNotBlank())
    }

    @Test
    fun testSyncResultVariants() {
        val success = SyncResult.Success(3)
        assertEquals(3, success.itemsSyncedCount)

        val noCompanion = SyncResult.NoCompanionFound("Phone unreachable")
        assertEquals("Phone unreachable", noCompanion.message)

        val error = SyncResult.Error("I/O error")
        assertEquals("I/O error", error.message)
    }
}
