package com.lockerlift.mobile.service

import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.healthconnect.HealthConnectManager
import com.lockerlift.core.model.SyncStatus
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncEventBus
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

                    database.syncQueueDao().deleteQueueItemBySessionId(sessionId)
                    database.workoutSessionDao().updateSyncStatus(sessionId, SyncStatus.SYNCED)
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

                    database.workoutSessionDao().deleteSession(sessionId)
                    database.syncQueueDao().deleteQueueItemBySessionId(sessionId)
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
                    }
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
        }.getOrDefault(true)
    }

    private suspend fun processWorkoutPayload(payloadJson: String, sourceNodeId: String) {
        activeIngestionMutex.withLock {
            val payload = SyncPayloadSerializer.decodeSessionPayload(payloadJson)
            val session = payload.session.copy(syncStatus = SyncStatus.SYNCED)
            val sessionDao = database.workoutSessionDao()
            val machineDao = database.machineDao()

            // Zombie Prevention: If this session was deleted on this device and is pending deletion sync,
            // do not resurrect it. Re-dispatch delete to the watch instead.
            val pendingDelete = database.syncQueueDao().getQueueItemBySessionId(session.id)
            if (pendingDelete != null && pendingDelete.payloadJson == SyncConstants.ACTION_DELETE) {
                Log.i(TAG, "Rejecting incoming session ${session.id} because it was deleted locally. Dispatching delete ACK to watch.")
                dataLayerManager.sendWorkoutDelete(sourceNodeId, session.id)
                return@withLock
            }

            // Safe Machine Reconciliation: If a machine with the same name exists locally with a different ID,
            // remap the session machine instance to the existing local machine ID to prevent unique name constraint
            // collisions and SQLite FOREIGN KEY RESTRICT violations.
            val machinesToInsert = mutableListOf<MachineEntity>()
            val machineIdMapping = mutableMapOf<String, String>()

            for (instPayload in payload.machineInstances) {
                val incomingMachine = instPayload.machine
                val existingMachine = machineDao.getMachineByName(incomingMachine.name)
                if (existingMachine != null) {
                    machineIdMapping[incomingMachine.id] = existingMachine.id
                } else {
                    machineIdMapping[incomingMachine.id] = incomingMachine.id
                    if (machinesToInsert.none { it.id == incomingMachine.id }) {
                        machinesToInsert.add(incomingMachine.toEntity())
                    }
                }
            }

            if (machinesToInsert.isNotEmpty()) {
                machineDao.insertMachines(machinesToInsert)
            }

            val instanceEntities = payload.machineInstances.map { instPayload ->
                val resolvedMachineId = machineIdMapping[instPayload.machine.id] ?: instPayload.instance.machineId
                instPayload.instance.copy(machineId = resolvedMachineId).toEntity()
            }
            val setEntities = payload.machineInstances.flatMap { it.sets.map { set -> set.toEntity() } }

            // Upsert session, instances and sets idempotently
            sessionDao.upsertFullSession(
                session = session.toEntity(),
                instances = instanceEntities,
                sets = setEntities
            )

            // Export to Health Connect
            if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                healthConnectManager.exportWorkoutSession(
                    session = session,
                    templateName = payload.templateName
                )
            }

            // Send Acknowledgment back to sender node
            dataLayerManager.sendAcknowledgment(sourceNodeId, session.id)
            dataLayerManager.updateLastSyncTimestamp()
            Log.i(TAG, "Workout session ${session.id} successfully processed and synchronized.")
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

            // Read with bounded size limit to avoid OutOfMemory denial-of-service (SEC-04)
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var totalBytes = 0

            inputStream.use { stream ->
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes > MAX_PAYLOAD_BYTES) {
                        throw IllegalStateException("Payload size exceeds maximum allowed limit ($MAX_PAYLOAD_BYTES bytes)")
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
