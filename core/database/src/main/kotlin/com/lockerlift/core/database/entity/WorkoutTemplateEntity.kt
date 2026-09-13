package com.lockerlift.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lockerlift.core.model.TemplateMachineCrossRef
import com.lockerlift.core.model.WorkoutTemplate

@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

fun WorkoutTemplateEntity.toDomainModel() = WorkoutTemplate(
    id = id,
    name = name,
    description = description,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun WorkoutTemplate.toEntity() = WorkoutTemplateEntity(
    id = id,
    name = name,
    description = description,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt
)

@Entity(
    tableName = "template_machine_cross_ref",
    primaryKeys = ["template_id", "machine_id"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machine_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["template_id"]),
        Index(value = ["machine_id"])
    ]
)
data class TemplateMachineCrossRefEntity(
    @ColumnInfo(name = "template_id")
    val templateId: String,

    @ColumnInfo(name = "machine_id")
    val machineId: String,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int
)

fun TemplateMachineCrossRefEntity.toDomainModel() = TemplateMachineCrossRef(
    templateId = templateId,
    machineId = machineId,
    sortOrder = sortOrder
)

fun TemplateMachineCrossRef.toEntity() = TemplateMachineCrossRefEntity(
    templateId = templateId,
    machineId = machineId,
    sortOrder = sortOrder
)
