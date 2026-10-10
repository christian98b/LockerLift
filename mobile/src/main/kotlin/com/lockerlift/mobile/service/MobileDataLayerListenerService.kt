package com.lockerlift.mobile.service

import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.healthconnect.HealthConnectManager
import com.lockerlift.core.model.QueueStatus
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncEventBus
import com.lockerlift.core.sync.SyncIngestionEngine
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.SyncQueueWorker
import com.lockerlift.core.sync.WearableDataLayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException

class MobileDataLayerListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { LockerLiftDatabase.getInstance(this) }
    private val healthConnectManager by lazy { HealthConnectManager(this) }
    private val dataLayerManager by lazy { WearableDataLayerManager(this) }
    private val activeIngestionMutex = Mutex()

    override fun onPeerConnected(peer: Node) {
        super.onPeerConnected(peer)
        Log.i(TAG, "Wear companion reconnected (id=${peer.id}). Enqueueing sync flush.")
        SyncQueueWorker.enqueue(this)
        serviceScope.launch {
            dataLayerManager.flushPendingQueue(database.syncQueueDao(), SyncConstants.CAPABILITY_WEAR)
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        super.onChannelOpened(channel)
        if (channel.path == SyncConstants.PATH_WORKOUT_CHANNEL) {
            serviceScope.launch {
                receiveWorkoutFromChannel(channel)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        when (messageEvent.path) {
            SyncConstants.PATH_WORKOUT_ACK -> {
                val sessionId = String(messageEvent.data, StandardCharsets.UTF_8)
                serviceScope.launch {
                    if (!isAuthorizedWatchNode(messageEvent.sourceNodeId)) {
                        Log.w(TAG, "Rejected ACK from unauthorized watch node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }

                    SyncIngestionEngine.handleWorkoutAck(database, sessionId)
                    dataLayerManager.updateLastSyncTimestamp()
                    Log.i(TAG, "Workout session $sessionId successfully acknowledged by watch and purged from mobile queue.")
                }
            }
            SyncConstants.PATH_WORKOUT_DELETE -> {
                val sessionId = String(messageEvent.data, StandardCharsets.UTF_8)
                serviceScope.launch {
                    if (!isAuthorizedWatchNode(messageEvent.sourceNodeId)) {
                        Log.w(TAG, "Rejected delete from unauthorized watch node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }

                    SyncIngestionEngine.handleWorkoutDelete(database, sessionId)
                    if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                        healthConnectManager.deleteWorkoutSession(sessionId)
                    }
                    dataLayerManager.updateLastSyncTimestamp()
                    Log.i(TAG, "Workout session $sessionId deleted on phone via sync.")
                }
            }
            SyncConstants.PATH_WORKOUT_MESSAGE -> {
                val payloadJson = String(messageEvent.data, StandardCharsets.UTF_8)
                serviceScope.launch {
                    if (!isAuthorizedWatchNode(messageEvent.sourceNodeId)) {
                        Log.w(TAG, "Rejected workout message from unauthorized watch node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }
                    runCatching {
                        processWorkoutPayload(payloadJson, messageEvent.sourceNodeId)
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to process workout message payload", e)
                        // Send NACK with error code
                        val errorCode = when (e) {
                            is IllegalArgumentException -> SyncConstants.NACK_INVALID_PAYLOAD
                            is android.database.sqlite.SQLiteException -> SyncConstants.NACK_DATABASE_ERROR
                            else -> SyncConstants.NACK_UNKNOWN_ERROR
                        }
                        val sessionId = runCatching {
                            SyncPayloadSerializer.decodeSessionPayload(payloadJson).session.id
                        }.getOrNull() ?: "unknown"
                        dataLayerManager.sendNack(messageEvent.sourceNodeId, sessionId, errorCode)
                    }
                }
            }
            SyncConstants.PATH_WORKOUT_NACK -> {
                val nackMessage = String(messageEvent.data, StandardCharsets.UTF_8)
                serviceScope.launch {
                    if (!isAuthorizedWatchNode(messageEvent.sourceNodeId)) {
                        Log.w(TAG, "Rejected NACK from unauthorized watch node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }
                    val parts = nackMessage.split(":", limit = 2)
                    val sessionId = parts.getOrNull(0) ?: return@launch
                    val errorCode = parts.getOrNull(1) ?: SyncConstants.NACK_UNKNOWN_ERROR
                    Log.w(TAG, "Received NACK for session $sessionId with error: $errorCode")
                    // Update queue item with error status
                    runCatching {
                        val queueItem = database.syncQueueDao().getQueueItemBySessionId(sessionId)
                        if (queueItem != null) {
                            database.syncQueueDao().updateQueueItem(
                                queueItem.copy(
                                    status = QueueStatus.ERROR,
                                    errorMessage = "NACK: $errorCode",
                                    lastAttemptAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to update queue item with NACK status", e)
                    }
                }
            }
            SyncConstants.PATH_MASTER_DATA_ACK -> {
                val ackMessage = String(messageEvent.data, StandardCharsets.UTF_8)
                serviceScope.launch {
                    if (!isAuthorizedWatchNode(messageEvent.sourceNodeId)) {
                        Log.w(TAG, "Rejected master data ACK from unauthorized watch node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }
                    val parts = ackMessage.split(":", limit = 2)
                    val dataType = parts.getOrNull(0) ?: return@launch
                    val version = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    Log.i(TAG, "Received master data ACK for $dataType version $version")
                }
            }
            SyncConstants.PATH_SYNC_FLUSH_COMPLETED -> {
                val countString = String(messageEvent.data, StandardCharsets.UTF_8)
                val count = countString.toIntOrNull() ?: 0
                serviceScope.launch {
                    // Ensure active payload ingestion finishes writing to Room before notifying completion
                    activeIngestionMutex.withLock {
                        SyncEventBus.notifyFlushCompleted(count)
                        dataLayerManager.updateLastSyncTimestamp()
                        Log.i(TAG, "Received sync flush completion notice from watch: $count workouts transferred.")
                    }
                }
            }
            SyncConstants.PATH_REQUEST_MASTER_DATA -> {
                serviceScope.launch {
                    if (!isAuthorizedWatchNode(messageEvent.sourceNodeId)) {
                        Log.w(TAG, "Rejected master data request from unauthorized watch node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }
                    Log.i(TAG, "Watch requested master data push. Dispatching catalog and templates.")
                    com.lockerlift.mobile.sync.MobileMasterDataSync.pushAllMasterData(
                        this@MobileDataLayerListenerService,
                        database,
                        dataLayerManager
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private suspend fun isAuthorizedWatchNode(nodeId: String): Boolean {
        return runCatching {
            val capabilityClient = Wearable.getCapabilityClient(this)
            val capabilityInfo = capabilityClient
                .getCapability(SyncConstants.CAPABILITY_WEAR, CapabilityClient.FILTER_ALL)
                .await()
            if (capabilityInfo.nodes.isEmpty()) {
                // If capability registration is pending, fall back to checking connected nodes
                val connectedNodes = Wearable.getNodeClient(this).connectedNodes.await()
                return connectedNodes.any { it.id == nodeId }
            }
            capabilityInfo.nodes.any { it.id == nodeId }
        }.getOrDefault(false)
    }

    private suspend fun processWorkoutPayload(payloadJson: String, sourceNodeId: String) {
        activeIngestionMutex.withLock {
            when (val result = SyncIngestionEngine.ingestWorkoutPayload(database, payloadJson, isMobile = true)) {
                is SyncIngestionEngine.IngestionResult.Success -> {
                    // Export to Health Connect
                    if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                        healthConnectManager.exportWorkoutSession(
                            session = result.payload.session,
                            templateName = result.payload.templateName
                        )
                    }

                    // Send Acknowledgment back to sender node
                    dataLayerManager.sendAcknowledgment(sourceNodeId, result.sessionId)
                    dataLayerManager.updateLastSyncTimestamp()
                    Log.i(TAG, "Workout session ${result.sessionId} successfully processed and synchronized.")
                }
                is SyncIngestionEngine.IngestionResult.RejectedZombie -> {
                    Log.i(TAG, "Rejecting incoming session ${result.sessionId} because it was deleted locally. Dispatching delete ACK to watch.")
                    dataLayerManager.sendWorkoutDelete(sourceNodeId, result.sessionId)
                }
            }
        }
    }

    private suspend fun receiveWorkoutFromChannel(channel: ChannelClient.Channel) {
        val channelClient = Wearable.getChannelClient(this)
        try {
            if (!isAuthorizedWatchNode(channel.nodeId)) {
                Log.w(TAG, "Rejected workout payload from unauthorized node: ${channel.nodeId}")
                channelClient.close(channel).await()
                return
            }

            val inputStream = channelClient.getInputStream(channel).await()

            // Read with bounded size limit and timeout to avoid OutOfMemory and hanging (SEC-04)
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var totalBytes = 0
            val startTime = System.currentTimeMillis()

            inputStream.use { stream ->
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes > MAX_PAYLOAD_BYTES) {
                        throw IllegalStateException("Payload size exceeds maximum allowed limit ($MAX_PAYLOAD_BYTES bytes)")
                    }
                    // Check timeout every 8KB chunk
                    if (System.currentTimeMillis() - startTime > SyncConstants.CHANNEL_READ_TIMEOUT_MS) {
                        throw TimeoutException("Channel read timed out after ${SyncConstants.CHANNEL_READ_TIMEOUT_MS}ms")
                    }
                    outputStream.write(buffer, 0, bytesRead)
                }
            }

            val payloadJson = outputStream.toString(StandardCharsets.UTF_8.name())
            processWorkoutPayload(payloadJson, channel.nodeId)

            channelClient.close(channel).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive and process workout payload", e)
            runCatching { channelClient.close(channel).await() }
        }
    }

    companion object {
        private const val TAG = "MobileDataLayerService"
        private const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024 // 5 MB maximum bound
    }
}
