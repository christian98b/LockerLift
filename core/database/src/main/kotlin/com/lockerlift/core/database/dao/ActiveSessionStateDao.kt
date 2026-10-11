package com.lockerlift.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lockerlift.core.database.entity.ActiveSessionStateEntity

@Dao
interface ActiveSessionStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ActiveSessionStateEntity)

    @Query("SELECT * FROM active_session_state WHERE id = ${ActiveSessionStateEntity.ACTIVE_ROW_ID} LIMIT 1")
    suspend fun get(): ActiveSessionStateEntity?

    @Query("DELETE FROM active_session_state")
    suspend fun clear()
}
