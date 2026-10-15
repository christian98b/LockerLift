package com.lockerlift.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.WeightUnit

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
    /**
     * Unit of the machine's weight stack scale (Issue #37): KG or LBS.
     * Weights are always stored in kg; this only governs input/display.
     */
    @ColumnInfo(name = "weight_unit")
    val weightUnit: WeightUnit = WeightUnit.KG,

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
    weightUnit = weightUnit,
    updatedAt = updatedAt
)

fun Machine.toEntity() = MachineEntity(
    id = id,
    name = name,
    targetMuscleGroup = targetMuscleGroup,
    machineSettingsNote = machineSettingsNote,
    defaultIncrementKg = defaultIncrementKg,
    defaultCadence = defaultCadence,
    weightUnit = weightUnit,
    updatedAt = updatedAt
)
