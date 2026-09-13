package com.lockerlift.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lockerlift.core.model.QueueStatus
import com.lockerlift.core.model.SyncQueueItem

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "status")
    val status: QueueStatus = QueueStatus.PENDING,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null
)

fun SyncQueueEntity.toDomainModel() = SyncQueueItem(
    id = id,
    sessionId = sessionId,
    payloadJson = payloadJson,
    status = status,
    retryCount = retryCount,
    createdAt = createdAt,
    lastAttemptAt = lastAttemptAt
)

fun SyncQueueItem.toEntity() = SyncQueueEntity(
    id = id,
    sessionId = sessionId,
    payloadJson = payloadJson,
    status = status,
    retryCount = retryCount,
    createdAt = createdAt,
    lastAttemptAt = lastAttemptAt
)
