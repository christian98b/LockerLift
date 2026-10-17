package com.lockerlift.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lockerlift.core.model.PlanEntry
import com.lockerlift.core.model.WorkoutPlan

/**
 * A weekly training plan (Issue #51): multi-week rotation that repeats in
 * a cycle, anchored to a start date.
 */
@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "cycle_length_weeks")
    val cycleLengthWeeks: Int = 1,
    @ColumnInfo(name = "start_date")
    val startDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

fun WorkoutPlanEntity.toDomainModel() = WorkoutPlan(
    id = id,
    name = name,
    cycleLengthWeeks = cycleLengthWeeks,
    startDate = startDate,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun WorkoutPlan.toEntity() = WorkoutPlanEntity(
    id = id,
    name = name,
    cycleLengthWeeks = cycleLengthWeeks,
    startDate = startDate,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * One planned day in a plan (Issue #51): week index + ISO day of week →
 * planned program (workout template, activity type, or free-text label).
 * Template/activity references are intentionally NOT foreign keys so the
 * plan survives later template/activity deletions (local-first soft refs).
 */
@Entity(
    tableName = "plan_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["plan_id"]),
        Index(value = ["plan_id", "week_index", "day_of_week"], unique = true)
    ]
)
data class PlanEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "plan_id")
    val planId: String,
    @ColumnInfo(name = "week_index")
    val weekIndex: Int,
    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: Int,
    @ColumnInfo(name = "template_id")
    val templateId: String? = null,
    @ColumnInfo(name = "activity_type_id")
    val activityTypeId: String? = null,
    @ColumnInfo(name = "label")
    val label: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

fun PlanEntryEntity.toDomainModel() = PlanEntry(
    id = id,
    planId = planId,
    weekIndex = weekIndex,
    dayOfWeek = dayOfWeek,
    templateId = templateId,
    activityTypeId = activityTypeId,
    label = label,
    createdAt = createdAt
)

fun PlanEntry.toEntity() = PlanEntryEntity(
    id = id,
    planId = planId,
    weekIndex = weekIndex,
    dayOfWeek = dayOfWeek,
    templateId = templateId,
    activityTypeId = activityTypeId,
    label = label,
    createdAt = createdAt
)
