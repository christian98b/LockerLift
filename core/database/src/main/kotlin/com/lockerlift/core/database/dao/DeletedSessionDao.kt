package com.lockerlift.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lockerlift.core.database.entity.DeletedSessionEntity

/**
 * DAO for managing tombstone entries of deleted workout sessions.
 * Used for zombie workout detection during synchronization.
 */
@Dao
interface DeletedSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deletedSession: DeletedSessionEntity)

    @Query("SELECT * FROM deleted_sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun getBySessionId(sessionId: String): DeletedSessionEntity?

    @Query("SELECT * FROM deleted_sessions WHERE deleted_at < :cutoffTimestamp")
    suspend fun getExpiredSessions(cutoffTimestamp: Long): List<DeletedSessionEntity>

    @Query("DELETE FROM deleted_sessions WHERE session_id = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM deleted_sessions WHERE deleted_at < :cutoffTimestamp")
    suspend fun deleteExpiredSessions(cutoffTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM deleted_sessions")
    suspend fun getCount(): Int

    @Query("SELECT * FROM deleted_sessions ORDER BY deleted_at DESC LIMIT :limit")
    suspend fun getRecentDeletedSessions(limit: Int): List<DeletedSessionEntity>
}
