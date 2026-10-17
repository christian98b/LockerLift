package com.lockerlift.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the workout plan scheduling logic (Issue #51).
 */
class WorkoutPlanTest {

    private fun millis(dayOfEra: Long): Long = dayOfEra * 86_400_000L

    @Test
    fun testIsoDayOfWeek_epochIsThursday() {
        // 1970-01-01 was a Thursday (ISO day 4)
        assertEquals(4, WorkoutPlanLogic.isoDayOfWeek(0L))
        // 1970-01-05 was a Monday (ISO day 1)
        assertEquals(1, WorkoutPlanLogic.isoDayOfWeek(millis(4)))
        // 1970-01-04 was a Sunday (ISO day 7)
        assertEquals(7, WorkoutPlanLogic.isoDayOfWeek(millis(3)))
    }

    @Test
    fun testWeekIndex_singleWeekCycle_alwaysZero() {
        val plan = WorkoutPlan(id = "p1", name = "Plan", cycleLengthWeeks = 1, startDate = 0L)
        assertEquals(0, WorkoutPlanLogic.weekIndex(0L, plan))
        assertEquals(0, WorkoutPlanLogic.weekIndex(millis(200), plan))
    }

    @Test
    fun testWeekIndex_fourWeekCycle() {
        val plan = WorkoutPlan(id = "p1", name = "Plan", cycleLengthWeeks = 4, startDate = 0L)
        assertEquals(0, WorkoutPlanLogic.weekIndex(millis(3), plan))
        assertEquals(1, WorkoutPlanLogic.weekIndex(millis(7), plan))
        assertEquals(2, WorkoutPlanLogic.weekIndex(millis(14), plan))
        assertEquals(3, WorkoutPlanLogic.weekIndex(millis(21), plan))
        // cycle repeats
        assertEquals(0, WorkoutPlanLogic.weekIndex(millis(28), plan))
        assertEquals(1, WorkoutPlanLogic.weekIndex(millis(35), plan))
    }

    @Test
    fun testWeekIndex_beforeStartDate_wrapsBackwards() {
        val plan = WorkoutPlan(id = "p1", name = "Plan", cycleLengthWeeks = 4, startDate = millis(28))
        // 14 days before start = 2 weeks back = week 2 of the cycle
        assertEquals(2, WorkoutPlanLogic.weekIndex(millis(14), plan))
    }

    @Test
    fun testEntryFor_findsCurrentDay() {
        val plan = WorkoutPlan(id = "p1", name = "Plan", cycleLengthWeeks = 2, startDate = 0L)
        // millis(4): Monday of week 1 (week index 1)
        val mondayWeek2 = PlanEntry(
            id = "e1", planId = "p1", weekIndex = 1, dayOfWeek = 1,
            templateId = "t-upper"
        )
        val otherDay = PlanEntry(
            id = "e2", planId = "p1", weekIndex = 0, dayOfWeek = 3,
            templateId = "t-lower"
        )
        val found = WorkoutPlanLogic.entryFor(millis(4), plan, listOf(otherDay, mondayWeek2))
        assertEquals("e1", found?.id)
    }

    @Test
    fun testEntryFor_nothingPlannedToday() {
        val plan = WorkoutPlan(id = "p1", name = "Plan", cycleLengthWeeks = 1, startDate = 0L)
        val entry = PlanEntry(id = "e1", planId = "p1", weekIndex = 0, dayOfWeek = 2)
        assertNull(WorkoutPlanLogic.entryFor(millis(4), plan, listOf(entry)))
    }
}
