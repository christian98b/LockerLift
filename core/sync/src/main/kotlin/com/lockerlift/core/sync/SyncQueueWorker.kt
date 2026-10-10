package com.lockerlift.core.sync

import android.content.Context
import android.content.pm.PackageManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lockerlift.core.database.LockerLiftDatabase
import java.util.concurrent.TimeUnit

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

        // Periodic tombstone cleanup (runs opportunistically during sync flush)
        runCatching { SyncIngestionEngine.cleanupExpiredTombstones(database) }

        val result = dataLayerManager.flushPendingQueue(syncQueueDao, targetCapability)
        return when (result) {
            is SyncResult.Success -> Result.success()
            is SyncResult.NoCompanionFound -> Result.retry()
            is SyncResult.Error -> Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "com.lockerlift.sync.queue_flush_worker"
        const val PERIODIC_WORK_NAME = "com.lockerlift.sync.periodic_queue_flush_worker"

        /** Immediate one-time flush (event-driven: workout complete, peer reconnect, manual sync). */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncQueueWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Periodic safety-net flush (every 15 minutes) ensuring no sync queue item
         * is stranded if all event-driven triggers fail or WorkManager retries exhaust.
         * Requires no network constraint — Wearable Data Layer handles proximity internally.
         */
        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncQueueWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

