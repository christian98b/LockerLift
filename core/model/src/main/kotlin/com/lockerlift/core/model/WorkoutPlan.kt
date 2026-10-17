package com.lockerlift.core.model

import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A weekly training plan (Issue #51): defines which program is planned for
 * each day of a multi-week rotation that repeats in a cycle.
 *
 * The plan is anchored to a start date; the current week index within the
 * rotation is derived from the elapsed time since that anchor.
 */
@Serializable
data class WorkoutPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /**
     * Number of weeks in one rotation cycle (e.g. 1 = same every week,
     * 4 = different plan for weeks 1-4, then repeat).
     */
    val cycleLengthWeeks: Int = 1,
    /**
     * Anchor date (epoch millis, midnight local time) of week 1, day 1.
     * The first day of the plan is the Monday of this anchor week.
     */
    val startDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * One planned day inside a plan (Issue #51): week index + day of week →
 * planned program. The planned program is either a gym workout template,
 * an activity type (e.g. Running), or a free-text label.
 *
 * @param weekIndex 0-based index of the week within the rotation cycle.
 * @param dayOfWeek ISO-8601 day number: 1 = Monday ... 7 = Sunday.
 */
@Serializable
data class PlanEntry(
    val id: String = UUID.randomUUID().toString(),
    val planId: String,
    val weekIndex: Int,
    val dayOfWeek: Int,
    val templateId: String? = null,
    val activityTypeId: String? = null,
    val label: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Pure plan scheduling logic (Issue #51): resolves the current week index
 * and day of week from the plan's anchor date and cycle length.
 */
object WorkoutPlanLogic {

    private val MILLIS_PER_WEEK = TimeUnit.DAYS.toMillis(7)

    /**
     * ISO-8601 day number for a timestamp: 1 = Monday ... 7 = Sunday.
     */
    fun isoDayOfWeek(timestampMillis: Long): Int {
        // 1970-01-01 was a Thursday (ISO day 4)
        val days = TimeUnit.MILLISECONDS.toDays(timestampMillis)
        return (((days + 3) % 7) + 1L).toInt()
    }

    /**
     * 0-based week index within the rotation cycle for a timestamp,
     * based on the Monday of the plan's anchor week.
     */
    fun weekIndex(timestampMillis: Long, plan: WorkoutPlan): Int {
        val weeksElapsed = (timestampMillis - plan.startDate) / MILLIS_PER_WEEK
        val cycle = plan.cycleLengthWeeks.coerceAtLeast(1)
        val index = ((weeksElapsed % cycle) + cycle) % cycle
        return index.toInt()
    }

    /**
     * The plan entry active for a given timestamp, or null when nothing is
     * planned for the current day.
     */
    fun entryFor(
        timestampMillis: Long,
        plan: WorkoutPlan,
        entries: List<PlanEntry>
    ): PlanEntry? {
        val week = weekIndex(timestampMillis, plan)
        val day = isoDayOfWeek(timestampMillis)
        return entries.firstOrNull { it.weekIndex == week && it.dayOfWeek == day }
    }
}
