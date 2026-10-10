package com.lockerlift.wear

import android.app.Application
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.sync.SyncIngestionEngine
import com.lockerlift.core.sync.SyncQueueWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LockerLiftWearApp : Application() {

    val database by lazy { LockerLiftDatabase.getInstance(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Periodic safety-net sync flush (every 15 min) ensures no queued item is stranded
        SyncQueueWorker.enqueuePeriodic(this)
        // Recover orphaned PENDING_SYNC sessions that lost their queue entry (e.g. crash between write and queue insert)
        appScope.launch {
            val recovered = SyncIngestionEngine.recoverOrphanedSessions(database)
            if (recovered > 0) {
                SyncQueueWorker.enqueue(this@LockerLiftWearApp)
            }
        }
    }
}
