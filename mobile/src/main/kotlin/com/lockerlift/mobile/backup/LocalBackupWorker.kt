package com.lockerlift.mobile.backup

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lockerlift.core.database.LockerLiftDatabase
import java.util.concurrent.TimeUnit

/**
 * [CoroutineWorker] that periodically exports a GZip-compressed JSON backup to
 * a user-selected SAF directory and prunes old backups.
 *
 * Scheduled via [schedule]; cancelled via [cancel].
 *
 * Input data keys:
 * - [KEY_DIRECTORY_URI] – SAF tree URI string for the target directory.
 * - [KEY_KEEP_COUNT]    – maximum number of backup files to retain.
 */
class LocalBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val dirUriString = inputData.getString(KEY_DIRECTORY_URI) ?: return Result.failure()
        val keepCount = inputData.getInt(KEY_KEEP_COUNT, 5)
        val dirUri = Uri.parse(dirUriString)
        val db = LockerLiftDatabase.getInstance(applicationContext)
        val manager = LocalBackupManager(applicationContext, db)
        return when (manager.exportToDirectory(dirUri)) {
            is BackupResult.Success -> {
                manager.deleteOldBackups(dirUri, keepCount)
                Result.success()
            }
            is BackupResult.Error -> Result.retry()
        }
    }

    companion object {
        const val KEY_DIRECTORY_URI = "key_dir_uri"
        const val KEY_KEEP_COUNT = "key_keep_count"
        const val WORK_NAME = "LocalBackupWorker"

        /**
         * Enqueues (or updates) a unique periodic backup job.
         *
         * @param context        Application context.
         * @param directoryUri   String form of the SAF tree URI to write backups into.
         * @param intervalDays   How often (in days) to run the backup.
         * @param keepCount      Maximum number of backup files to keep in [directoryUri].
         */
        fun schedule(context: Context, directoryUri: String, intervalDays: Long, keepCount: Int) {
            val data = workDataOf(
                KEY_DIRECTORY_URI to directoryUri,
                KEY_KEEP_COUNT to keepCount
            )
            val request = PeriodicWorkRequestBuilder<LocalBackupWorker>(intervalDays, TimeUnit.DAYS)
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /**
         * Cancels the scheduled periodic backup job.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
