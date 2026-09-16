package com.lockerlift.core.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

sealed class PullRefreshSyncResult {
    data class Success(val itemsSyncedCount: Int) : PullRefreshSyncResult()
    object UpToDate : PullRefreshSyncResult()
    object WatchUnreachable : PullRefreshSyncResult()
    data class Error(val message: String) : PullRefreshSyncResult()
}

/**
 * Interface abstracting wearable companion check and flush requests,
 * allowing pure unit testing of the pull-to-refresh coordinator without Android context.
 */
interface SyncFlushRequester {
    suspend fun isWearCompanionConnected(): Boolean
    suspend fun requestWatchSyncFlush(): Boolean
}

/**
 * Coordinates on-demand synchronization initiated via Pull-to-Refresh on the smartphone.
 *
 * Flow:
 * 1. Resets the sync event bus replay cache.
 * 2. Pushes master data (machine catalog and workout templates) if provided.
 * 3. Verifies smartwatch companion connectivity.
 * 4. Sends wake-up queue-flush request to the watch via MessageClient.
 * 5. Awaits flush completion response with timeout.
 * 6. Returns structured result for user feedback.
 */
class SyncPullRefreshCoordinator(
    private val requester: SyncFlushRequester,
    private val timeoutMillis: Long = 8000L
) {

    suspend fun executeSync(
        syncMasterDataAction: (suspend () -> Unit)? = null
    ): PullRefreshSyncResult {
        return runCatching {
            // Reset completion event cache before new sync attempt
            SyncEventBus.reset()

            // Push master data first if provided (AK 5.4.4)
            syncMasterDataAction?.invoke()

            // Verify companion connectivity
            if (!requester.isWearCompanionConnected()) {
                return PullRefreshSyncResult.WatchUnreachable
            }

            // Send wake-up flush request (AK 5.4.3)
            val requestSent = requester.requestWatchSyncFlush()
            if (!requestSent) {
                return PullRefreshSyncResult.WatchUnreachable
            }

            // Await flush completion from watch with timeout (AK 5.4.5, AK 5.4.10)
            val flushCount = withTimeoutOrNull(timeoutMillis) {
                SyncEventBus.flushCompletedEvents.first()
            }

            // Clear cache after reading
            SyncEventBus.reset()

            if (flushCount == null) {
                PullRefreshSyncResult.WatchUnreachable
            } else if (flushCount > 0) {
                PullRefreshSyncResult.Success(flushCount)
            } else {
                PullRefreshSyncResult.UpToDate
            }
        }.getOrElse { e ->
            PullRefreshSyncResult.Error(e.message ?: "Unknown sync error")
        }
    }
}
