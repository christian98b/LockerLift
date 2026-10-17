package com.lockerlift.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lockerlift.core.database.entity.PlanEntryEntity
import com.lockerlift.core.database.entity.WorkoutPlanEntity
import com.lockerlift.core.database.model.WorkoutPlanWithEntries
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutPlanDao {

    @Transaction
    @Query("SELECT * FROM workout_plans ORDER BY created_at ASC")
    fun getAllPlansWithEntriesFlow(): Flow<List<WorkoutPlanWithEntries>>

    @Query("SELECT * FROM workout_plans ORDER BY created_at ASC")
    suspend fun getAllPlans(): List<WorkoutPlanEntity>

    @Transaction
    @Query("SELECT * FROM workout_plans WHERE id = :id LIMIT 1")
    suspend fun getPlanWithEntriesById(id: String): WorkoutPlanWithEntries?

    @Query("SELECT * FROM workout_plans WHERE id = :id LIMIT 1")
    suspend fun getPlanById(id: String): WorkoutPlanEntity?

    @Query("SELECT * FROM plan_entries WHERE plan_id = :planId")
    suspend fun getEntriesForPlan(planId: String): List<PlanEntryEntity>

    @Query("SELECT COUNT(*) FROM workout_plans")
    suspend fun countPlans(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WorkoutPlanEntity)

    @Update
    suspend fun updatePlan(plan: WorkoutPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: PlanEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<PlanEntryEntity>)

    @Query("DELETE FROM plan_entries WHERE id = :entryId")
    suspend fun deleteEntryById(entryId: String)

    @Query("DELETE FROM plan_entries WHERE plan_id = :planId")
    suspend fun deleteEntriesForPlan(planId: String)

    @Query("DELETE FROM workout_plans WHERE id = :planId")
    suspend fun deletePlanById(planId: String)

    /**
     * Atomically replaces all entries of a plan (Issue #51).
     */
    @Transaction
    suspend fun replacePlanEntries(planId: String, entries: List<PlanEntryEntity>) {
        deleteEntriesForPlan(planId)
        insertEntries(entries)
    }
}
