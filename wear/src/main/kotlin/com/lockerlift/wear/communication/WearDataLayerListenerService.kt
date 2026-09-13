package com.lockerlift.wear.communication

import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.SyncQueueWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WearDataLayerListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { LockerLiftDatabase.getInstance(this) }

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
                database.syncQueueDao().deleteQueueItemBySessionId(sessionId)
                Log.i(TAG, "Workout session $sessionId successfully acknowledged and removed from queue.")
            }
        }
    }

    override fun onPeerConnected(peer: Node) {
        super.onPeerConnected(peer)
        Log.i(TAG, "Phone reconnected (${peer.displayName}). Triggering sync worker.")
        val request = OneTimeWorkRequestBuilder<SyncQueueWorker>().build()
        WorkManager.getInstance(this).enqueue(request)
    }

    companion object {
        private const val TAG = "WearDataLayerListener"
    }
}
