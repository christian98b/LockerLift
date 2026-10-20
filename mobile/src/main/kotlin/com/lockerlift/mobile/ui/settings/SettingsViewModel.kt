package com.lockerlift.mobile.ui.settings

import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncResult
import com.lockerlift.core.sync.SyncUtils
import com.lockerlift.core.sync.WearableDataLayerManager
import com.lockerlift.core.sync.WorkoutTemplatePayload
import com.lockerlift.core.sync.CompanionDeviceStatus
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.mobile.backup.BackupResult
import com.lockerlift.mobile.backup.LocalBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Result of the manual "sync workouts" action exposed to the UI.
 */
sealed class WorkoutSyncUiResult {
    data class Success(val itemsSyncedCount: Int) : WorkoutSyncUiResult()
    object NoPending : WorkoutSyncUiResult()
    object NoCompanion : WorkoutSyncUiResult()
    data class Error(val message: String) : WorkoutSyncUiResult()
}

/**
 * State holder for the settings screen (Issue #56): owns the companion and
 * sync queue state, the manual master-data and workout syncs and the
 * backup/restore operations that previously accessed the database directly
 * inside the composable.
 */
class SettingsViewModel(
    application: android.app.Application,
    private val database: LockerLiftDatabase
) : AndroidViewModel(application) {

    private val dataLayerManager = WearableDataLayerManager(application)
    private val syncQueueDao = database.syncQueueDao()
    private val machineDao = database.machineDao()
    private val templateDao = database.workoutTemplateDao()

    val pendingQueueCount: StateFlow<Int> = syncQueueDao.getPendingQueueCountFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _companionStatus = MutableStateFlow<CompanionDeviceStatus?>(null)
    val companionStatus: StateFlow<CompanionDeviceStatus?> = _companionStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _isSyncingMasterData = MutableStateFlow(false)
    val isSyncingMasterData: StateFlow<Boolean> = _isSyncingMasterData.asStateFlow()

    private val _isSyncingWorkouts = MutableStateFlow(false)
    val isSyncingWorkouts: StateFlow<Boolean> = _isSyncingWorkouts.asStateFlow()

    init {
        refreshCompanionStatus()
    }

    /**
     * Re-reads the Wear companion connection state and the last sync
     * timestamp from the DataClient.
     */
    private var syncJob: kotlinx.coroutines.Job? = null

    /**
     * Waits until all pending sync actions have finished. Intended for
     * tests to make assertions deterministic.
     */
    internal suspend fun awaitActions() {
        syncJob?.join()
    }

    fun refreshCompanionStatus() {
        syncJob = viewModelScope.launch {
            _companionStatus.value = dataLayerManager.getWearCompanionStatus()
            _lastSyncTimestamp.value = dataLayerManager.getLastSyncTimestamp()
        }
    }

    /**
     * Manually pushes the full equipment catalog and all active templates
     * (with their ordered machine cross-refs) to the Wear companion.
     * Returns true when both channels succeeded.
     */
    fun syncMasterData(onResult: (Boolean) -> Unit) {
        if (_isSyncingMasterData.value) return
        _isSyncingMasterData.value = true
        syncJob = viewModelScope.launch {
            val catalogSuccess = dataLayerManager.syncEquipmentCatalog(catalogJson())
            val templatesSuccess = dataLayerManager.syncTemplates(templatesJson())
            _lastSyncTimestamp.value = dataLayerManager.getLastSyncTimestamp()
            _isSyncingMasterData.value = false
            onResult(catalogSuccess && templatesSuccess)
        }
    }

    /**
     * Flushes all pending workout sync queue items to the Wear companion
     * and reports the outcome for the user-facing snackbar message.
     */
    fun syncWorkouts(onResult: (WorkoutSyncUiResult) -> Unit) {
        if (_isSyncingWorkouts.value) return
        _isSyncingWorkouts.value = true
        syncJob = viewModelScope.launch {
            val result = dataLayerManager.flushPendingQueue(
                syncQueueDao, SyncConstants.CAPABILITY_WEAR
            )
            _lastSyncTimestamp.value = dataLayerManager.getLastSyncTimestamp()
            _isSyncingWorkouts.value = false
            onResult(
                when (result) {
                    is SyncResult.Success ->
                        if (result.itemsSyncedCount > 0) {
                            WorkoutSyncUiResult.Success(result.itemsSyncedCount)
                        } else {
                            WorkoutSyncUiResult.NoPending
                        }
                    is SyncResult.NoCompanionFound -> WorkoutSyncUiResult.NoCompanion
                    is SyncResult.Error -> WorkoutSyncUiResult.Error(result.message)
                }
            )
        }
    }

    /**
     * Exports the full database to the user-picked SAF directory.
     */
    fun exportBackup(dirUri: Uri, onResult: (BackupResult) -> Unit) {
        syncJob = viewModelScope.launch {
            onResult(LocalBackupManager(getApplication(), database).exportToDirectory(dirUri))
        }
    }

    /**
     * Restores the database from a user-picked backup file.
     */
    fun restoreBackup(uri: Uri, onResult: (BackupResult) -> Unit) {
        syncJob = viewModelScope.launch {
            onResult(LocalBackupManager(getApplication(), database).restoreFromFile(uri))
        }
    }

    fun formatSyncTimestamp(neverLabel: String): String =
        SyncUtils.formatSyncTimestamp(_lastSyncTimestamp.value, neverLabel)

    private suspend fun catalogJson(): String {
        val allMachines = machineDao.getAllMachines()
        return SyncPayloadSerializer.encodeMachines(allMachines.map { it.toDomainModel() })
    }

    private suspend fun templatesJson(): String {
        val allActive = templateDao.getAllActiveTemplatesWithMachinesFlow().first()
        val payloads = allActive.map { item ->
            val orderedMachineIds = templateDao.getCrossRefsForTemplate(item.template.id)
                .map { it.machineId }
            WorkoutTemplatePayload(
                template = item.template.toDomainModel(),
                machineIdsInOrder = orderedMachineIds
            )
        }
        return SyncPayloadSerializer.encodeTemplates(payloads)
    }
}
