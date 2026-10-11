package com.lockerlift.wear.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.sync.CompanionDeviceStatus
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncResult
import com.lockerlift.core.sync.WearableDataLayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Outcome of a manual "Sync now" run, mapped to localized strings and
 * haptics by the UI layer.
 */
sealed interface SyncOutcome {
    data class Success(val itemsSyncedCount: Int) : SyncOutcome
    data object NoCompanionFound : SyncOutcome
    data class Error(val message: String) : SyncOutcome
}

/**
 * Owns the watch settings screen state: pending sync queue count,
 * companion status, last sync timestamp and the manual sync run.
 */
class WearSettingsViewModel(
    application: Application,
    database: LockerLiftDatabase
) : AndroidViewModel(application) {

    private val syncQueueDao = database.syncQueueDao()
    private val dataLayerManager = WearableDataLayerManager(application)

    val pendingQueueCount: StateFlow<Int> = syncQueueDao.getPendingQueueCountFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _companionStatus = MutableStateFlow<CompanionDeviceStatus?>(null)
    val companionStatus: StateFlow<CompanionDeviceStatus?> = _companionStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _companionStatus.value = dataLayerManager.getMobileCompanionStatus()
            _lastSyncTimestamp.value = dataLayerManager.getLastSyncTimestamp()
        }
    }

    /**
     * Runs a full manual sync: pull master data from the phone, then push
     * all pending workout sessions (Store-and-Forward flush).
     */
    fun syncNow(onOutcome: (SyncOutcome) -> Unit) {
        if (_isSyncing.value) return
        _isSyncing.value = true

        viewModelScope.launch {
            val status = dataLayerManager.getMobileCompanionStatus()
            _companionStatus.value = status

            if (!status.isConnected) {
                _isSyncing.value = false
                onOutcome(SyncOutcome.NoCompanionFound)
                return@launch
            }

            dataLayerManager.requestMasterDataFromPhone()

            val result = dataLayerManager.flushPendingQueue(
                syncQueueDao,
                SyncConstants.CAPABILITY_MOBILE
            )
            _lastSyncTimestamp.value = dataLayerManager.getLastSyncTimestamp()
            _isSyncing.value = false

            when (result) {
                is SyncResult.Success -> onOutcome(SyncOutcome.Success(result.itemsSyncedCount))
                is SyncResult.NoCompanionFound -> onOutcome(SyncOutcome.NoCompanionFound)
                is SyncResult.Error -> onOutcome(SyncOutcome.Error(result.message))
            }
        }
    }
}
