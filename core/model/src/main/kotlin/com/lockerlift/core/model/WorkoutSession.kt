package com.lockerlift.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class WorkoutSession(
    val id: String = UUID.randomUUID().toString(),
    val templateId: String? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val originDevice: String = "WEAR_OS",
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val notes: String? = null
)

@Serializable
data class SessionMachineInstance(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val machineId: String,
    val executionOrder: Int,
    val isSkipped: Boolean = false,
    val customSettingsNote: String? = null
)

@Serializable
data class WorkoutSet(
    val id: String = UUID.randomUUID().toString(),
    val sessionMachineId: String,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Float,
    val cadence: String? = null,
    val setType: SetType = SetType.NORMAL,
    val completedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SyncQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val payloadJson: String,
    val status: QueueStatus = QueueStatus.PENDING,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null
)
