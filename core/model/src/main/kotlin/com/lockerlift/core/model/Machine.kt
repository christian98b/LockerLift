package com.lockerlift.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Machine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetMuscleGroup: String,
    val machineSettingsNote: String? = null,
    val defaultIncrementKg: Float = 2.5f,
    val defaultCadence: String? = null,
    /**
     * Unit of the machine's weight stack scale (Issue #37). Stored internally
     * in kg even for LBS machines; the unit only governs user input/display.
     */
    val weightUnit: WeightUnit = WeightUnit.KG,
    val updatedAt: Long = System.currentTimeMillis()
)
