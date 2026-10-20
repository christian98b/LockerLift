package com.lockerlift.mobile.ui.plan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.database.model.WorkoutPlanWithEntries
import com.lockerlift.core.model.ActivityType
import com.lockerlift.core.model.PlanEntry
import com.lockerlift.core.model.WorkoutPlan
import com.lockerlift.core.model.WorkoutTemplate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the training plan screen (Issue #56): exposes plans,
 * templates and activity types as flows and owns plan save/delete side
 * effects that previously lived in the composable.
 */
class WorkoutPlanViewModel(
    application: Application,
    private val database: LockerLiftDatabase
) : AndroidViewModel(application) {

    private val planDao = database.workoutPlanDao()
    private val templateDao = database.workoutTemplateDao()
    private val activityTypeDao = database.activityTypeDao()

    val plans: StateFlow<List<WorkoutPlanWithEntries>> =
        planDao.getAllPlansWithEntriesFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val templates: StateFlow<List<WorkoutTemplate>> =
        templateDao.getAllActiveTemplatesWithMachinesFlow()
            .map { list -> list.map { it.template.toDomainModel() } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activityTypes: StateFlow<List<ActivityType>> =
        activityTypeDao.getAllActivityTypesFlow()
            .map { list -> list.map { it.toDomainModel() } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Inserts or updates the plan and atomically replaces its entries.
     */
    fun savePlan(plan: WorkoutPlan, entries: List<PlanEntry>) {
        viewModelScope.launch {
            planDao.insertPlan(plan.toEntity())
            planDao.replacePlanEntries(plan.id, entries.map { it.toEntity() })
        }
    }

    fun deletePlan(planId: String) {
        viewModelScope.launch {
            planDao.deletePlanById(planId)
        }
    }
}
