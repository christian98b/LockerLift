package com.lockerlift.mobile.ui.catalog

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.SessionMachineInstanceEntity
import com.lockerlift.core.database.entity.WorkoutSessionEntity
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.Machine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the machine delete workflow of the CatalogViewModel
 * (Issue #40): soft-delete keeps history, blocks on active workouts and
 * removes the machine from template cross-refs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogViewModelDeleteTest {

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

    private fun createViewModel(): CatalogViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return CatalogViewModel(application = app, database = database)
    }

    @Test
    fun testDeleteMachine_softDeletesAndKeepsRow() = runTest(testDispatcher) {
        database.machineDao().insertMachine(
            Machine(id = "m-1", name = "Bench Press", targetMuscleGroup = "Chest").toEntity()
        )
        val viewModel = createViewModel()
        var result: MachineDeleteResult? = null

        viewModel.deleteMachine(database.machineDao().getMachineById("m-1")!!) {
            result = it
        }
        viewModel.awaitActions()

        assertEquals(MachineDeleteResult.SUCCESS, result)
        assertTrueOrEmpty(database.machineDao().getAllMachines())
        assertNotNull(database.machineDao().getMachineById("m-1"))
    }

    @Test
    fun testDeleteMachine_blockedWhenMachineInActiveWorkout() = runTest(testDispatcher) {
        database.machineDao().insertMachine(
            Machine(id = "m-1", name = "Bench Press", targetMuscleGroup = "Chest").toEntity()
        )
        database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(id = "s-1", endTime = null)
        )
        database.workoutSessionDao().insertMachineInstance(
            SessionMachineInstanceEntity(
                id = "smi-1",
                sessionId = "s-1",
                machineId = "m-1",
                executionOrder = 0
            )
        )
        val viewModel = createViewModel()
        var result: MachineDeleteResult? = null

        viewModel.deleteMachine(database.machineDao().getMachineById("m-1")!!) {
            result = it
        }
        viewModel.awaitActions()

        assertEquals(MachineDeleteResult.BLOCKED_ACTIVE_WORKOUT, result)
        assertNull(database.machineDao().getMachineByName("Bench Press")?.takeIf { it.isDeleted.not() })
        assertNotNull(database.machineDao().getMachineById("m-1"))
        assertEquals("m-1", database.machineDao().getMachineById("m-1")!!.id)
    }

    private fun assertTrueOrEmpty(machines: List<com.lockerlift.core.database.entity.MachineEntity>) {
        assertEquals(0, machines.size)
    }
}
