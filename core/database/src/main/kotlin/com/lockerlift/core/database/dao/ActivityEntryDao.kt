package com.lockerlift.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lockerlift.core.database.entity.ActivityEntryEntity
import com.lockerlift.core.database.model.ActivityEntryWithType
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityEntryDao {

    @androidx.room.Transaction
    @Query("SELECT * FROM activity_entries ORDER BY date DESC")
    fun getAllEntriesWithTypeFlow(): Flow<List<ActivityEntryWithType>>

    @androidx.room.Transaction
    @Query("SELECT * FROM activity_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryWithTypeById(id: String): ActivityEntryWithType?

    @Query("SELECT * FROM activity_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: String): ActivityEntryEntity?

    @Query("SELECT * FROM activity_entries WHERE activity_type_id = :typeId")
    suspend fun getEntriesByTypeId(typeId: String): List<ActivityEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: ActivityEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<ActivityEntryEntity>)

    @Query("SELECT * FROM activity_entries")
    suspend fun getAllActivityEntries(): List<ActivityEntryEntity>

    @Update
    suspend fun updateEntry(entry: ActivityEntryEntity)

    @Query("DELETE FROM activity_entries WHERE id = :id")
    suspend fun deleteEntryById(id: String)
}
