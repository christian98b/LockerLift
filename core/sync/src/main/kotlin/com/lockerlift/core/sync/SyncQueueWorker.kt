package com.lockerlift.core.sync

import android.content.Context
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
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

        val isWatch = applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        val targetCapability = if (isWatch) SyncConstants.CAPABILITY_MOBILE else SyncConstants.CAPABILITY_WEAR

        val result = dataLayerManager.flushPendingQueue(syncQueueDao, targetCapability)
        return when (result) {
            is SyncResult.Success -> Result.success()
            is SyncResult.NoCompanionFound -> Result.retry()
            is SyncResult.Error -> Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "com.lockerlift.sync.queue_flush_worker"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncQueueWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

