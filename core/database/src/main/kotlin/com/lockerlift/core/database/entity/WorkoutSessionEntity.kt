package com.lockerlift.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.SetType
import com.lockerlift.core.model.SyncStatus
import com.lockerlift.core.model.WorkoutSession
import com.lockerlift.core.model.WorkoutSet

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["template_id"])]
)
data class WorkoutSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "template_id")
    val templateId: String? = null,

    @ColumnInfo(name = "start_time")
    val startTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    @ColumnInfo(name = "origin_device")
    val originDevice: String = "WEAR_OS",

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,

    @ColumnInfo(name = "notes")
    val notes: String? = null
)

fun WorkoutSessionEntity.toDomainModel() = WorkoutSession(
    id = id,
    templateId = templateId,
    startTime = startTime,
    endTime = endTime,
    originDevice = originDevice,
    syncStatus = syncStatus,
    notes = notes
)

fun WorkoutSession.toEntity() = WorkoutSessionEntity(
    id = id,
    templateId = templateId,
    startTime = startTime,
    endTime = endTime,
    originDevice = originDevice,
    syncStatus = syncStatus,
    notes = notes
)

@Entity(
    tableName = "session_machine_instances",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machine_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["machine_id"])
    ]
)
data class SessionMachineInstanceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "machine_id")
    val machineId: String,

    @ColumnInfo(name = "execution_order")
    val executionOrder: Int,

    @ColumnInfo(name = "is_skipped")
    val isSkipped: Boolean = false,

    @ColumnInfo(name = "custom_settings_note")
    val customSettingsNote: String? = null
)

fun SessionMachineInstanceEntity.toDomainModel() = SessionMachineInstance(
    id = id,
    sessionId = sessionId,
    machineId = machineId,
    executionOrder = executionOrder,
    isSkipped = isSkipped,
    customSettingsNote = customSettingsNote
)

fun SessionMachineInstance.toEntity() = SessionMachineInstanceEntity(
    id = id,
    sessionId = sessionId,
    machineId = machineId,
    executionOrder = executionOrder,
    isSkipped = isSkipped,
    customSettingsNote = customSettingsNote
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionMachineInstanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_machine_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["session_machine_id"])]
)
data class WorkoutSetEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_machine_id")
    val sessionMachineId: String,

    @ColumnInfo(name = "set_number")
    val setNumber: Int,

    @ColumnInfo(name = "reps")
    val reps: Int,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Float,

    @ColumnInfo(name = "cadence")
    val cadence: String? = null,

    @ColumnInfo(name = "set_type")
    val setType: SetType = SetType.NORMAL,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long = System.currentTimeMillis()
)

fun WorkoutSetEntity.toDomainModel() = WorkoutSet(
    id = id,
    sessionMachineId = sessionMachineId,
    setNumber = setNumber,
    reps = reps,
    weightKg = weightKg,
    cadence = cadence,
    setType = setType,
    completedAt = completedAt
)

fun WorkoutSet.toEntity() = WorkoutSetEntity(
    id = id,
    sessionMachineId = sessionMachineId,
    setNumber = setNumber,
    reps = reps,
    weightKg = weightKg,
    cadence = cadence,
    setType = setType,
    completedAt = completedAt
)
