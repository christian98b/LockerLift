package com.lockerlift.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class WorkoutTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class TemplateMachineCrossRef(
    val templateId: String,
    val machineId: String,
    val sortOrder: Int
)
