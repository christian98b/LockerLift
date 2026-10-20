package com.lockerlift.mobile.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the SettingsViewModel (Issue #56): sync queue state
 * exposure and the manual workout-sync result mapping previously inlined
 * in the composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    private lateinit var database: LockerLiftDatabase
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        database = LockerLiftDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return SettingsViewModel(application = app, database = database)
    }

    @Test
    fun testPendingQueueCount_reflectsQueue() = runTest(testDispatcher) {
        database.syncQueueDao().insertQueueItem(
            SyncQueueEntity(id = "q-1", sessionId = "s-1", payloadJson = "{}")
        )
        val viewModel = createViewModel()
        assertEquals(1, viewModel.pendingQueueCount.first { it > 0 })
    }

    @Test
    fun testSyncWorkouts_reportsNoCompanionWithoutWatch() = runTest(testDispatcher) {
        var result: WorkoutSyncUiResult? = null
        val viewModel = createViewModel()
        viewModel.syncWorkouts { result = it }
        viewModel.awaitActions()
        assertTrue(
            "Expected NoCompanion or Error in the test environment, got $result",
            result is WorkoutSyncUiResult.NoCompanion || result is WorkoutSyncUiResult.Error
        )
    }

    @Test
    fun testSyncFlags_resetAfterSync() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.syncWorkouts { }
        viewModel.awaitActions()
        assertFalse(viewModel.isSyncingWorkouts.value)
        assertFalse(viewModel.isSyncingMasterData.value)
    }
}
