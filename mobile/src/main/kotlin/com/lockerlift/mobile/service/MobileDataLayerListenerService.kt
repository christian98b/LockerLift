package com.lockerlift.mobile.service

import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.healthconnect.HealthConnectManager
import com.lockerlift.core.model.SyncStatus
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WearableDataLayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class MobileDataLayerListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { LockerLiftDatabase.getInstance(this) }
    private val healthConnectManager by lazy { HealthConnectManager(this) }
    private val dataLayerManager by lazy { WearableDataLayerManager(this) }

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
        if (messageEvent.path == SyncConstants.PATH_WORKOUT_ACK) {
            val sessionId = String(messageEvent.data, StandardCharsets.UTF_8)
            serviceScope.launch {
                // Verify sender node capability if available (SEC-05)
                runCatching {
                    val capabilityInfo = Wearable.getCapabilityClient(this@MobileDataLayerListenerService)
                        .getCapability(SyncConstants.CAPABILITY_WEAR, CapabilityClient.FILTER_ALL)
                        .await()
                    if (capabilityInfo.nodes.isNotEmpty() && capabilityInfo.nodes.none { it.id == messageEvent.sourceNodeId }) {
                        Log.w(TAG, "Rejected ACK from unauthorized watch node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }
                }

                database.syncQueueDao().deleteQueueItemBySessionId(sessionId)
                database.workoutSessionDao().updateSyncStatus(sessionId, SyncStatus.SYNCED)
                Log.i(TAG, "Workout session $sessionId successfully acknowledged by watch and purged from mobile queue.")
            }
        } else if (messageEvent.path == SyncConstants.PATH_WORKOUT_DELETE) {
            val sessionId = String(messageEvent.data, StandardCharsets.UTF_8)
            serviceScope.launch {
                // Verify sender node capability if available (SEC-05)
                runCatching {
                    val capabilityInfo = Wearable.getCapabilityClient(this@MobileDataLayerListenerService)
                        .getCapability(SyncConstants.CAPABILITY_WEAR, CapabilityClient.FILTER_ALL)
                        .await()
                    if (capabilityInfo.nodes.isNotEmpty() && capabilityInfo.nodes.none { it.id == messageEvent.sourceNodeId }) {
                        Log.w(TAG, "Rejected delete from unauthorized watch node: ${messageEvent.sourceNodeId}")
                        return@launch
                    }
                }

                database.workoutSessionDao().deleteSession(sessionId)
                database.syncQueueDao().deleteQueueItemBySessionId(sessionId)
                if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                    healthConnectManager.deleteWorkoutSession(sessionId)
                }
                Log.i(TAG, "Workout session $sessionId deleted on phone via sync.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private suspend fun receiveWorkoutFromChannel(channel: ChannelClient.Channel) {
        val channelClient = Wearable.getChannelClient(this)
        try {
            // Verify node capability if available (SEC-05)
            val capabilityClient = Wearable.getCapabilityClient(this)
            val capabilityInfo = capabilityClient
                .getCapability(SyncConstants.CAPABILITY_WEAR, CapabilityClient.FILTER_ALL)
                .await()

            if (capabilityInfo.nodes.isNotEmpty() && capabilityInfo.nodes.none { it.id == channel.nodeId }) {
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

            val payload = SyncPayloadSerializer.decodeSessionPayload(payloadJson)
            val session = payload.session.copy(syncStatus = SyncStatus.SYNCED)
            val sessionDao = database.workoutSessionDao()
            val machineDao = database.machineDao()

            val instanceEntities = payload.machineInstances.map { it.instance.toEntity() }
            val setEntities = payload.machineInstances.flatMap { it.sets.map { set -> set.toEntity() } }
            val machineEntities = payload.machineInstances.map { it.machine.toEntity() }

            // Persist machines first in case ad-hoc machines were created on the watch
            machineDao.insertMachines(machineEntities)

            // Upsert session, instances and sets
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
            dataLayerManager.sendAcknowledgment(channel.nodeId, session.id)

            channelClient.close(channel).await()
            Log.i(TAG, "Workout session ${session.id} successfully received and synchronized.")
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
