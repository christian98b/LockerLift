package com.lockerlift.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lockerlift.core.database.LockerLiftDatabase

class SyncQueueWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = LockerLiftDatabase.getInstance(applicationContext)
        val syncQueueDao = database.syncQueueDao()
        val dataLayerManager = WearableDataLayerManager(applicationContext)

        val result = dataLayerManager.flushPendingQueue(syncQueueDao)
        return when (result) {
            is SyncResult.Success -> Result.success()
            is SyncResult.NoCompanionFound -> Result.retry()
            is SyncResult.Error -> Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncQueueWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
