package com.lockerlift.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lockerlift.core.model.DeletedSession

/**
 * Tombstone table for tracking deleted workout sessions.
 * Prevents zombie workouts from being re-synced after deletion.
 * Entries are automatically cleaned up after TTL (default: 30 days).
 */
@Entity(tableName = "deleted_sessions")
data class DeletedSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "origin_device")
    val originDevice: String, // "MOBILE" or "WEAR_OS"

    @ColumnInfo(name = "ttl_days")
    val ttlDays: Int = 30 // Default: 30 days retention
)

fun DeletedSessionEntity.toDomainModel() = DeletedSession(
    sessionId = sessionId,
    deletedAt = deletedAt,
    originDevice = originDevice,
    ttlDays = ttlDays
)

fun DeletedSession.toEntity() = DeletedSessionEntity(
    sessionId = sessionId,
    deletedAt = deletedAt,
    originDevice = originDevice,
    ttlDays = ttlDays
)
