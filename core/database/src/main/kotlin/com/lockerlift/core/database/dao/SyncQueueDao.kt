package com.lockerlift.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lockerlift.core.database.entity.SyncQueueEntity
import com.lockerlift.core.model.QueueStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' OR status = 'ERROR' ORDER BY created_at ASC")
    fun getPendingQueueItemsFlow(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' OR status = 'ERROR' ORDER BY created_at ASC")
    suspend fun getPendingQueueItems(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE id = :id LIMIT 1")
    suspend fun getQueueItemById(id: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: SyncQueueEntity)

    @Update
    suspend fun updateQueueItem(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET status = :status, last_attempt_at = :lastAttemptAt, retry_count = retry_count + 1 WHERE id = :id")
    suspend fun updateAttemptStatus(id: String, status: QueueStatus, lastAttemptAt: Long)

    @Query("UPDATE sync_queue SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: QueueStatus)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteQueueItemById(id: String)

    @Query("DELETE FROM sync_queue WHERE session_id = :sessionId")
    suspend fun deleteQueueItemBySessionId(sessionId: String)
}
