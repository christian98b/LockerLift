package com.lockerlift.core.database.dao

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.PlanEntry
import com.lockerlift.core.model.WorkoutPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
 * Unit tests for the workout plan DAO (Issue #51): plan CRUD, entry
 * replacement, relation loading, and cascade deletion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutPlanDaoTest {

    private lateinit var database: LockerLiftDatabase

    @Before
    fun setup() {
        database = LockerLiftDatabase.createInMemory(
            ApplicationProvider.getApplicationContext<Application>()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun plan() = WorkoutPlan(
        id = "plan-1",
        name = "4-Week Rotation",
        cycleLengthWeeks = 4,
        startDate = 1_700_000_000_000L
    )

    @Test
    fun testInsertPlanAndEntries_relationLoads() = runTest {
        val dao = database.workoutPlanDao()
        val expected = plan()
        dao.insertPlan(expected.toEntity())
        dao.insertEntries(
            listOf(
                PlanEntry(
                    id = "e1", planId = "plan-1", weekIndex = 0, dayOfWeek = 1,
                    templateId = "t-upper"
                ),
                PlanEntry(
                    id = "e2", planId = "plan-1", weekIndex = 0, dayOfWeek = 3,
                    activityTypeId = "type-running"
                ),
                PlanEntry(
                    id = "e3", planId = "plan-1", weekIndex = 1, dayOfWeek = 2,
                    label = "Yoga"
                )
            ).map { it.toEntity() }
        )

        val loaded = dao.getPlanWithEntriesById("plan-1")
        assertNotNull(loaded)
        assertEquals(expected, loaded!!.plan.toDomainModel())
        assertEquals(3, loaded.entries.size)
        assertEquals(setOf("e1", "e2", "e3"), loaded.entries.map { it.id }.toSet())
    }

    @Test
    fun testGetAllPlansWithEntriesFlow() = runTest {
        val dao = database.workoutPlanDao()
        dao.insertPlan(plan().toEntity())
        val other = plan().copy(id = "plan-2", name = "Second")
        dao.insertPlan(other.toEntity())

        val plans = dao.getAllPlansWithEntriesFlow().first()
        assertEquals(2, plans.size)
        assertTrue(plans.all { it.entries.isEmpty() })
    }

    @Test
    fun testReplacePlanEntries() = runTest {
        val dao = database.workoutPlanDao()
        dao.insertPlan(plan().toEntity())
        dao.insertEntry(
            PlanEntry(id = "old", planId = "plan-1", weekIndex = 0, dayOfWeek = 1).toEntity()
        )

        dao.replacePlanEntries(
            "plan-1",
            listOf(
                PlanEntry(id = "n1", planId = "plan-1", weekIndex = 0, dayOfWeek = 1).toEntity(),
                PlanEntry(id = "n2", planId = "plan-1", weekIndex = 0, dayOfWeek = 5).toEntity()
            )
        )

        val entries = dao.getEntriesForPlan("plan-1")
        assertEquals(setOf("n1", "n2"), entries.map { it.id }.toSet())
    }

    @Test
    fun testUniqueIndexPerPlanWeekDay() = runTest {
        val dao = database.workoutPlanDao()
        dao.insertPlan(plan().toEntity())
        // Same (plan, week, day) with REPLACE strategy: second insert wins
        dao.insertEntry(
            PlanEntry(id = "a", planId = "plan-1", weekIndex = 0, dayOfWeek = 1, label = "first").toEntity()
        )
        dao.insertEntry(
            PlanEntry(id = "b", planId = "plan-1", weekIndex = 0, dayOfWeek = 1, label = "second").toEntity()
        )
        val entries = dao.getEntriesForPlan("plan-1")
        assertEquals(1, entries.size)
        assertEquals("second", entries.first().label)
    }

    @Test
    fun testDeletePlan_cascadesToEntries() = runTest {
        val dao = database.workoutPlanDao()
        dao.insertPlan(plan().toEntity())
        dao.insertEntry(
            PlanEntry(id = "e1", planId = "plan-1", weekIndex = 0, dayOfWeek = 1).toEntity()
        )

        dao.deletePlanById("plan-1")
        assertNull(dao.getPlanById("plan-1"))
        assertTrue(dao.getEntriesForPlan("plan-1").isEmpty())
    }

    @Test
    fun testDeleteEntryById() = runTest {
        val dao = database.workoutPlanDao()
        dao.insertPlan(plan().toEntity())
        dao.insertEntry(
            PlanEntry(id = "e1", planId = "plan-1", weekIndex = 0, dayOfWeek = 1).toEntity()
        )

        dao.deleteEntryById("e1")
        assertTrue(dao.getEntriesForPlan("plan-1").isEmpty())
    }

    @Test
    fun testCountPlans() = runTest {
        val dao = database.workoutPlanDao()
        assertEquals(0, dao.countPlans())
        dao.insertPlan(plan().toEntity())
        assertEquals(1, dao.countPlans())
    }
}
