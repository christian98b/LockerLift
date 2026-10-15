package com.lockerlift.mobile.ui.catalog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.Machine
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WearableDataLayerManager
import com.lockerlift.mobile.sync.MobileMasterDataSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MachineEditResult {
    SUCCESS,
    EMPTY_FIELDS,
    DUPLICATE_NAME
}

enum class MachineDeleteResult {
    SUCCESS,
    BLOCKED_ACTIVE_WORKOUT
}

/**
 * Owns the machine catalog: list flow, add/update validation and persistence,
 * and the master-data push of the catalog to the watch (DataClient channel).
 */
class CatalogViewModel(
    application: Application,
    private val database: LockerLiftDatabase
) : AndroidViewModel(application) {

    private val machineDao = database.machineDao()
    private var actionJob: Job? = null

    val machines: StateFlow<List<MachineEntity>> = machineDao.getAllMachinesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val knownMuscleGroups: StateFlow<List<String>> = machineDao.getAllTargetMuscleGroupsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    companion object {
        /**
         * Case-insensitive substring filter over the distinct muscle groups
         * already used in the catalog, used for the autocomplete dropdown.
         */
        fun filterMuscleGroupSuggestions(
            knownMuscleGroups: List<String>,
            query: String
        ): List<String> {
            val trimmedQuery = query.trim()
            if (trimmedQuery.isEmpty()) return knownMuscleGroups
            return knownMuscleGroups.filter { it.contains(trimmedQuery, ignoreCase = true) }
        }
    }

    /**
     * Validates and inserts a new machine, then pushes the updated catalog
     * to the Wear OS companion. The async duplicate check is reported back
     * via [onResult] so the UI can show the matching error message.
     */
    fun addMachine(
        name: String,
        muscleGroup: String,
        settingsNote: String,
        incrementKgText: String,
        onResult: (MachineEditResult) -> Unit
    ) {
        if (name.isBlank() || muscleGroup.isBlank()) {
            onResult(MachineEditResult.EMPTY_FIELDS)
            return
        }

        actionJob = viewModelScope.launch {
            if (machineDao.getMachineByName(name.trim()) != null) {
                onResult(MachineEditResult.DUPLICATE_NAME)
                return@launch
            }
            val newMachine = Machine(
                name = name.trim(),
                targetMuscleGroup = muscleGroup.trim(),
                machineSettingsNote = settingsNote.takeIf { it.isNotBlank() },
                defaultIncrementKg = incrementKgText.toFloatOrNull() ?: 2.5f
            )
            machineDao.insertMachine(newMachine.toEntity())
            syncCatalogToWear()
            onResult(MachineEditResult.SUCCESS)
        }
    }

    /**
     * Validates and applies changes to an existing machine, then pushes the
     * updated catalog to the Wear OS companion.
     */
    fun updateMachine(
        current: MachineEntity,
        name: String,
        muscleGroup: String,
        settingsNote: String,
        incrementKgText: String,
        onResult: (MachineEditResult) -> Unit
    ) {
        if (name.isBlank() || muscleGroup.isBlank()) {
            onResult(MachineEditResult.EMPTY_FIELDS)
            return
        }

        actionJob = viewModelScope.launch {
            val existing = machineDao.getMachineByName(name.trim())
            if (existing != null && existing.id != current.id) {
                onResult(MachineEditResult.DUPLICATE_NAME)
                return@launch
            }
            val updated = current.copy(
                name = name.trim(),
                targetMuscleGroup = muscleGroup.trim(),
                machineSettingsNote = settingsNote.takeIf { it.isNotBlank() },
                defaultIncrementKg = incrementKgText.toFloatOrNull() ?: 2.5f,
                updatedAt = System.currentTimeMillis()
            )
            machineDao.updateMachine(updated)
            syncCatalogToWear()
            onResult(MachineEditResult.SUCCESS)
        }
    }

    /**
     * Soft-deletes a machine (Issue #40): the machine disappears from the
     * catalog and all selection lists while historical workout sets stay
     * readable. It is removed from all templates; deleting is blocked while
     * a running workout uses the machine. Afterwards the updated master data
     * is pushed to the watch.
     */
    fun deleteMachine(machine: MachineEntity, onResult: (MachineDeleteResult) -> Unit) {
        actionJob = viewModelScope.launch {
            val sessionDao = database.workoutSessionDao()
            val activeInstance = sessionDao.getActiveMachineInstanceByMachineId(machine.id)
            if (activeInstance != null) {
                onResult(MachineDeleteResult.BLOCKED_ACTIVE_WORKOUT)
                return@launch
            }
            database.workoutTemplateDao().deleteCrossRefsByMachineId(machine.id)
            machineDao.softDeleteMachine(machine.id, System.currentTimeMillis())
            MobileMasterDataSync.pushAllMasterData(
                getApplication(),
                database,
                WearableDataLayerManager(getApplication())
            )
            onResult(MachineDeleteResult.SUCCESS)
        }
    }

    /**
     * Pushes the full catalog to the watch (DataClient master data channel).
     * Defensive: a failed push must never crash the app; the watch re-pulls
     * master data on the next sync anyway.
     */
    private suspend fun syncCatalogToWear() {
        runCatching {
            val allMachines = machineDao.getAllMachines()
            val catalogJson = SyncPayloadSerializer.encodeMachines(allMachines.map { it.toDomainModel() })
            WearableDataLayerManager(getApplication()).syncEquipmentCatalog(catalogJson)
        }
    }

    /**
     * Waits until all pending add/update actions have been flushed to the DB.
     * Intended for tests to make assertions deterministic.
     */
    internal suspend fun awaitActions() {
        actionJob?.join()
    }
}
