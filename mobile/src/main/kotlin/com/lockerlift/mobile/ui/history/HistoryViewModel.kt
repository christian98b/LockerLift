package com.lockerlift.mobile.ui.history

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.SessionMachineInstanceEntity
import com.lockerlift.core.database.entity.SyncQueueEntity
import com.lockerlift.core.database.entity.WorkoutSessionEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.model.WorkoutSessionWithDetails
import com.lockerlift.core.healthconnect.HealthConnectManager
import com.lockerlift.core.model.Machine
import com.lockerlift.core.sync.WorkoutSessionPayload
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncIngestionEngine
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.SyncQueueWorker
import com.lockerlift.core.sync.WearableDataLayerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * State holder for the history screen (Issue #56): exposes the session list
 * and machine catalog as flows and owns the persistence side effects that
 * previously lived inside the composable - the cascade-deletion flow
 * (tombstone, Room delete, sync queue cleanup, Wear sync, Health Connect)
 * and the full-graph session edit flow.
 */
class HistoryViewModel(
    application: android.app.Application,
    private val database: LockerLiftDatabase
) : AndroidViewModel(application) {

    private val sessionDao = database.workoutSessionDao()
    private val machineDao = database.machineDao()
    private val syncQueueDao = database.syncQueueDao()
    private val healthConnectManager = HealthConnectManager(application)
    private val dataLayerManager = WearableDataLayerManager(application)

    val sessions: StateFlow<List<WorkoutSessionWithDetails>> =
        sessionDao.getAllSessionsWithDetailsFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val machines: StateFlow<List<MachineEntity>> =
        machineDao.getAllMachinesFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val availableMachines: List<Machine>
        get() = machines.value.map { it.toDomainModel() }

    /**
     * Deletes a workout session end to end: records the tombstone first,
     * removes the session (Room cascades to instances and sets), cleans the
     * pending sync queue, queues the deletion sync to the Wear companion and
     * removes the Health Connect record if it was exported before.
     */
    fun deleteSession(sessionId: String) {
        val previous = actionJob
        actionJob = viewModelScope.launch {
            previous?.join()
            SyncIngestionEngine.addDeletedSessionTombstone(
                database, sessionId, originDevice = ORIGIN_DEVICE
            )
            sessionDao.deleteSession(sessionId)
            syncQueueDao.deleteQueueItemBySessionId(sessionId)
            syncQueueDao.insertQueueItem(
                SyncQueueEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    payloadJson = SyncConstants.ACTION_DELETE
                )
            )
            enqueueAndFlush()
            if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                healthConnectManager.deleteWorkoutSession(sessionId)
            }
        }
    }

    /**
     * Persists the updated session graph, replaces any pending sync queue
     * item with the fresh payload, pushes it to the Wear companion and
     * updates the Health Connect record when available.
     */
    fun saveSession(
        session: WorkoutSessionEntity,
        instances: List<SessionMachineInstanceEntity>,
        sets: List<WorkoutSetEntity>,
        payload: WorkoutSessionPayload,
        templateName: String?
    ) {
        val previous = actionJob
        actionJob = viewModelScope.launch {
            previous?.join()
            sessionDao.upsertFullSession(session = session, instances = instances, sets = sets)
            val payloadJson = SyncPayloadSerializer.encodeSessionPayload(payload)
            syncQueueDao.deleteQueueItemBySessionId(session.id)
            syncQueueDao.insertQueueItem(
                SyncQueueEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = session.id,
                    payloadJson = payloadJson
                )
            )
            enqueueAndFlush()
            if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                healthConnectManager.exportWorkoutSession(
                    session = session.toDomainModel(),
                    templateName = templateName
                )
            }
        }
    }

    private var actionJob: kotlinx.coroutines.Job? = null

    /**
     * Waits until all pending delete/save actions have been flushed to the
     * DB and the watch. Intended for tests to make assertions deterministic.
     */
    internal suspend fun awaitActions() {
        actionJob?.join()
    }

    private suspend fun enqueueAndFlush() {
        val context = getApplication<android.app.Application>()
        SyncQueueWorker.enqueue(context)
        runCatching {
            dataLayerManager.flushPendingQueue(syncQueueDao, SyncConstants.CAPABILITY_WEAR)
        }
    }

    companion object {
        const val ORIGIN_DEVICE = "MOBILE"
    }
}
