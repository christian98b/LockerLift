package com.lockerlift.wear.tracking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.SyncQueueEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.database.state.ActiveSessionStateRepository
import com.lockerlift.core.model.ActiveSessionSnapshot
import com.lockerlift.core.model.ActiveSessionSnapshotCodec
import com.lockerlift.core.model.CreateMachineMode
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.QueueStatus
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.SyncStatus
import com.lockerlift.core.model.WorkoutSession
import com.lockerlift.core.model.WeightConversion
import com.lockerlift.core.model.WeightDirection
import com.lockerlift.core.model.WeightUnit
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.core.sync.SessionMachineInstancePayload
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.SyncQueueWorker
import com.lockerlift.core.sync.WearableDataLayerManager
import com.lockerlift.core.sync.WorkoutSessionPayload
import com.lockerlift.wear.logic.WearWorkoutLogic
import com.lockerlift.wear.logic.WorkoutPauseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Immutable UI state of the active workout on the watch.
 */
data class ActiveWorkoutUiState(
    val isWorkoutActive: Boolean = false,
    val sessionId: String = "",
    val templateId: String? = null,
    val templateName: String? = null,
    val startTimeMillis: Long = 0L,
    val initialMachines: List<Machine> = emptyList(),
    val instances: List<SessionMachineInstance> = emptyList(),
    val setsByInstance: Map<String, List<WorkoutSet>> = emptyMap(),
    val pauseState: WorkoutPauseState = WorkoutPauseState(),
    val resumedFromInterruption: Boolean = false
)

/**
 * Owns the active workout session on Wear OS: all session mutations flow
 * through this ViewModel, the resulting state is persisted to the local
 * Room database after every mutation (Locker Scenario durability), and an
 * interrupted workout is restored automatically on re-entry (crash recovery).
 */
