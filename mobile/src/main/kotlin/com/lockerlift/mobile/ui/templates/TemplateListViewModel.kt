package com.lockerlift.mobile.ui.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.WorkoutTemplateEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.database.model.WorkoutTemplateWithMachines
import com.lockerlift.core.database.state.ActiveSessionStateRepository
import com.lockerlift.core.model.WorkoutTemplate
import com.lockerlift.core.sync.WearableDataLayerManager
import com.lockerlift.mobile.sync.MobileMasterDataSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Outcome of a template deletion attempt. */
sealed class TemplateDeleteResult {
    data class Deleted(val templateName: String) : TemplateDeleteResult()
    object BlockedActiveWorkout : TemplateDeleteResult()
}

/**
 * State holder for the template list screen (Issue #56): exposes active
 * templates and catalog machines as flows and owns the template CRUD side
 * effects (create, reordering edit, soft delete with active-workout guard
 * and Wear master-data push) that previously lived in the composable.
 */
class TemplateListViewModel(
    application: Application,
    private val database: LockerLiftDatabase
) : AndroidViewModel(application) {

    private val templateDao = database.workoutTemplateDao()
    private var actionJob: kotlinx.coroutines.Job? = null

    /**
     * Waits until all pending CRUD actions have been flushed to the DB.
     * Intended for tests to make assertions deterministic.
     */
    internal suspend fun awaitActions() {
        actionJob?.join()
    }
    private val machineDao = database.machineDao()
    private val activeSessionRepository = ActiveSessionStateRepository(database.activeSessionStateDao())

    val templates: StateFlow<List<WorkoutTemplateWithMachines>> =
        templateDao.getAllActiveTemplatesWithMachinesFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val catalogMachines: StateFlow<List<MachineEntity>> =
        machineDao.getAllMachinesFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _assignedMachines = MutableStateFlow<List<MachineEntity>>(emptyList())
    val assignedMachines: StateFlow<List<MachineEntity>> = _assignedMachines.asStateFlow()

    /**
     * Loads the ordered machine assignments of the template that is being
     * edited.
     */
    fun loadAssignedMachines(templateId: String) {
        val previous = actionJob
        actionJob = viewModelScope.launch {
            previous?.join()
            _assignedMachines.value = templateDao.getMachinesForTemplateOrdered(templateId)
        }
    }

    /**
     * Replaces the local edit-order draft of assigned machines.
     */
    fun setAssignedMachines(machines: List<MachineEntity>) {
        _assignedMachines.value = machines
    }

    fun addAssignedMachine(machine: MachineEntity) {
        _assignedMachines.value = _assignedMachines.value + machine
    }

    /**
     * Creates a new template and pushes master data to the watch.
     */
    fun createTemplate(name: String, description: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val previous = actionJob
        actionJob = viewModelScope.launch {
            previous?.join()
            templateDao.insertTemplate(
                WorkoutTemplate(
                    name = trimmed,
                    description = description?.takeIf { it.isNotBlank() }
                ).toEntity()
            )
            pushMasterData()
        }
    }

    /**
     * Saves the edited template with the (reordered) machine list and
     * pushes master data to the watch.
     */
    fun saveTemplate(
        current: WorkoutTemplateEntity,
        name: String,
        description: String?,
        machineIdsInOrder: List<String>
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val previous = actionJob
        actionJob = viewModelScope.launch {
            previous?.join()
            templateDao.saveTemplateWithMachines(
                template = current.copy(
                    name = trimmed,
                    description = description?.takeIf { it.isNotBlank() },
                    updatedAt = System.currentTimeMillis()
                ),
                machineIdsInOrder = machineIdsInOrder
            )
            pushMasterData()
        }
    }

    /**
     * Soft-deletes the template with its cross-refs. Deletion is blocked
     * while the template is used by the active workout. Afterwards the
     * updated master data is pushed to the watch.
     */
    fun deleteTemplate(templateId: String, templateName: String, onResult: (TemplateDeleteResult) -> Unit) {
        val previous = actionJob
        actionJob = viewModelScope.launch {
            previous?.join()
            val activeTemplateId = activeSessionRepository.loadActiveTemplateId()
            if (activeTemplateId == templateId) {
                onResult(TemplateDeleteResult.BlockedActiveWorkout)
                return@launch
            }
            templateDao.softDeleteTemplateWithCrossRefs(templateId)
            pushMasterData()
            onResult(TemplateDeleteResult.Deleted(templateName))
        }
    }

    private suspend fun pushMasterData() {
        MobileMasterDataSync.pushAllMasterData(
            getApplication(), database, WearableDataLayerManager(getApplication())
        )
    }
}
