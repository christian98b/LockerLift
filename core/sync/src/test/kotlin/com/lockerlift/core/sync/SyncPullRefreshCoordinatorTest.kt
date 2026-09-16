package com.lockerlift.core.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPullRefreshCoordinatorTest {

    private class FakeSyncFlushRequester(
        var isConnected: Boolean = true,
        var requestFlushResult: Boolean = true,
        var onFlushRequested: (() -> Unit)? = null
    ) : SyncFlushRequester {
        var requestFlushCalledCount = 0

        override suspend fun isWearCompanionConnected(): Boolean = isConnected

        override suspend fun requestWatchSyncFlush(): Boolean {
            requestFlushCalledCount++
            onFlushRequested?.invoke()
            return requestFlushResult
        }
    }

    @Test
    fun executeSync_whenWatchDisconnected_returnsWatchUnreachable() = runBlocking {
        val fakeRequester = FakeSyncFlushRequester(isConnected = false)
        val coordinator = SyncPullRefreshCoordinator(fakeRequester, timeoutMillis = 1000L)

        var masterDataSynced = false
        val result = coordinator.executeSync(
            syncMasterDataAction = { masterDataSynced = true }
        )

        assertTrue("Master data should be pushed even if watch is disconnected", masterDataSynced)
        assertEquals(0, fakeRequester.requestFlushCalledCount)
        assertEquals(PullRefreshSyncResult.WatchUnreachable, result)
    }

    @Test
    fun executeSync_whenRequestFlushFails_returnsWatchUnreachable() = runBlocking {
        val fakeRequester = FakeSyncFlushRequester(isConnected = true, requestFlushResult = false)
        val coordinator = SyncPullRefreshCoordinator(fakeRequester, timeoutMillis = 1000L)

        val result = coordinator.executeSync()

        assertEquals(1, fakeRequester.requestFlushCalledCount)
        assertEquals(PullRefreshSyncResult.WatchUnreachable, result)
    }

    @Test
    fun executeSync_whenFlushSucceedsWithWorkouts_returnsSuccess() = runBlocking {
        val fakeRequester = FakeSyncFlushRequester(
            isConnected = true,
            requestFlushResult = true,
            onFlushRequested = {
                SyncEventBus.notifyFlushCompleted(2)
            }
        )
        val coordinator = SyncPullRefreshCoordinator(fakeRequester, timeoutMillis = 2000L)

        val result = coordinator.executeSync()

        assertEquals(1, fakeRequester.requestFlushCalledCount)
        assertTrue(result is PullRefreshSyncResult.Success)
        assertEquals(2, (result as PullRefreshSyncResult.Success).itemsSyncedCount)
    }

    @Test
    fun executeSync_whenFlushSucceedsWithZeroWorkouts_returnsUpToDate() = runBlocking {
        val fakeRequester = FakeSyncFlushRequester(
            isConnected = true,
            requestFlushResult = true,
            onFlushRequested = {
                SyncEventBus.notifyFlushCompleted(0)
            }
        )
        val coordinator = SyncPullRefreshCoordinator(fakeRequester, timeoutMillis = 2000L)

        val result = coordinator.executeSync()

        assertEquals(1, fakeRequester.requestFlushCalledCount)
        assertEquals(PullRefreshSyncResult.UpToDate, result)
    }

    @Test
    fun executeSync_whenTimeoutOccurs_returnsWatchUnreachable() = runBlocking {
        val fakeRequester = FakeSyncFlushRequester(isConnected = true, requestFlushResult = true)
        // Set a very short timeout (50ms) to trigger timeout without waiting
        val coordinator = SyncPullRefreshCoordinator(fakeRequester, timeoutMillis = 50L)

        val result = coordinator.executeSync()

        assertEquals(1, fakeRequester.requestFlushCalledCount)
        assertEquals(PullRefreshSyncResult.WatchUnreachable, result)
    }

    @Test
    fun syncConstants_paths_matchProtocolSpecification() {
        assertEquals("/sync/request_flush", SyncConstants.PATH_SYNC_REQUEST_FLUSH)
        assertEquals("/sync/flush_completed", SyncConstants.PATH_SYNC_FLUSH_COMPLETED)
        assertEquals("/workout_payload_message", SyncConstants.PATH_WORKOUT_MESSAGE)
        assertEquals("/sync/request_master_data", SyncConstants.PATH_REQUEST_MASTER_DATA)
    }
}
