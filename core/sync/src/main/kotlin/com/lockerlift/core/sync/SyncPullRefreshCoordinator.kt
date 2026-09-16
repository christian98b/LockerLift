package com.lockerlift.core.sync

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 * 1. Pushes master data (machine catalog and workout templates) if provided.
 * 2. Verifies smartwatch companion connectivity.
 * 3. Subscribes to the completion event bus before dispatching the request.
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
            // Push master data first if provided (AK 5.4.4)
            syncMasterDataAction?.invoke()

            // Verify companion connectivity
            if (!requester.isWearCompanionConnected()) {
                return PullRefreshSyncResult.WatchUnreachable
            }

            // Subscribe BEFORE sending request to avoid missing immediate responses (AK 5.4.5, AK 5.4.10)
            coroutineScope {
                val eventAsync = async {
                    SyncEventBus.flushCompletedEvents.first()
                }

                val requestSent = requester.requestWatchSyncFlush()
                if (!requestSent) {
                    eventAsync.cancel()
                    return@coroutineScope PullRefreshSyncResult.WatchUnreachable
                }

                val flushCount = withTimeoutOrNull(timeoutMillis) {
                    eventAsync.await()
                }

                if (flushCount == null) {
                    eventAsync.cancel()
                    PullRefreshSyncResult.WatchUnreachable
                } else if (flushCount > 0) {
                    PullRefreshSyncResult.Success(flushCount)
                } else {
                    PullRefreshSyncResult.UpToDate
                }
            }
        }.getOrElse { e ->
            PullRefreshSyncResult.Error(e.message ?: "Unknown sync error")
        }
    }
}
