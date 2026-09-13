package com.lockerlift.wear.communication

import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
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
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.SyncStatus
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.SyncQueueWorker
import com.lockerlift.core.sync.WearableDataLayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private suspend fun receiveWorkoutFromChannel(channel: ChannelClient.Channel) {
        val channelClient = Wearable.getChannelClient(this)
        try {
            // Verify node capability if available (SEC-05)
            val capabilityClient = Wearable.getCapabilityClient(this)
            val capabilityInfo = capabilityClient
                .getCapability(SyncConstants.CAPABILITY_MOBILE, CapabilityClient.FILTER_ALL)
                .await()

            if (capabilityInfo.nodes.isNotEmpty() && capabilityInfo.nodes.none { it.id == channel.nodeId }) {
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
            val payload = SyncPayloadSerializer.decodeSessionPayload(payloadJson)
            val session = payload.session.copy(syncStatus = SyncStatus.SYNCED)

            val sessionDao = database.workoutSessionDao()
            val machineDao = database.machineDao()

            val instanceEntities = payload.machineInstances.map { it.instance.toEntity() }
            val setEntities = payload.machineInstances.flatMap { it.sets.map { set -> set.toEntity() } }
            val machineEntities = payload.machineInstances.map { it.machine.toEntity() }

            machineDao.insertMachines(machineEntities)
            sessionDao.upsertFullSession(
                session = session.toEntity(),
                instances = instanceEntities,
                sets = setEntities
            )

            // Send Acknowledgment back to phone
            dataLayerManager.sendAcknowledgment(channel.nodeId, session.id)

            channelClient.close(channel).await()
            Log.i(TAG, "Workout session ${session.id} synchronized from phone to watch.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive workout payload from phone", e)
            runCatching { channelClient.close(channel).await() }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val payloadString = dataMap.getString("payload") ?: continue

                when (uri.path) {
                    SyncConstants.PATH_EQUIPMENT_CATALOG -> {
                        serviceScope.launch {
                            runCatching {
                                val machines = SyncPayloadSerializer.decodeMachines(payloadString)
                                database.machineDao().insertMachines(machines.map { it.toEntity() })
                                Log.i(TAG, "Synchronized ${machines.size} machines from phone.")
                            }.onFailure { e ->
                                Log.e(TAG, "Failed to sync equipment catalog", e)
                            }
                        }
                    }
                    SyncConstants.PATH_WORKOUT_TEMPLATES -> {
                        serviceScope.launch {
                            runCatching {
                                val payloads = SyncPayloadSerializer.decodeTemplates(payloadString)
                                for (payload in payloads) {
                                    database.workoutTemplateDao().saveTemplateWithMachines(
                                        template = payload.template.toEntity(),
                                        machineIdsInOrder = payload.machineIdsInOrder
                                    )
                                }
                                Log.i(TAG, "Synchronized ${payloads.size} templates from phone.")
                            }.onFailure { e ->
                                Log.e(TAG, "Failed to sync workout templates", e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path == SyncConstants.PATH_WORKOUT_ACK) {
            val sessionId = String(messageEvent.data, Charsets.UTF_8)
            serviceScope.launch {
                // Verify sender node capability if available (SEC-05)
                runCatching {
                    val capabilityInfo = Wearable.getCapabilityClient(this@WearDataLayerListenerService)
                        .getCapability(SyncConstants.CAPABILITY_MOBILE, CapabilityClient.FILTER_ALL)
                        .await()
                    if (capabilityInfo.nodes.isNotEmpty() && capabilityInfo.nodes.none { it.id == messageEvent.sourceNodeId }) {
                        Log.w(TAG, "Rejected ACK from unauthorized node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }
                }

                database.syncQueueDao().deleteQueueItemBySessionId(sessionId)
                Log.i(TAG, "Workout session $sessionId successfully acknowledged and removed from queue.")
            }
        }
    }

    override fun onPeerConnected(peer: Node) {
        super.onPeerConnected(peer)
        Log.i(TAG, "Companion node reconnected (id=${peer.id}). Triggering sync worker.")
        val request = OneTimeWorkRequestBuilder<SyncQueueWorker>().build()
        WorkManager.getInstance(this).enqueue(request)
    }

    companion object {
        private const val TAG = "WearDataLayerListener"
        private const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024
    }
}
