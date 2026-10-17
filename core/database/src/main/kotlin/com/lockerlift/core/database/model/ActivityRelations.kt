package com.lockerlift.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import com.lockerlift.core.database.entity.ActivityEntryEntity
import com.lockerlift.core.database.entity.ActivityTypeEntity

data class ActivityEntryWithType(
    @Embedded val entry: ActivityEntryEntity,
    @Relation(
        parentColumn = "activity_type_id",
        entityColumn = "id"
    )
    val type: ActivityTypeEntity?
)
