package com.lockerlift.mobile.ui.history

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.SessionMachineInstanceEntity
import com.lockerlift.core.database.entity.WorkoutSessionEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.model.WorkoutSession
import com.lockerlift.core.sync.WorkoutSessionPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the HistoryViewModel (Issue #56): the deletion cascade
 * (tombstone, session delete, sync queue cleanup + delete action) and the
 * full-graph session edit previously inlined in the composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryViewModelTest {

    private lateinit var database: LockerLiftDatabase
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(),
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        database = LockerLiftDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HistoryViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return HistoryViewModel(application = app, database = database)
    }

    private suspend fun insertSession(sessionId: String) {
        database.machineDao().insertMachine(
            MachineEntity(id = "m-1", name = "Bench", targetMuscleGroup = "Chest")
        )
        database.workoutSessionDao().upsertFullSession(
            session = WorkoutSessionEntity(
                id = sessionId,
                startTime = 1000L,
                endTime = 2000L,
                originDevice = "MOBILE"
            ),
            instances = listOf(
                SessionMachineInstanceEntity(
                    id = "i-1",
                    sessionId = sessionId,
                    machineId = "m-1",
                    executionOrder = 0
                )
            ),
            sets = listOf(
                WorkoutSetEntity(
                    id = "s-1",
                    sessionMachineId = "i-1",
                    setNumber = 1,
                    weightKg = 60f,
                    reps = 10
                )
            )
        )
    }

    @Test
    fun testDeleteSession_cascadesAndQueuesDeleteAction() = runTest(testDispatcher) {
        insertSession("session-1")
        database.syncQueueDao().insertQueueItem(
            com.lockerlift.core.database.entity.SyncQueueEntity(
                id = "q-1",
                sessionId = "session-1",
                payloadJson = "{}"
            )
        )

        val viewModel = createViewModel()
        viewModel.deleteSession("session-1")
        viewModel.awaitActions()

        assertNull(database.workoutSessionDao().getSessionWithDetailsById("session-1"))
        assertNotNull(database.deletedSessionDao().getBySessionId("session-1"))
        val pending = database.syncQueueDao().getPendingQueueItems()
        assertEquals(1, pending.size)
        assertEquals("session-1", pending.first().sessionId)
        assertEquals("DELETE", pending.first().payloadJson)
    }

    @Test
    fun testSaveSession_replacesPendingSyncItem() = runTest(testDispatcher) {
        insertSession("session-2")
        database.syncQueueDao().insertQueueItem(
            com.lockerlift.core.database.entity.SyncQueueEntity(
                id = "q-old",
                sessionId = "session-2",
                payloadJson = "{}"
            )
        )
        val session = WorkoutSession(id = "session-2", startTime = 1000L, endTime = 3000L)

        val viewModel = createViewModel()
        viewModel.saveSession(
            session = WorkoutSessionEntity(
                id = "session-2",
                startTime = 1000L,
                endTime = 3000L,
                originDevice = "MOBILE",
                notes = "updated"
            ),
            instances = listOf(
                SessionMachineInstanceEntity(
                    id = "i-1",
                    sessionId = "session-2",
                    machineId = "m-1",
                    executionOrder = 0
                )
            ),
            sets = listOf(
                WorkoutSetEntity(
                    id = "s-1",
                    sessionMachineId = "i-1",
                    setNumber = 1,
                    weightKg = 80f,
                    reps = 5
                )
            ),
            payload = WorkoutSessionPayload(
                session = session,
                templateName = null,
                machineInstances = emptyList()
            ),
            templateName = null
        )
        viewModel.awaitActions()

        val stored = database.workoutSessionDao().getSessionWithDetailsById("session-2")
        assertNotNull(stored)
        assertEquals("updated", stored!!.session.notes)
        val pending = database.syncQueueDao().getPendingQueueItems()
        assertEquals(1, pending.size)
        assertTrue(pending.first().payloadJson.isNotEmpty())
    }

    @Test
    fun testSessionsFlow_exposesSessions() = runTest(testDispatcher) {
        insertSession("session-3")
        val viewModel = createViewModel()
        assertEquals(1, viewModel.sessions.first { it.isNotEmpty() }.size)
        assertEquals(1, viewModel.machines.first { it.isNotEmpty() }.size)
    }
}
