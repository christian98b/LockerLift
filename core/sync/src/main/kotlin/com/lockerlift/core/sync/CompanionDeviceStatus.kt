package com.lockerlift.core.sync

import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class CompanionDeviceStatus(
    val isConnected: Boolean,
    val deviceName: String? = null,
    val nodeId: String? = null,
    val isNearby: Boolean = false,
    val hasRequiredCapability: Boolean = false
)

data class CompanionNodeInfo(
    val id: String,
    val displayName: String,
    val isNearby: Boolean
)

sealed class SyncResult {
    data class Success(val itemsSyncedCount: Int) : SyncResult()
    data class NoCompanionFound(val message: String = "No companion device connected") : SyncResult()
    data class Error(val message: String) : SyncResult()
}

object CompanionStatusResolver {

    fun resolve(
        connectedNodes: List<CompanionNodeInfo>,
        capabilityNodeIds: Set<String>
    ): CompanionDeviceStatus {
        if (connectedNodes.isEmpty()) {
            return CompanionDeviceStatus(isConnected = false)
        }
        val matchingNode = connectedNodes.firstOrNull { it.id in capabilityNodeIds }
            ?: connectedNodes.first()
        val hasCap = matchingNode.id in capabilityNodeIds
        return CompanionDeviceStatus(
            isConnected = true,
            deviceName = matchingNode.displayName,
            nodeId = matchingNode.id,
            isNearby = matchingNode.isNearby,
            hasRequiredCapability = hasCap
        )
    }

    fun findTargetNode(
        connectedNodes: List<CompanionNodeInfo>,
        capabilityNodeIds: Set<String>
    ): CompanionNodeInfo? {
        if (connectedNodes.isEmpty()) return null
        return connectedNodes.firstOrNull { it.id in capabilityNodeIds } ?: connectedNodes.first()
    }
}

object SyncUtils {

    fun formatSyncTimestamp(
        timestamp: Long,
        neverText: String,
        locale: Locale = Locale.getDefault()
    ): String {
        if (timestamp <= 0L) return neverText
        val date = Date(timestamp)
        val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        return format.format(date)
    }
}
