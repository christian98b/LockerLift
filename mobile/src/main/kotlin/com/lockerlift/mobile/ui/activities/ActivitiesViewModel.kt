package com.lockerlift.mobile.ui.activities

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.database.model.ActivityEntryWithType
import com.lockerlift.core.model.ActivityEntry
import com.lockerlift.core.model.ActivityType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * State holder for the activity journal screen (Issue #56): exposes activity
 * types and entries as flows and owns entry/type CRUD side effects
 * (including Health Connect delegation) that previously lived in the
 * composable.
 */
class ActivitiesViewModel(
    application: Application,
    private val database: LockerLiftDatabase
) : AndroidViewModel(application) {

    private val activityTypeDao = database.activityTypeDao()
    private val activityEntryDao = database.activityEntryDao()
    private var actionJob: kotlinx.coroutines.Job? = null

    /**
     * Waits until all pending CRUD actions have been flushed to the DB.
     * Intended for tests to make assertions deterministic.
     */
    internal suspend fun awaitActions() {
        actionJob?.join()
    }

    val types: StateFlow<List<ActivityType>> =
        activityTypeDao.getAllActivityTypesFlow()
            .map { list -> list.map { it.toDomainModel() } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val entries: StateFlow<List<ActivityEntryWithType>> =
        activityEntryDao.getAllEntriesWithTypeFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Logs a new activity entry and delegates the Health Connect export.
     */
    fun logEntry(
        entry: ActivityEntry,
        typeName: String,
        onExportToHealthConnect: suspend (ActivityEntry, String) -> Unit
    ) {
        val previous = actionJob
        actionJob = viewModelScope.launch {
            previous?.join()
            activityEntryDao.insertEntry(entry.toEntity())
            onExportToHealthConnect(entry, typeName)
        }
    }

    /**
     * Updates an existing activity entry and delegates the Health Connect
     * export.
     */
    fun updateEntry(
        updatedEntry: ActivityEntry,
        typeName: String,
        onExportToHealthConnect: suspend (ActivityEntry, String) -> Unit
    ) {
        val previous = actionJob
        actionJob = viewModelScope.launch {
            previous?.join()
            activityEntryDao.updateEntry(updatedEntry.toEntity())
            onExportToHealthConnect(updatedEntry, typeName)
        }
    }

    /**
     * Deletes an activity entry and the corresponding Health Connect record.
     */
    fun deleteEntry(
        entryId: String,
        onDeleteFromHealthConnect: suspend (String) -> Unit
    ) {
        val previous = actionJob
        actionJob = viewModelScope.launch {
            previous?.join()
            activityEntryDao.deleteEntryById(entryId)
            onDeleteFromHealthConnect(entryId)
        }
    }

    fun createType(type: ActivityType) {
        val previous = actionJob
        actionJob = viewModelScope.launch {
            previous?.join()
            activityTypeDao.insertActivityType(type.toEntity())
        }
    }

    /**
     * Deletes an activity type along with all of its entries and removes
     * every affected Health Connect record.
     */
    fun deleteType(
        type: ActivityType,
        onDeleteFromHealthConnect: suspend (String) -> Unit
    ) {
        val previous = actionJob
        actionJob = viewModelScope.launch {
            previous?.join()
            val entries = activityEntryDao.getEntriesByTypeId(type.id)
            activityTypeDao.deleteActivityTypeById(type.id)
            entries.forEach { onDeleteFromHealthConnect(it.id) }
        }
    }
}
