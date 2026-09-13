package com.lockerlift.mobile.service

import android.util.Log
import com.google.android.gms.wearable.ChannelClient
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.InputStreamReader
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

    private suspend fun receiveWorkoutFromChannel(channel: ChannelClient.Channel) {
        try {
            val channelClient = Wearable.getChannelClient(this)
            val inputStream = channelClient.getInputStream(channel).await()
            val payloadJson = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use {
                it.readText()
            }

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
        }
    }

    companion object {
        private const val TAG = "MobileDataLayerService"
    }
}
