package com.lockerlift.mobile.tracking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.SyncQueueEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.database.state.ActiveSessionStateRepository
import com.lockerlift.core.healthconnect.HealthConnectManager
import com.lockerlift.core.model.ActiveSessionSnapshot
import com.lockerlift.core.model.ActiveSessionSnapshotCodec
import com.lockerlift.core.model.InstanceSets
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.QueueStatus
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.SyncStatus
import com.lockerlift.core.model.WorkoutLogic
import com.lockerlift.core.model.WorkoutSession
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.core.model.WorkoutTemplate
import com.lockerlift.core.sync.SessionMachineInstancePayload
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.SyncQueueWorker
import com.lockerlift.core.sync.WearableDataLayerManager
import com.lockerlift.core.sync.WorkoutSessionPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Immutable UI state of the mobile tracking screen.
 */
data class TrackingUiState(
    val isWorkoutActive: Boolean = false,
    val sessionId: String = "",
    val templateId: String? = null,
    val templateName: String? = null,
    val startTimeMillis: Long = 0L,
    val initialMachineIds: List<String> = emptyList(),
    val instances: List<SessionMachineInstance> = emptyList(),
    val setsByInstance: Map<String, List<WorkoutSet>> = emptyMap(),
    val resumedFromInterruption: Boolean = false,
    val elapsedSeconds: Int = 0,
    val restTimerRemainingSeconds: Int = 0,
    val isRestTimerRunning: Boolean = false
) {

    /**
     * Elapsed workout seconds derived from the session start time. Keeps the
     * value correct across configuration changes without a ticking counter.
     */
    fun deriveElapsedSeconds(nowMillis: Long = System.currentTimeMillis()): Int {
        if (!isWorkoutActive || startTimeMillis <= 0L) return 0
        return ((nowMillis - startTimeMillis) / 1000L).toInt().coerceAtLeast(0)
    }

    /**
     * Ticks the rest countdown by one second and stops it when it reaches
     * zero. Returns the next immutable state.
     */
    fun tickRestTimer(): TrackingUiState {
        if (!isRestTimerRunning || restTimerRemainingSeconds <= 0) return this
        val nextRemaining = restTimerRemainingSeconds - 1
        return if (nextRemaining <= 0) {
            copy(restTimerRemainingSeconds = 0, isRestTimerRunning = false)
        } else {
            copy(restTimerRemainingSeconds = nextRemaining)
        }
    }
}

/**
 * Owns the workout tracking state on the phone: start/recover, all session
 * mutations, and completion (local write, Health Connect export, sync to the
 * watch). The active session is persisted as a durable snapshot after every
 * mutation and restored automatically after process death.
 */
