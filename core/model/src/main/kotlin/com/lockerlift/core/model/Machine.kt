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
    val updatedAt: Long = System.currentTimeMillis()
)
