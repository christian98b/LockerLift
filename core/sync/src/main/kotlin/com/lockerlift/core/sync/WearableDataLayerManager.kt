package com.lockerlift.core.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.lockerlift.core.database.dao.SyncQueueDao
import com.lockerlift.core.model.QueueStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class WearableDataLayerManager(private val context: Context) : SyncFlushRequester {

    private val dataClient = Wearable.getDataClient(context)
    private val channelClient = Wearable.getChannelClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun getConnectedNodes(): List<Node> {
        return runCatching {
            nodeClient.connectedNodes.await()
        }.getOrDefault(emptyList())
    }

    suspend fun getCompanionStatus(requiredCapability: String): CompanionDeviceStatus {
        return runCatching {
            val capabilityClient = Wearable.getCapabilityClient(context)
            val capabilityInfo = capabilityClient
                .getCapability(requiredCapability, CapabilityClient.FILTER_ALL)
                .await()
            val capNodeIds = capabilityInfo.nodes.map { it.id }.toSet()

            val nodes = nodeClient.connectedNodes.await()
            val nodeInfos = nodes.map { CompanionNodeInfo(it.id, it.displayName, it.isNearby) }

            CompanionStatusResolver.resolve(nodeInfos, capNodeIds)
        }.getOrDefault(CompanionDeviceStatus(isConnected = false))
    }

    suspend fun getWearCompanionStatus(): CompanionDeviceStatus =
        getCompanionStatus(SyncConstants.CAPABILITY_WEAR)

    suspend fun getMobileCompanionStatus(): CompanionDeviceStatus =
        getCompanionStatus(SyncConstants.CAPABILITY_MOBILE)

    override suspend fun isWearCompanionConnected(): Boolean =
        getWearCompanionStatus().isConnected

    /**
     * Sends a master data pull request to the phone companion.
     * The phone will respond by pushing the full equipment catalog and workout templates.
     */
    suspend fun requestMasterDataFromPhone(): Boolean {
        return runCatching {
            val connectedNodes = getConnectedNodes()
            if (connectedNodes.isEmpty()) return false

            val capInfo = runCatching {
                Wearable.getCapabilityClient(context)
                    .getCapability(SyncConstants.CAPABILITY_MOBILE, CapabilityClient.FILTER_ALL)
                    .await()
            }.getOrNull()

            val targetNodes = if (capInfo != null && capInfo.nodes.isNotEmpty()) {
                capInfo.nodes.toList()
            } else {
                connectedNodes
            }

            var anySent = false
            for (node in targetNodes) {
                val sent = runCatching {
                    messageClient.sendMessage(
                        node.id,
                        SyncConstants.PATH_REQUEST_MASTER_DATA,
                        ByteArray(0)
                    ).await()
                    true
                }.getOrDefault(false)
                if (sent) anySent = true
            }
            anySent
        }.getOrDefault(false)
    }

    override suspend fun requestWatchSyncFlush(): Boolean {
        return runCatching {
            val connectedNodes = getConnectedNodes()
            if (connectedNodes.isEmpty()) return false

            val capInfo = runCatching {
                Wearable.getCapabilityClient(context)
                    .getCapability(SyncConstants.CAPABILITY_WEAR, CapabilityClient.FILTER_ALL)
                    .await()
            }.getOrNull()

            val targetNodes = if (capInfo != null && capInfo.nodes.isNotEmpty()) {
                capInfo.nodes.toList()
            } else {
                connectedNodes
            }

            var anySent = false
            for (node in targetNodes) {
                val sent = runCatching {
                    messageClient.sendMessage(
                        node.id,
                        SyncConstants.PATH_SYNC_REQUEST_FLUSH,
                        ByteArray(0)
                    ).await()
                    true
                }.getOrDefault(false)
                if (sent) anySent = true
            }
            anySent
        }.getOrDefault(false)
    }

    suspend fun sendSyncFlushCompleted(nodeId: String, count: Int): Boolean {
        return runCatching {
            messageClient.sendMessage(
                nodeId,
                SyncConstants.PATH_SYNC_FLUSH_COMPLETED,
                count.toString().toByteArray(StandardCharsets.UTF_8)
            ).await()
            true
        }.getOrDefault(false)
    }

    fun getLastSyncTimestamp(): Long {
        return runCatching {
            val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
            prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        }.getOrDefault(0L)
    }

    fun updateLastSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).apply()
        }
    }

    suspend fun syncEquipmentCatalog(catalogJson: String): Boolean {
        return runCatching {
            val request = PutDataMapRequest.create(SyncConstants.PATH_EQUIPMENT_CATALOG).apply {
                dataMap.putString("payload", catalogJson)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
            updateLastSyncTimestamp()
            true
        }.getOrDefault(false)
    }

    suspend fun syncTemplates(templatesJson: String): Boolean {
        return runCatching {
            val request = PutDataMapRequest.create(SyncConstants.PATH_WORKOUT_TEMPLATES).apply {
                dataMap.putString("payload", templatesJson)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
            updateLastSyncTimestamp()
            true
        }.getOrDefault(false)
    }

    suspend fun sendWorkoutPayload(nodeId: String, payloadJson: String): Boolean {
        val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)
        // Fast atomic message transfer for payloads within MessageClient capacity (under 60 KB)
        if (payloadBytes.size < 60_000) {
            val messageSent = runCatching {
                messageClient.sendMessage(
                    nodeId,
                    SyncConstants.PATH_WORKOUT_MESSAGE,
                    payloadBytes
                ).await()
                updateLastSyncTimestamp()
                true
            }.getOrDefault(false)

            if (messageSent) return true
        }

        // Fall back to ChannelClient streaming for large payloads
        return sendWorkoutPayloadViaChannel(nodeId, payloadJson)
    }

    suspend fun sendWorkoutPayloadViaChannel(nodeId: String, payloadJson: String): Boolean {
        return runCatching {
            val channel = channelClient.openChannel(nodeId, SyncConstants.PATH_WORKOUT_CHANNEL).await()
            val outputStream = channelClient.getOutputStream(channel).await()
            OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(payloadJson)
                writer.flush()
            }
            // Closing outputStream sends EOF across the channel.
            // Channel closure is handled by the receiver upon full read to prevent aborting in-flight data.
            updateLastSyncTimestamp()
            true
        }.getOrDefault(false)
    }

    suspend fun sendAcknowledgment(nodeId: String, sessionId: String): Boolean {
        return runCatching {
            messageClient.sendMessage(
                nodeId,
                SyncConstants.PATH_WORKOUT_ACK,
                sessionId.toByteArray(StandardCharsets.UTF_8)
            ).await()
            true
        }.getOrDefault(false)
    }

    suspend fun sendNack(nodeId: String, sessionId: String, errorCode: String): Boolean {
        return runCatching {
            val nackMessage = "$sessionId:${errorCode}"
            messageClient.sendMessage(
                nodeId,
                SyncConstants.PATH_WORKOUT_NACK,
                nackMessage.toByteArray(StandardCharsets.UTF_8)
            ).await()
            true
        }.getOrDefault(false)
    }

    suspend fun sendMasterDataAck(nodeId: String, dataType: String, version: Int): Boolean {
        return runCatching {
            val ackMessage = "$dataType:$version"
            messageClient.sendMessage(
                nodeId,
                SyncConstants.PATH_MASTER_DATA_ACK,
                ackMessage.toByteArray(StandardCharsets.UTF_8)
            ).await()
            true
        }.getOrDefault(false)
    }

    suspend fun sendWorkoutDelete(nodeId: String, sessionId: String): Boolean {
        return runCatching {
            messageClient.sendMessage(
                nodeId,
                SyncConstants.PATH_WORKOUT_DELETE,
                sessionId.toByteArray(StandardCharsets.UTF_8)
            ).await()
            true
        }.getOrDefault(false)
    }

    suspend fun flushPendingQueue(
        syncQueueDao: SyncQueueDao,
        targetCapability: String? = null
    ): SyncResult {
        return globalFlushMutex.withLock {
            runCatching {
                // Recover any stale in-transit items that timed out (lost ACK)
                syncQueueDao.resetStaleInTransitItems(
                    System.currentTimeMillis() - SyncConstants.STALE_IN_TRANSIT_THRESHOLD_MS
                )

                val connectedNodes = getConnectedNodes()
                if (connectedNodes.isEmpty()) {
                    return@runCatching SyncResult.NoCompanionFound()
                }

                val capNodeIds = if (targetCapability != null) {
                    runCatching {
                        val capInfo = Wearable.getCapabilityClient(context)
                            .getCapability(targetCapability, CapabilityClient.FILTER_ALL)
                            .await()
                        capInfo.nodes.map { it.id }.toSet()
                    }.getOrDefault(emptySet())
                } else {
                    emptySet()
                }

                val nodeInfos = connectedNodes.map { CompanionNodeInfo(it.id, it.displayName, it.isNearby) }
                // Strict capability matching: if a target capability is specified, only send to nodes
                // that actually have it. Falling back to arbitrary nodes risks data going to the wrong device.
                val targetNodeInfo = if (targetCapability != null) {
                    nodeInfos.firstOrNull { it.id in capNodeIds }
                        ?: return@runCatching SyncResult.NoCompanionFound(
                            "No connected node with capability $targetCapability"
                        )
                } else {
                    CompanionStatusResolver.findTargetNode(nodeInfos, capNodeIds)
                        ?: return@runCatching SyncResult.NoCompanionFound()
                }

                val pendingItems = syncQueueDao.getPendingQueueItems()
                if (pendingItems.isEmpty()) {
                    return@runCatching SyncResult.Success(0)
                }

                var dispatchedCount = 0
                var failedCount = 0
                var deadLetterCount = 0
                for (item in pendingItems) {
                    // Dead-letter: items that exceeded max retries are kept in the DB (status DEAD_LETTER)
                    // so no completed workout data is ever silently lost. The queue no longer blocks
                    // because DEAD_LETTER items are excluded from getPendingQueueItems().
                    if (item.retryCount >= SyncConstants.MAX_RETRY_ATTEMPTS) {
                        Log.w(TAG,
                            "Dead-lettering queue item ${item.id} (session=${item.sessionId}) after ${item.retryCount} retries")
                        syncQueueDao.markAsDeadLetter(
                            item.id,
                            "Exceeded max retry attempts (${SyncConstants.MAX_RETRY_ATTEMPTS})"
                        )
                        deadLetterCount++
                        continue
                    }

                    syncQueueDao.updateAttemptStatus(item.id, QueueStatus.IN_TRANSIT, System.currentTimeMillis())
                    val success = when {
                        item.payloadJson == SyncConstants.ACTION_DELETE -> {
                            val delSuccess = sendWorkoutDelete(targetNodeInfo.id, item.sessionId)
                            if (delSuccess) {
                                syncQueueDao.deleteQueueItemById(item.id)
                            }
                            delSuccess
                        }
                        item.itemType == SyncConstants.ITEM_TYPE_MASTER_CATALOG -> {
                            // Master data items go via DataClient, not MessageClient/ChannelClient
                            val dataSuccess = syncEquipmentCatalog(item.payloadJson)
                            if (dataSuccess) {
                                syncQueueDao.deleteQueueItemById(item.id)
                            }
                            dataSuccess
                        }
                        item.itemType == SyncConstants.ITEM_TYPE_MASTER_TEMPLATES -> {
                            val dataSuccess = syncTemplates(item.payloadJson)
                            if (dataSuccess) {
                                syncQueueDao.deleteQueueItemById(item.id)
                            }
                            dataSuccess
                        }
                        else -> {
                            // Workout session payload: stays IN_TRANSIT until ACK arrives
                            sendWorkoutPayload(targetNodeInfo.id, item.payloadJson)
                        }
                    }

                    if (success) {
                        dispatchedCount++
                    } else {
                        failedCount++
                        syncQueueDao.updateAttemptStatus(item.id, QueueStatus.ERROR, System.currentTimeMillis())
                    }
                }

                updateLastSyncTimestamp()
                when {
                    failedCount > 0 && dispatchedCount == 0 && deadLetterCount == 0 ->
                        SyncResult.Error("Failed to transfer $failedCount pending item(s)")
                    deadLetterCount > 0 && dispatchedCount == 0 && failedCount == 0 ->
                        SyncResult.Error("Dead-lettered $deadLetterCount item(s) after ${SyncConstants.MAX_RETRY_ATTEMPTS} retries; data is retained for manual retry")
                    else ->
                        SyncResult.Success(dispatchedCount)
                }
            }.getOrElse { e ->
                SyncResult.Error(e.message ?: "Unknown sync error")
            }
        }
    }

    companion object {
        private const val TAG = "WearableDataLayerManager"
        const val PREFS_SYNC = "lockerlift_sync_prefs"
        const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private val globalFlushMutex = Mutex()
    }
}
