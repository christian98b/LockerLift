package com.lockerlift.wear.communication

import android.util.Log
import androidx.work.WorkManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.SyncStatus
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.SyncQueueWorker
import com.lockerlift.core.sync.SyncResult
import com.lockerlift.core.sync.WearableDataLayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class WearDataLayerListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { LockerLiftDatabase.getInstance(this) }
    private val dataLayerManager by lazy { WearableDataLayerManager(this) }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        super.onChannelOpened(channel)
        if (channel.path == SyncConstants.PATH_WORKOUT_CHANNEL) {
            serviceScope.launch {
                receiveWorkoutFromChannel(channel)
            }
        }
    }

    private suspend fun isAuthorizedPhoneNode(nodeId: String): Boolean {
        return runCatching {
            val capabilityClient = Wearable.getCapabilityClient(this)
            val capabilityInfo = capabilityClient
                .getCapability(SyncConstants.CAPABILITY_MOBILE, CapabilityClient.FILTER_ALL)
                .await()
            if (capabilityInfo.nodes.isEmpty()) {
                val connectedNodes = Wearable.getNodeClient(this).connectedNodes.await()
                return connectedNodes.any { it.id == nodeId }
            }
            capabilityInfo.nodes.any { it.id == nodeId }
        }.getOrDefault(true)
    }

    private suspend fun processWorkoutPayload(payloadJson: String, sourceNodeId: String) {
        val payload = SyncPayloadSerializer.decodeSessionPayload(payloadJson)
        val session = payload.session.copy(syncStatus = SyncStatus.SYNCED)

        val sessionDao = database.workoutSessionDao()
        val machineDao = database.machineDao()

        // Safe Machine Reconciliation: Avoid unique name collision and foreign key violations
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

        sessionDao.upsertFullSession(
            session = session.toEntity(),
            instances = instanceEntities,
            sets = setEntities
        )

        // Send Acknowledgment back to phone
        dataLayerManager.sendAcknowledgment(sourceNodeId, session.id)
        dataLayerManager.updateLastSyncTimestamp()
        Log.i(TAG, "Workout session ${session.id} synchronized from phone to watch.")
    }

    private suspend fun receiveWorkoutFromChannel(channel: ChannelClient.Channel) {
        val channelClient = Wearable.getChannelClient(this)
        try {
            if (!isAuthorizedPhoneNode(channel.nodeId)) {
                Log.w(TAG, "Rejected workout payload from unauthorized phone node: ${channel.nodeId}")
                channelClient.close(channel).await()
                return
            }

            val inputStream = channelClient.getInputStream(channel).await()
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
            Log.e(TAG, "Failed to receive workout payload from phone", e)
            runCatching { channelClient.close(channel).await() }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        val catalogEvents = mutableListOf<String>()
        val templateEvents = mutableListOf<String>()

        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val payloadString = dataMap.getString("payload") ?: continue

                when (uri.path) {
                    SyncConstants.PATH_EQUIPMENT_CATALOG -> catalogEvents.add(payloadString)
                    SyncConstants.PATH_WORKOUT_TEMPLATES -> templateEvents.add(payloadString)
                }
            }
        }

        if (catalogEvents.isNotEmpty() || templateEvents.isNotEmpty()) {
            serviceScope.launch {
                // 1. Process catalog updates first so machines exist before template cross-references
                for (catalogPayload in catalogEvents) {
                    runCatching {
                        val machines = SyncPayloadSerializer.decodeMachines(catalogPayload)
                        database.machineDao().insertMachines(machines.map { it.toEntity() })
                        dataLayerManager.updateLastSyncTimestamp()
                        Log.i(TAG, "Synchronized ${machines.size} machines from phone.")
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to sync equipment catalog", e)
                    }
                }

                // 2. Process templates sequentially after catalog updates have completed
                for (templatePayload in templateEvents) {
                    runCatching {
                        val payloads = SyncPayloadSerializer.decodeTemplates(templatePayload)
                        val incomingIds = payloads.map { it.template.id }.toSet()

                        // Archive local templates that are no longer active on the phone
                        val localActiveTemplates = database.workoutTemplateDao().getAllActiveTemplatesWithMachinesFlow().first()
                        for (local in localActiveTemplates) {
                            if (local.template.id !in incomingIds) {
                                database.workoutTemplateDao().archiveTemplate(local.template.id)
                            }
                        }

                        // Upsert templates and machine cross-references
                        for (payload in payloads) {
                            database.workoutTemplateDao().saveTemplateWithMachines(
                                template = payload.template.toEntity(),
                                machineIdsInOrder = payload.machineIdsInOrder
                            )
                        }
                        dataLayerManager.updateLastSyncTimestamp()
                        Log.i(TAG, "Synchronized ${payloads.size} templates from phone.")
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to sync workout templates", e)
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        when (messageEvent.path) {
            SyncConstants.PATH_WORKOUT_ACK -> {
                val sessionId = String(messageEvent.data, Charsets.UTF_8)
                serviceScope.launch {
                    if (!isAuthorizedPhoneNode(messageEvent.sourceNodeId)) {
                        Log.w(TAG, "Rejected ACK from unauthorized node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }

                    database.syncQueueDao().deleteQueueItemBySessionId(sessionId)
                    database.workoutSessionDao().updateSyncStatus(sessionId, SyncStatus.SYNCED)
                    dataLayerManager.updateLastSyncTimestamp()
                    Log.i(TAG, "Workout session $sessionId successfully acknowledged and marked as SYNCED on watch.")
                }
            }
            SyncConstants.PATH_WORKOUT_DELETE -> {
                val sessionId = String(messageEvent.data, Charsets.UTF_8)
                serviceScope.launch {
                    if (!isAuthorizedPhoneNode(messageEvent.sourceNodeId)) {
                        Log.w(TAG, "Rejected delete from unauthorized node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }

                    database.workoutSessionDao().deleteSession(sessionId)
                    database.syncQueueDao().deleteQueueItemBySessionId(sessionId)
                    dataLayerManager.updateLastSyncTimestamp()
                    Log.i(TAG, "Workout session $sessionId deleted on watch via sync.")
                }
            }
            SyncConstants.PATH_WORKOUT_MESSAGE -> {
                val payloadJson = String(messageEvent.data, StandardCharsets.UTF_8)
                serviceScope.launch {
                    if (!isAuthorizedPhoneNode(messageEvent.sourceNodeId)) {
                        Log.w(TAG, "Rejected workout message from unauthorized node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }
                    runCatching {
                        processWorkoutPayload(payloadJson, messageEvent.sourceNodeId)
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to process workout message on watch", e)
                    }
                }
            }
            SyncConstants.PATH_SYNC_REQUEST_FLUSH -> {
                serviceScope.launch {
                    if (!isAuthorizedPhoneNode(messageEvent.sourceNodeId)) {
                        Log.w(TAG, "Rejected flush request from unauthorized node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }
                    Log.i(TAG, "Received wake-up flush request from phone. Processing sync queue immediately...")
                    val result = dataLayerManager.flushPendingQueue(
                        database.syncQueueDao(),
                        SyncConstants.CAPABILITY_MOBILE
                    )
                    val syncedCount = when (result) {
                        is SyncResult.Success -> result.itemsSyncedCount
                        else -> 0
                    }
                    dataLayerManager.sendSyncFlushCompleted(messageEvent.sourceNodeId, syncedCount)
                    Log.i(TAG, "Queue flush completed. Sent completion notification with count=$syncedCount to phone.")
                }
            }
        }
    }

    override fun onPeerConnected(peer: Node) {
        super.onPeerConnected(peer)
        Log.i(TAG, "Companion node reconnected (id=${peer.id}). Triggering sync worker and queue flush.")
        SyncQueueWorker.enqueue(this)
        serviceScope.launch {
            dataLayerManager.flushPendingQueue(database.syncQueueDao(), SyncConstants.CAPABILITY_MOBILE)
        }
    }

    companion object {
        private const val TAG = "WearDataLayerListener"
        private const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024
    }
}
