package com.lockerlift.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MachineDao {

    @Query("SELECT * FROM machines WHERE is_deleted = 0 ORDER BY name ASC")
    fun getAllMachinesFlow(): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE is_deleted = 0 ORDER BY name ASC")
    suspend fun getAllMachines(): List<MachineEntity>

    @Query("SELECT DISTINCT target_muscle_group FROM machines WHERE is_deleted = 0 ORDER BY target_muscle_group ASC")
    fun getAllTargetMuscleGroupsFlow(): Flow<List<String>>

    @Query("SELECT * FROM machines WHERE id = :id LIMIT 1")
    suspend fun getMachineById(id: String): MachineEntity?

    @Query("SELECT * FROM machines WHERE LOWER(name) = LOWER(:name) AND is_deleted = 0 LIMIT 1")
    suspend fun getMachineByName(name: String): MachineEntity?

    /**
     * Duplicate check for soft-deleted machines (Issue #40): a deleted
     * machine no longer blocks its name for new machines.
     */
    @Query("SELECT * FROM machines WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getMachineByNameIncludingDeleted(name: String): MachineEntity?

    /**
     * Soft-deletes a machine (Issue #40): the row stays in the database so
     * historical workout sets remain readable, but it disappears from the
     * catalog and all selection lists.
     */
    @Query("UPDATE machines SET is_deleted = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteMachine(id: String, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachine(machine: MachineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachines(machines: List<MachineEntity>)

    @Update
    suspend fun updateMachine(machine: MachineEntity)

    @Delete
    suspend fun deleteMachine(machine: MachineEntity)

    @Query("DELETE FROM machines WHERE id = :id")
    suspend fun deleteMachineById(id: String)

    /**
     * Query historical sets for a machine from the latest completed workout session.
     */
    @Query("""
        SELECT ws.* FROM workout_sets ws
        INNER JOIN session_machine_instances smi ON ws.session_machine_id = smi.id
        INNER JOIN workout_sessions s ON smi.session_id = s.id
        WHERE smi.machine_id = :machineId AND s.end_time IS NOT NULL
        ORDER BY s.end_time DESC, ws.set_number ASC
    """)
    suspend fun getLastSessionSetsForMachine(machineId: String): List<WorkoutSetEntity>
}
