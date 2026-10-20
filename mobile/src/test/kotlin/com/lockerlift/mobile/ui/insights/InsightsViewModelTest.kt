package com.lockerlift.mobile.ui.insights

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.SessionMachineInstanceEntity
import com.lockerlift.core.database.entity.WorkoutSessionEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the InsightsViewModel (Issue #56): verifies that the
 * session history is exposed as pre-mapped snapshots instead of direct
 * DAO access in the composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InsightsViewModelTest {

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

    @Test
    fun testSnapshots_emptyByDefault() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = InsightsViewModel(application = app, database = database)
        assertTrue(viewModel.snapshots.value.isEmpty())
    }

    @Test
    fun testSnapshots_mapsSessionsFromDatabase() = runTest(testDispatcher) {
        database.machineDao().insertMachine(
            MachineEntity(id = "m-1", name = "Bench", targetMuscleGroup = "Chest")
        )
        database.workoutSessionDao().upsertFullSession(
            session = WorkoutSessionEntity(
                id = "s-1",
                startTime = 1000L,
                endTime = 2000L,
                originDevice = "MOBILE"
            ),
            instances = listOf(
                SessionMachineInstanceEntity(
                    id = "i-1",
                    sessionId = "s-1",
                    machineId = "m-1",
                    executionOrder = 0
                )
            ),
            sets = listOf(
                WorkoutSetEntity(
                    id = "set-1",
                    sessionMachineId = "i-1",
                    setNumber = 1,
                    weightKg = 60f,
                    reps = 10
                )
            )
        )

        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = InsightsViewModel(application = app, database = database)

        val snapshots = viewModel.snapshots.value
        assertEquals(1, snapshots.size)
        assertEquals("s-1", snapshots.first().sessionId)
        assertEquals(600f, snapshots.first().machineInstances.sumOf { it.volumeKg.toDouble() }.toFloat(), 0.01f)
    }
}