class ActiveWorkoutViewModel(
    application: Application,
    private val database: LockerLiftDatabase,
    private val templateId: String?,
    private val templateName: String?
) : AndroidViewModel(application) {

    private val sessionDao = database.workoutSessionDao()
    private val machineDao = database.machineDao()
    private val templateDao = database.workoutTemplateDao()
    private val syncQueueDao = database.syncQueueDao()
    private val activeSessionRepository = ActiveSessionStateRepository(database.activeSessionStateDao())

    private val persistMutex = Mutex()
    private var persistJob: Job? = null

    private val _uiState = MutableStateFlow(ActiveWorkoutUiState())
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    /** Full machine catalog for pickers and name resolution. */
    val catalogMachines: StateFlow<List<Machine>> = machineDao.getAllMachinesFlow()
        .map { entities -> entities.map { it.toDomainModel() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val initJob: Job = viewModelScope.launch {
        val snapshot = activeSessionRepository.load()
        if (snapshot != null) {
            restoreFromSnapshot(snapshot)
        } else {
            startNewSession()
        }
        persist()
    }


    // ---------------------------------------------------------------------
    // Session lifecycle
    // ---------------------------------------------------------------------

    private suspend fun startNewSession() {
        val sessionId = UUID.randomUUID().toString()
        val machines = templateId?.let { id ->
            templateDao.getMachinesForTemplateOrdered(id).map { it.toDomainModel() }
        } ?: emptyList()
        val instances = WearWorkoutLogic.initializeSessionInstances(machines, sessionId)
        _uiState.value = ActiveWorkoutUiState(
            isWorkoutActive = true,
            sessionId = sessionId,
            templateId = templateId,
            templateName = templateName,
            startTimeMillis = System.currentTimeMillis(),
            initialMachines = machines,
            instances = instances,
            setsByInstance = instances.associate { it.id to emptyList<WorkoutSet>() },
            pauseState = WorkoutPauseState()
        )
    }

    private fun restoreFromSnapshot(snapshot: ActiveSessionSnapshot) {
        _uiState.value = ActiveWorkoutUiState(
            isWorkoutActive = true,
            sessionId = snapshot.sessionId,
            templateId = snapshot.templateId,
            templateName = snapshot.templateName,
            startTimeMillis = snapshot.startTimeMillis,
            initialMachines = snapshot.initialMachines,
            instances = snapshot.instances,
            setsByInstance = ActiveSessionSnapshotCodec.setsMapFromSnapshot(snapshot),
            pauseState = WorkoutPauseState(
                isPaused = snapshot.pausedAtMillis != null,
                pausedAtMillis = snapshot.pausedAtMillis,
                totalPausedDurationMillis = snapshot.totalPausedMillis
            ),
            resumedFromInterruption = true
        )
    }

    private fun currentSnapshot(): ActiveSessionSnapshot {
        val state = _uiState.value
        return ActiveSessionSnapshot(
            sessionId = state.sessionId,
            templateId = state.templateId,
            templateName = state.templateName,
            startTimeMillis = state.startTimeMillis,
            originDevice = ORIGIN_DEVICE,
            initialMachines = state.initialMachines,
            instances = state.instances,
            pausedAtMillis = if (state.pauseState.isPaused) state.pauseState.pausedAtMillis else null,
            totalPausedMillis = state.pauseState.totalPausedDurationMillis
        )
    }

    /**
     * Persists the current session state after a mutation. Serialized with a
     * mutex so snapshots are written in mutation order; the freshest state is
     * read inside the lock.
     */
    private fun persist() {
        persistJob = viewModelScope.launch {
            persistMutex.withLock {
                val snapshot = ActiveSessionSnapshotCodec.snapshotWithSets(
                    currentSnapshot(),
                    _uiState.value.setsByInstance
                )
                activeSessionRepository.save(snapshot)
            }
        }
    }

    /**
     * Waits until all pending snapshot writes have been flushed to the DB.
     * Intended for tests to make durability assertions deterministic.
     */
    internal suspend fun awaitPersistence() {
        initJob.join()
        persistJob?.join()
        persistMutex.withLock { }
    }

    // ---------------------------------------------------------------------
    // Set mutations
    // ---------------------------------------------------------------------

    fun logSet(instanceId: String, set: WorkoutSet) {
        mutate { state ->
            val sets = state.setsByInstance[instanceId].orEmpty()
            state.copy(setsByInstance = state.setsByInstance + (instanceId to sets + set))
        }
    }

    fun updateSet(instanceId: String, targetIndex: Int, weightKg: Float, reps: Int, cadence: String?) {
        mutate { state ->
            val sets = state.setsByInstance[instanceId].orEmpty()
            val updated = WearWorkoutLogic.updateSetInList(sets, targetIndex, weightKg, reps, cadence)
            state.copy(setsByInstance = state.setsByInstance + (instanceId to updated))
        }
    }

    fun deleteSet(instanceId: String, targetIndex: Int) {
        mutate { state ->
            val sets = state.setsByInstance[instanceId].orEmpty()
            val renumbered = WearWorkoutLogic.deleteSetAndRenumber(sets, targetIndex)
            state.copy(setsByInstance = state.setsByInstance + (instanceId to renumbered))
        }
    }

    // ---------------------------------------------------------------------
    // Station mutations
    // ---------------------------------------------------------------------

    fun appendMachine(machine: Machine) {
        mutate { state ->
            val newInstance = WearWorkoutLogic.appendMachineToSession(state.instances, state.sessionId, machine)
            val initialMachines = if (state.initialMachines.none { it.id == machine.id }) {
                state.initialMachines + machine
            } else {
                state.initialMachines
            }
            state.copy(
                instances = state.instances + newInstance,
                setsByInstance = state.setsByInstance + (newInstance.id to emptyList()),
                initialMachines = initialMachines
            )
        }
    }

    fun replaceMachine(targetIndex: Int, machine: Machine) {
        mutate { state ->
            val updated = WearWorkoutLogic.replaceMachineInSession(state.instances, targetIndex, machine)
            val initialMachines = if (state.initialMachines.none { it.id == machine.id }) {
                state.initialMachines + machine
            } else {
                state.initialMachines
            }
            state.copy(instances = updated, initialMachines = initialMachines)
        }
    }

    fun toggleSkipStation(targetIndex: Int) {
        mutate { state ->
            state.copy(instances = WearWorkoutLogic.toggleSkipStation(state.instances, targetIndex))
        }
    }

    fun saveStationNote(targetIndex: Int, note: String?) {
        mutate { state ->
            val updated = state.instances.mapIndexed { index, instance ->
                if (index == targetIndex) instance.copy(customSettingsNote = note) else instance
            }
            state.copy(instances = updated)
        }
    }

    fun pauseWorkout() {
        mutate { state ->
            state.copy(pauseState = WearWorkoutLogic.pauseWorkout(state.pauseState, System.currentTimeMillis()))
        }
    }

    fun resumeWorkout() {
        mutate { state ->
            state.copy(pauseState = WearWorkoutLogic.resumeWorkout(state.pauseState, System.currentTimeMillis()))
        }
    }

    /**
     * Persists a machine setting note permanently in the catalog (AK 1.2.2).
     */
    fun saveMachineNoteToCatalog(machineId: String, note: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = machineDao.getMachineById(machineId) ?: return@launch
            machineDao.updateMachine(
                entity.copy(machineSettingsNote = note, updatedAt = System.currentTimeMillis())
            )
        }
    }

    /**
     * Creates a new catalog machine from the in-workout creator screen and
     * returns it. Returns null if validation fails.
     */
    fun createMachine(
        name: String,
        incrementKg: Float,
        weightUnit: WeightUnit = WeightUnit.KG,
        weightDirection: WeightDirection = WeightDirection.NORMAL,
        targetMuscleGroup: String,
        existingMachineNames: List<String>,
        mode: CreateMachineMode,
        targetIndex: Int
    ): Boolean {
        val validation = WearWorkoutLogic.validateNewMachine(name, existingMachineNames)
        if (validation != com.lockerlift.core.model.ValidationResult.Valid) return false

        viewModelScope.launch(Dispatchers.IO) {
            val newMachine = Machine(
                name = name.trim(),
                targetMuscleGroup = targetMuscleGroup,
                defaultIncrementKg = WeightConversion.toKg(incrementKg, weightUnit),
                weightUnit = weightUnit,
                weightDirection = weightDirection
            )
            machineDao.insertMachine(newMachine.toEntity())
            withContext(Dispatchers.Main) {
                when (mode) {
                    CreateMachineMode.ADD_NEW -> appendMachine(newMachine)
                    CreateMachineMode.REPLACE_CURRENT -> replaceMachine(targetIndex, newMachine)
                }
            }
        }
        return true
    }

    // ---------------------------------------------------------------------
    // Read helpers
    // ---------------------------------------------------------------------

    suspend fun getHistoricalSetsForMachine(machineId: String): List<WorkoutSetEntity> {
        return sessionDao.getLastCompletedSetsForMachine(machineId)
    }

    // ---------------------------------------------------------------------
    // Finish
    // ---------------------------------------------------------------------

    /**
     * Completes the workout: writes the full session graph to the local DB,
     * enqueues the sync payload (Store-and-Forward), attempts an immediate
     * flush, stops the foreground service and clears the durable snapshot.
     */
    fun finishWorkout(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val endTimeMillis = System.currentTimeMillis()
            val adjustedStartTime = WearWorkoutLogic.calculateAdjustedStartTime(
                originalStartTime = state.startTimeMillis,
                endTime = endTimeMillis,
                totalPausedMillis = state.pauseState.totalPausedDurationMillis
            )
            val session = WorkoutSession(
                id = state.sessionId,
                templateId = state.templateId,
                startTime = adjustedStartTime,
                endTime = endTimeMillis,
                originDevice = ORIGIN_DEVICE,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            val allSets = state.setsByInstance.values.flatten()
            val payload = WorkoutSessionPayload(
                session = session,
                templateName = state.templateName,
                machineInstances = state.instances.map { instance ->
                    val machine = catalogMachines.value.find { it.id == instance.machineId }
                        ?: state.initialMachines.find { it.id == instance.machineId }
                        ?: Machine(id = instance.machineId, name = "Station", targetMuscleGroup = "General")
                    SessionMachineInstancePayload(
                        instance = instance,
                        machine = machine,
                        sets = state.setsByInstance[instance.id].orEmpty()
                    )
                }
            )
            val syncItem = SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                sessionId = state.sessionId,
                payloadJson = SyncPayloadSerializer.encodeSessionPayload(payload),
                status = QueueStatus.PENDING
            )

            withContext(Dispatchers.IO) {
                // DB write first, then queue insert: if the app dies between
                // the two calls, SyncIngestionEngine.recoverOrphanedSessions()
                // re-queues the session on next startup.
                sessionDao.upsertFullSession(
                    session = session.toEntity(),
                    instances = state.instances.map { it.toEntity() },
                    sets = allSets.map { it.toEntity() }
                )
                syncQueueDao.insertQueueItem(syncItem)
            }

            // Store-and-Forward: schedule the background flush and try an
            // immediate one in case the phone is reachable right now.
            val app = getApplication<Application>()
            SyncQueueWorker.enqueue(app)
            runCatching {
                WearableDataLayerManager(app).flushPendingQueue(syncQueueDao, SyncConstants.CAPABILITY_MOBILE)
            }

            activeSessionRepository.clear()
            WorkoutForegroundService.stopService(app)
            withContext(Dispatchers.Main) {
                onFinished()
            }
        }
    }

    /**
     * Consolidates session variations back into the template (invariant 2),
     * then finishes the workout.
     */
    fun consolidateTemplateAndFinish(onFinished: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val id = state.templateId
            if (id != null) {
                val templateEntity = templateDao.getTemplateWithMachinesById(id)?.template
                if (templateEntity != null) {
                    val nonSkippedMachineIds = state.instances.filter { !it.isSkipped }.map { it.machineId }
                    templateDao.saveTemplateWithMachines(
                        template = templateEntity.copy(updatedAt = System.currentTimeMillis()),
                        machineIdsInOrder = nonSkippedMachineIds
                    )
                }
            }
            withContext(Dispatchers.Main) {
                finishWorkout(onFinished)
            }
        }
    }

    private inline fun mutate(reducer: (ActiveWorkoutUiState) -> ActiveWorkoutUiState) {
        _uiState.value = reducer(_uiState.value)
        persist()
    }

    companion object {
        const val ORIGIN_DEVICE = "WEAR_OS"
    }
}
