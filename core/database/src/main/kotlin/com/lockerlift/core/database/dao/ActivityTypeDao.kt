package com.lockerlift.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lockerlift.core.database.entity.ActivityTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityTypeDao {

    @Query("SELECT * FROM activity_types ORDER BY name ASC")
    fun getAllActivityTypesFlow(): Flow<List<ActivityTypeEntity>>

    @Query("SELECT * FROM activity_types ORDER BY name ASC")
    suspend fun getAllActivityTypes(): List<ActivityTypeEntity>

    @Query("SELECT * FROM activity_types WHERE id = :id LIMIT 1")
    suspend fun getActivityTypeById(id: String): ActivityTypeEntity?

    @Query("SELECT * FROM activity_types WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getActivityTypeByName(name: String): ActivityTypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityType(type: ActivityTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityTypes(types: List<ActivityTypeEntity>)

    @Update
    suspend fun updateActivityType(type: ActivityTypeEntity)

    @Query("DELETE FROM activity_types WHERE id = :id AND is_builtin = 0")
    suspend fun deleteActivityTypeById(id: String)
}