class TrackingViewModel(
    application: Application,
    private val database: LockerLiftDatabase
) : AndroidViewModel(application) {

    private val sessionDao = database.workoutSessionDao()
    private val machineDao = database.machineDao()
    private val templateDao = database.workoutTemplateDao()
    private val syncQueueDao = database.syncQueueDao()
    private val activeSessionRepository = ActiveSessionStateRepository(database.activeSessionStateDao())
    private val healthConnectManager = HealthConnectManager(application)

    private val persistMutex = Mutex()
    private var persistJob: Job? = null
    private var timerJob: Job? = null

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    /**
     * "Up next today" plan flows (Issue #51): exposed as UI state so the
     * UpNextPlanCard renders without touching the database directly.
     */
    val workoutPlans = database.workoutPlanDao().getAllPlansWithEntriesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activityTypes = database.activityTypeDao().getAllActivityTypesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val templates: StateFlow<List<WorkoutTemplate>> = templateDao.getAllActiveTemplatesWithMachinesFlow()
        .map { list -> list.map { it.template.toDomainModel() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val machines: StateFlow<List<Machine>> = machineDao.getAllMachinesFlow()
        .map { entities -> entities.map { it.toDomainModel() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var startupJob: Job? = null
    private var completionJob: Job? = null

    private val initJob: Job = viewModelScope.launch {
        // Crash recovery: an interrupted workout is restored automatically
        activeSessionRepository.load()?.let { snapshot -> restoreFromSnapshot(snapshot) }
    }

    // ---------------------------------------------------------------------
    // Session lifecycle
    // ---------------------------------------------------------------------

    fun startWorkout(template: WorkoutTemplate?) {
        startupJob = viewModelScope.launch {
            stopTimers()
            val now = System.currentTimeMillis()
            val sessionId = UUID.randomUUID().toString()
            val templateMachines = template?.let {
                templateDao.getMachinesForTemplateOrdered(it.id).map { entity -> entity.toDomainModel() }
            } ?: emptyList()
            val templateMachineIds = templateMachines.map { it.id }
            val instances = templateMachines.mapIndexed { index, machine ->
                SessionMachineInstance(
                    sessionId = sessionId,
                    machineId = machine.id,
                    executionOrder = index,
                    customSettingsNote = machine.machineSettingsNote
                )
            }
            _uiState.value = TrackingUiState(
                isWorkoutActive = true,
                sessionId = sessionId,
                templateId = template?.id,
                templateName = template?.name,
                startTimeMillis = System.currentTimeMillis(),
                initialMachineIds = templateMachineIds,
                instances = instances,
                setsByInstance = instances.associate { it.id to emptyList<WorkoutSet>() },
                elapsedSeconds = 0
            )
            startTimers()
            persist()
        }
    }

    // ---------------------------------------------------------------------
    // Timers (elapsed workout + rest countdown)
    // ---------------------------------------------------------------------

    /**
     * Starts the one-second UI tick that advances the elapsed workout timer
     * and the rest countdown. Timers live in the ViewModel, so they survive
     * configuration changes without restarting.
     */
    private fun startTimers() {
        stopTimers()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.value = _uiState.value.deriveElapsedSeconds().tickRestTimer()
            }
        }
    }

    private fun stopTimers() {
        timerJob?.cancel()
        timerJob = null
    }

    /** Starts the rest countdown after a completed set. */
    fun startRestTimer(seconds: Int) {
        if (!_uiState.value.isWorkoutActive || seconds <= 0) return
        _uiState.value = _uiState.value.copy(
            restTimerRemainingSeconds = seconds,
            isRestTimerRunning = true
        )
    }

    /** Adds extra seconds to a running rest countdown. */
    fun addRestTimerSeconds(seconds: Int) {
        val state = _uiState.value
        if (!state.isRestTimerRunning) return
        _uiState.value = state.copy(
            restTimerRemainingSeconds = (state.restTimerRemainingSeconds + seconds).coerceAtLeast(1)
        )
    }

    /** Stops and clears the rest countdown (skip). */
    fun skipRestTimer() {
        _uiState.value = _uiState.value.copy(
            restTimerRemainingSeconds = 0,
            isRestTimerRunning = false
        )
    }

    private fun restoreFromSnapshot(snapshot: ActiveSessionSnapshot) {
        _uiState.value = TrackingUiState(
            isWorkoutActive = true,
            sessionId = snapshot.sessionId,
            templateId = snapshot.templateId,
            templateName = snapshot.templateName,
            startTimeMillis = snapshot.startTimeMillis,
            initialMachineIds = snapshot.initialMachines.map { it.id },
            instances = snapshot.instances,
            setsByInstance = ActiveSessionSnapshotCodec.setsMapFromSnapshot(snapshot),
            resumedFromInterruption = true
        )
        startTimers()
    }

    private fun currentSnapshot(): ActiveSessionSnapshot {
        val state = _uiState.value
        return ActiveSessionSnapshot(
            sessionId = state.sessionId,
            templateId = state.templateId,
            templateName = state.templateName,
            startTimeMillis = state.startTimeMillis,
            originDevice = ORIGIN_DEVICE,
            initialMachines = state.initialMachineIds.map { id ->
                machines.value.find { it.id == id } ?: Machine(id = id, name = "Exercise", targetMuscleGroup = "General")
            },
            instances = state.instances,
            setsByInstance = state.setsByInstance.map { (instanceId, sets) ->
                InstanceSets(instanceId, sets)
            },
            pausedAtMillis = null,
            totalPausedMillis = 0L
        )
    }

    private fun persist() {
        persistJob = viewModelScope.launch {
            persistMutex.withLock {
                activeSessionRepository.save(currentSnapshot())
            }
        }
    }

    /**
     * Waits until all pending snapshot writes have been flushed to the DB.
     * Intended for tests to make durability assertions deterministic.
     */
    internal suspend fun awaitPersistence() {
        initJob.join()
        startupJob?.join()
        completionJob?.join()
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

    fun updateSet(instanceId: String, targetIndex: Int, weightKg: Float, reps: Int) {
        mutate { state ->
            val sets = state.setsByInstance[instanceId].orEmpty()
            val updated = WorkoutLogic.updateSetInList(sets, targetIndex, weightKg, reps)
            state.copy(setsByInstance = state.setsByInstance + (instanceId to updated))
        }
    }

    fun deleteSet(instanceId: String, targetIndex: Int) {
        mutate { state ->
            val sets = state.setsByInstance[instanceId].orEmpty()
            val renumbered = WorkoutLogic.deleteSetAndRenumber(sets, targetIndex)
            state.copy(setsByInstance = state.setsByInstance + (instanceId to renumbered))
        }
    }

    // ---------------------------------------------------------------------
    // Station mutations
    // ---------------------------------------------------------------------

    fun reorderInstance(fromIndex: Int, toIndex: Int) {
        mutate { state ->
            state.copy(instances = WorkoutLogic.reorderInstances(state.instances, fromIndex, toIndex))
        }
    }

    fun removeInstance(targetIndex: Int) {
        mutate { state ->
            state.copy(instances = WorkoutLogic.removeInstanceFromSession(state.instances, targetIndex))
        }
    }

    fun swapMachine(targetIndex: Int, machine: Machine) {
        mutate { state ->
            state.copy(instances = WorkoutLogic.swapMachinePreservingSets(state.instances, targetIndex, machine))
        }
    }

    fun appendMachine(machine: Machine) {
        mutate { state ->
            val appended = WorkoutLogic.appendMachineToSession(state.instances, state.sessionId, machine)
            state.copy(
                instances = state.instances + appended,
                setsByInstance = state.setsByInstance + (appended.id to emptyList())
            )
        }
    }

    /**
     * Persists a new machine to the catalog and appends it to the session.
     * The catalog insert runs on IO; the machine is visible immediately in
     * the session state.
     */
    fun createMachineAndAppend(machine: Machine) {
        viewModelScope.launch(Dispatchers.IO) {
            machineDao.insertMachine(machine.toEntity())
            withContext(Dispatchers.Main) {
                appendMachine(machine)
            }
        }
    }

    fun saveStationNote(targetIndex: Int, note: String?) {
        mutate { state ->
            val updated = state.instances.mapIndexed { index, instance ->
                if (index == targetIndex) instance.copy(customSettingsNote = note?.trim()?.ifEmpty { null }) else instance
            }
            state.copy(instances = updated)
        }
    }

    // ---------------------------------------------------------------------
    // Read helpers
    // ---------------------------------------------------------------------

    suspend fun getHistoricalSetsForMachine(machineId: String): List<WorkoutSetEntity> {
        return sessionDao.getLastCompletedSetsForMachine(machineId)
    }

    // ---------------------------------------------------------------------
    // Finish / discard
    // ---------------------------------------------------------------------

    /**
     * Completes the workout: writes the full session graph to the local DB,
     * optionally consolidates the template, exports to Health Connect,
     * enqueues the sync payload for the watch (Store-and-Forward) and clears
     * the durable snapshot.
     */
    fun finishWorkout(consolidateTemplate: Boolean, notes: String, onFinished: () -> Unit) {
        completionJob = viewModelScope.launch {
            val state = _uiState.value
            val session = WorkoutSession(
                id = state.sessionId,
                templateId = state.templateId,
                startTime = state.startTimeMillis,
                endTime = System.currentTimeMillis(),
                originDevice = ORIGIN_DEVICE,
                syncStatus = SyncStatus.PENDING_SYNC,
                notes = notes.trim().ifEmpty { null }
            )
            val allSets = state.setsByInstance.values.flatten()

            withContext(Dispatchers.IO) {
                sessionDao.upsertFullSession(
                    session = session.toEntity(),
                    instances = state.instances.map { it.toEntity() },
                    sets = allSets.map { it.toEntity() }
                )

                if (consolidateTemplate && state.templateId != null) {
                    val activeMachineIds = state.instances.filter { !it.isSkipped }.map { it.machineId }
                    val templateWithMachines = templateDao.getTemplateWithMachinesById(state.templateId)
                    val existingTemplate = templateWithMachines?.template
                    if (existingTemplate != null) {
                        templateDao.saveTemplateWithMachines(existingTemplate, activeMachineIds)
                    }
                }

                // Export to Health Connect (invariant 4: phone only)
                if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                    healthConnectManager.exportWorkoutSession(
                        session = session,
                        templateName = state.templateName
                    )
                }

                // Store-and-Forward transfer to the Wear OS companion
                val payload = WorkoutSessionPayload(
                    session = session,
                    templateName = state.templateName,
                    machineInstances = state.instances.map { instance ->
                        val machine = machines.value.find { it.id == instance.machineId }
                            ?: Machine(id = instance.machineId, name = "Exercise", targetMuscleGroup = "General")
                        SessionMachineInstancePayload(
                            instance = instance,
                            machine = machine,
                            sets = state.setsByInstance[instance.id].orEmpty()
                        )
                    }
                )
                syncQueueDao.insertQueueItem(
                    SyncQueueEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = session.id,
                        payloadJson = SyncPayloadSerializer.encodeSessionPayload(payload)
                    )
                )
            }

            val app = getApplication<Application>()
            SyncQueueWorker.enqueue(app)
            runCatching {
                WearableDataLayerManager(app).flushPendingQueue(syncQueueDao, SyncConstants.CAPABILITY_WEAR)
            }

            stopTimers()
            activeSessionRepository.clear()
            _uiState.value = TrackingUiState()
            withContext(Dispatchers.Main) {
                onFinished()
            }
        }
    }

    /**
     * Discards the workout without saving and clears the durable snapshot.
     */
    fun discardWorkout() {
        completionJob = viewModelScope.launch {
            stopTimers()
            activeSessionRepository.clear()
            _uiState.value = TrackingUiState()
        }
    }

    private inline fun mutate(reducer: (TrackingUiState) -> TrackingUiState) {
        _uiState.value = reducer(_uiState.value)
        persist()
    }

    companion object {
        const val ORIGIN_DEVICE = "MOBILE"
    }
}
