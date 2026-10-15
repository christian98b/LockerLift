package com.lockerlift.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lockerlift.core.model.Machine

@Entity(tableName = "machines")
data class MachineEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "target_muscle_group")
    val targetMuscleGroup: String,

    @ColumnInfo(name = "machine_settings_note")
    val machineSettingsNote: String? = null,

    @ColumnInfo(name = "default_increment_kg")
    val defaultIncrementKg: Float = 2.5f,

    @ColumnInfo(name = "default_cadence")
    val defaultCadence: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Soft-delete flag (Issue #40): a deleted machine disappears from the
     * catalog and selection lists but stays in the database so historical
     * workout sets remain readable (AK 2.2.2 semantics).
     */
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)

fun MachineEntity.toDomainModel() = Machine(
    id = id,
    name = name,
    targetMuscleGroup = targetMuscleGroup,
    machineSettingsNote = machineSettingsNote,
    defaultIncrementKg = defaultIncrementKg,
    defaultCadence = defaultCadence,
    updatedAt = updatedAt
)

fun Machine.toEntity() = MachineEntity(
    id = id,
    name = name,
    targetMuscleGroup = targetMuscleGroup,
    machineSettingsNote = machineSettingsNote,
    defaultIncrementKg = defaultIncrementKg,
    defaultCadence = defaultCadence,
    updatedAt = updatedAt
)
