package com.lockerlift.mobile.ui.plan

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.model.PlanEntry
import com.lockerlift.core.model.WorkoutPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the WorkoutPlanViewModel (Issue #56): plan save with
 * entry replacement and plan deletion previously inlined in the composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutPlanViewModelTest {

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

    private fun createViewModel(): WorkoutPlanViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return WorkoutPlanViewModel(application = app, database = database)
    }

    @Test
    fun testSavePlan_insertsPlanAndEntries() = runTest(testDispatcher) {
        val plan = WorkoutPlan(id = "p-1", name = "Split", cycleLengthWeeks = 2)
        val entries = listOf(
            PlanEntry(id = "e-1", planId = "p-1", weekIndex = 0, dayOfWeek = 1, label = "Push"),
            PlanEntry(id = "e-2", planId = "p-1", weekIndex = 1, dayOfWeek = 3, label = "Pull")
        )

        createViewModel().savePlan(plan, entries)

        val stored = database.workoutPlanDao().getPlanWithEntriesById("p-1")
        assertEquals("Split", stored!!.plan.name)
        assertEquals(2, stored.entries.size)
        assertEquals(setOf("e-1", "e-2"), stored.entries.map { it.id }.toSet())
    }

    @Test
    fun testSavePlan_replacesEntriesOnUpdate() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.savePlan(
            WorkoutPlan(id = "p-1", name = "Split", cycleLengthWeeks = 1),
            listOf(PlanEntry(id = "e-1", planId = "p-1", weekIndex = 0, dayOfWeek = 1, label = "Push"))
        )
        viewModel.savePlan(
            WorkoutPlan(id = "p-1", name = "Split", cycleLengthWeeks = 1),
            listOf(
                PlanEntry(id = "e-2", planId = "p-1", weekIndex = 0, dayOfWeek = 2, label = "Pull"),
                PlanEntry(id = "e-3", planId = "p-1", weekIndex = 0, dayOfWeek = 4, label = "Legs")
            )
        )

        val stored = database.workoutPlanDao().getPlanWithEntriesById("p-1")
        assertEquals(setOf("e-2", "e-3"), stored!!.entries.map { it.id }.toSet())
    }

    @Test
    fun testDeletePlan_removesPlanAndEntries() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.savePlan(
            WorkoutPlan(id = "p-1", name = "Split", cycleLengthWeeks = 1),
            listOf(PlanEntry(id = "e-1", planId = "p-1", weekIndex = 0, dayOfWeek = 1, label = "Push"))
        )

        viewModel.deletePlan("p-1")

        assertNull(database.workoutPlanDao().getPlanById("p-1"))
        assertTrue(database.workoutPlanDao().getEntriesForPlan("p-1").isEmpty())
    }
}
