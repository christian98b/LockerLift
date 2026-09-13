package com.lockerlift.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.model.QueueStatus

class SyncQueueWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = LockerLiftDatabase.getInstance(applicationContext)
        val syncQueueDao = database.syncQueueDao()
        val dataLayerManager = WearableDataLayerManager(applicationContext)

        val connectedNodes = dataLayerManager.getConnectedNodes()
        if (connectedNodes.isEmpty()) {
            // No phone in range (still in gym / locker)
            return Result.retry()
        }

        val pendingItems = syncQueueDao.getPendingQueueItems()
        if (pendingItems.isEmpty()) {
            return Result.success()
        }

        val targetNodeId = connectedNodes.first().id

        for (item in pendingItems) {
            syncQueueDao.updateAttemptStatus(item.id, QueueStatus.IN_TRANSIT, System.currentTimeMillis())
            val success = dataLayerManager.sendWorkoutPayloadViaChannel(targetNodeId, item.payloadJson)
            if (success) {
                // Acknowledgment from phone will remove or mark item as ACKNOWLEDGED
            } else {
                syncQueueDao.updateAttemptStatus(item.id, QueueStatus.ERROR, System.currentTimeMillis())
            }
        }

        return Result.success()
    }
}
