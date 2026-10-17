package com.lockerlift.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import com.lockerlift.core.database.entity.PlanEntryEntity
import com.lockerlift.core.database.entity.WorkoutPlanEntity

data class WorkoutPlanWithEntries(
    @Embedded val plan: WorkoutPlanEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "plan_id"
    )
    val entries: List<PlanEntryEntity>
)
