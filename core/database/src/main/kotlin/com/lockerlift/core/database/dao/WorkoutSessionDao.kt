package com.lockerlift.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lockerlift.core.database.entity.SessionMachineInstanceEntity
import com.lockerlift.core.database.entity.WorkoutSessionEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.model.WorkoutSessionWithDetails
import com.lockerlift.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    @Transaction
    @Query("SELECT * FROM workout_sessions ORDER BY start_time DESC")
    fun getAllSessionsWithDetailsFlow(): Flow<List<WorkoutSessionWithDetails>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionWithDetailsById(id: String): WorkoutSessionWithDetails?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    suspend fun getActiveSessionWithDetails(): WorkoutSessionWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity)

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachineInstance(instance: SessionMachineInstanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachineInstances(instances: List<SessionMachineInstanceEntity>)

    @Update
    suspend fun updateMachineInstance(instance: SessionMachineInstanceEntity)

    @Query("DELETE FROM session_machine_instances WHERE id = :instanceId")
    suspend fun deleteMachineInstance(instanceId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(workoutSet: WorkoutSetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(workoutSets: List<WorkoutSetEntity>)

    @Update
    suspend fun updateSet(workoutSet: WorkoutSetEntity)

    @Query("DELETE FROM workout_sets WHERE id = :setId")
    suspend fun deleteSet(setId: String)

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("UPDATE workout_sessions SET sync_status = :syncStatus WHERE id = :sessionId")
    suspend fun updateSyncStatus(sessionId: String, syncStatus: SyncStatus)

    /**
     * Query completed historical sets for a machine from past finished sessions,
     * ordered by session completion time descending and set number ascending.
     */
    @Query("""
        SELECT ws.* FROM workout_sets ws
        INNER JOIN session_machine_instances smi ON ws.session_machine_id = smi.id
        INNER JOIN workout_sessions s ON smi.session_id = s.id
        WHERE smi.machine_id = :machineId AND s.end_time IS NOT NULL
        ORDER BY s.end_time DESC, ws.set_number ASC
        LIMIT 10
    """)
    suspend fun getLastCompletedSetsForMachine(machineId: String): List<WorkoutSetEntity>

    /**
     * Atomically inserts or updates a full workout session graph (used by sync receiver).
     */
    @Transaction
    suspend fun upsertFullSession(
        session: WorkoutSessionEntity,
        instances: List<SessionMachineInstanceEntity>,
        sets: List<WorkoutSetEntity>
    ) {
        insertSession(session)
        insertMachineInstances(instances)
        insertSets(sets)
    }
}
