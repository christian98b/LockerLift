package com.lockerlift.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lockerlift.core.database.entity.TemplateMachineCrossRefEntity
import com.lockerlift.core.database.entity.WorkoutTemplateEntity
import com.lockerlift.core.database.model.WorkoutTemplateWithMachines
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateDao {

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE is_archived = 0 ORDER BY updated_at DESC")
    fun getAllActiveTemplatesWithMachinesFlow(): Flow<List<WorkoutTemplateWithMachines>>

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateWithMachinesById(id: String): WorkoutTemplateWithMachines?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: WorkoutTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(crossRefs: List<TemplateMachineCrossRefEntity>)

    @Query("DELETE FROM template_machine_cross_ref WHERE template_id = :templateId")
    suspend fun clearCrossRefsForTemplate(templateId: String)

    @Update
    suspend fun updateTemplate(template: WorkoutTemplateEntity)

    @Query("UPDATE workout_templates SET is_archived = 1 WHERE id = :id")
    suspend fun archiveTemplate(id: String)

    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: String)

    @Transaction
    suspend fun saveTemplateWithMachines(
        template: WorkoutTemplateEntity,
        machineIdsInOrder: List<String>
    ) {
        insertTemplate(template)
        clearCrossRefsForTemplate(template.id)
        val crossRefs = machineIdsInOrder.mapIndexed { index, machineId ->
            TemplateMachineCrossRefEntity(
                templateId = template.id,
                machineId = machineId,
                sortOrder = index
            )
        }
        if (crossRefs.isNotEmpty()) {
            insertCrossRefs(crossRefs)
        }
    }
}
