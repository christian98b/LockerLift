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
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.WorkoutTemplateEntity
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncQueueWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WearDataLayerListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { LockerLiftDatabase.getInstance(this) }
    private val json = Json { ignoreUnknownKeys = true }

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
                                val machines = json.decodeFromString<List<MachineEntity>>(payloadString)
                                database.machineDao().insertMachines(machines)
                                Log.i(TAG, "Synchronized ${machines.size} machines from phone.")
                            }
                        }
                    }
                    SyncConstants.PATH_WORKOUT_TEMPLATES -> {
                        serviceScope.launch {
                            runCatching {
                                val templates = json.decodeFromString<List<WorkoutTemplateEntity>>(payloadString)
                                for (template in templates) {
                                    database.workoutTemplateDao().insertTemplate(template)
                                }
                                Log.i(TAG, "Synchronized ${templates.size} templates from phone.")
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
