package com.lockerlift.wear.logic

import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.logic.WorkoutTrackingLogic
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.WorkoutSet

typealias CreateMachineMode = com.lockerlift.core.database.logic.CreateMachineMode
typealias ValidationResult = com.lockerlift.core.database.logic.ValidationResult

object WearWorkoutLogic {

    const val DEFAULT_PROGRESSION_REP_THRESHOLD = WorkoutTrackingLogic.DEFAULT_PROGRESSION_REP_THRESHOLD
    const val MIN_WEIGHT_KG = WorkoutTrackingLogic.MIN_WEIGHT_KG
    const val MAX_WEIGHT_KG = WorkoutTrackingLogic.MAX_WEIGHT_KG
    const val MIN_REPS = WorkoutTrackingLogic.MIN_REPS
    const val MAX_REPS = WorkoutTrackingLogic.MAX_REPS

    const val DEFAULT_REST_DURATION_SECONDS = 90
    const val MIN_REST_SECONDS = 5
    const val MAX_REST_SECONDS = 600
    const val REST_ADJUSTMENT_STEP_SECONDS = 15
    val PRESET_REST_DURATIONS = listOf(30, 60, 90, 120, 180)

    fun formatWeight(weight: Float): String = WorkoutTrackingLogic.formatWeight(weight)

    fun extractLastSessionSets(sets: List<WorkoutSetEntity>): List<WorkoutSetEntity> =
        WorkoutTrackingLogic.extractLastSessionSets(sets)

    fun extractLastSessionSetsFromDomain(sets: List<WorkoutSet>): List<WorkoutSet> =
        WorkoutTrackingLogic.extractLastSessionSetsFromDomain(sets)

    fun formatHistoricalSetsString(sets: List<WorkoutSetEntity>): String? =
        WorkoutTrackingLogic.formatHistoricalSetsString(sets)

    fun formatDomainHistoricalSetsString(sets: List<WorkoutSet>): String? =
        WorkoutTrackingLogic.formatDomainHistoricalSetsString(sets)

    fun formatPerformanceSummary(sets: List<WorkoutSetEntity>, prefix: String = "Last: "): String? =
        WorkoutTrackingLogic.formatPerformanceSummary(sets, prefix)

    fun formatDomainPerformanceSummary(sets: List<WorkoutSet>, prefix: String = "Last: "): String? =
        WorkoutTrackingLogic.formatDomainPerformanceSummary(sets, prefix)

    fun isProgressionProposed(reps: Int, threshold: Int = DEFAULT_PROGRESSION_REP_THRESHOLD): Boolean =
        WorkoutTrackingLogic.isProgressionProposed(reps, threshold)

    fun calculateNextWeight(currentWeight: Float, increment: Float, shouldProgress: Boolean): Float =
        WorkoutTrackingLogic.calculateNextWeight(currentWeight, increment, shouldProgress)

    fun validateNewMachine(name: String, existingMachineNames: List<String>): ValidationResult =
        WorkoutTrackingLogic.validateNewMachine(name, existingMachineNames)

    fun replaceMachineInSession(
        instances: List<SessionMachineInstance>,
        targetIndex: Int,
        newMachine: Machine
    ): List<SessionMachineInstance> =
        WorkoutTrackingLogic.replaceMachineInSession(instances, targetIndex, newMachine)

    fun appendMachineToSession(
        instances: List<SessionMachineInstance>,
        sessionId: String,
        machine: Machine
    ): SessionMachineInstance =
        WorkoutTrackingLogic.appendMachineToSession(instances, sessionId, machine)

    fun toggleSkipStation(
        instances: List<SessionMachineInstance>,
        targetIndex: Int
    ): List<SessionMachineInstance> =
        WorkoutTrackingLogic.toggleSkipStation(instances, targetIndex)

    fun determinePrefillWeightAndReps(
        currentSets: List<WorkoutSet>,
        historicalSets: List<WorkoutSetEntity>,
        defaultWeight: Float = 60f,
        defaultReps: Int = 10
    ): Pair<Float, Int> =
        WorkoutTrackingLogic.determinePrefillWeightAndReps(currentSets, historicalSets, defaultWeight, defaultReps)

    fun clampWeight(weight: Float): Float = WorkoutTrackingLogic.clampWeight(weight)

    fun clampReps(reps: Int): Int = WorkoutTrackingLogic.clampReps(reps)

    fun initializeSessionInstances(
        templateMachines: List<Machine>?,
        sessionId: String
    ): List<SessionMachineInstance> {
        if (templateMachines.isNullOrEmpty()) return emptyList()
        return templateMachines.mapIndexed { index, machine ->
            SessionMachineInstance(
                sessionId = sessionId,
                machineId = machine.id,
                executionOrder = index,
                customSettingsNote = machine.machineSettingsNote
            )
        }
    }

    /**
     * Adjusts the rest timer duration by [deltaSeconds] while enforcing [minSeconds] and [maxSeconds] bounds (AK 3.9).
     */
    fun adjustRestSeconds(
        currentSeconds: Int,
        deltaSeconds: Int,
        minSeconds: Int = MIN_REST_SECONDS,
        maxSeconds: Int = MAX_REST_SECONDS
    ): Int {
        return (currentSeconds + deltaSeconds).coerceIn(minSeconds, maxSeconds)
    }

    /**
     * Updates an existing set in the list with clamped weight and reps (AK 3.11).
     */
    fun updateSetInList(
        sets: List<WorkoutSet>,
        targetIndex: Int,
        weightKg: Float,
        reps: Int,
        cadence: String? = null
    ): List<WorkoutSet> = WorkoutTrackingLogic.updateSetInList(sets, targetIndex, weightKg, reps, cadence)

    /**
     * Updates an existing set identified by ID with clamped weight and reps (AK 3.11).
     */
    fun updateSetById(
        sets: List<WorkoutSet>,
        setId: String,
        weightKg: Float,
        reps: Int,
        cadence: String? = null
    ): List<WorkoutSet> {
        val targetIndex = sets.indexOfFirst { it.id == setId }
        if (targetIndex == -1) return sets
        return updateSetInList(sets, targetIndex, weightKg, reps, cadence)
    }

    /**
     * Deletes a set from the list and renumbers subsequent sets sequentially (1, 2, 3...) (AK 3.12).
     */
    fun deleteSetAndRenumber(
        sets: List<WorkoutSet>,
        targetIndex: Int
    ): List<WorkoutSet> = WorkoutTrackingLogic.deleteSetAndRenumber(sets, targetIndex)

    /**
     * Deletes a set identified by ID and renumbers subsequent sets sequentially (AK 3.12).
     */
    fun deleteSetById(
        sets: List<WorkoutSet>,
        setId: String
    ): List<WorkoutSet> {
        val targetIndex = sets.indexOfFirst { it.id == setId }
        if (targetIndex == -1) return sets
        return deleteSetAndRenumber(sets, targetIndex)
    }

    /**
     * Transitions workout state to paused (AK 3.1).
     */
    fun pauseWorkout(
        state: WorkoutPauseState,
        currentTimeMillis: Long
    ): WorkoutPauseState {
        if (state.isPaused) return state
        return state.copy(
            isPaused = true,
            pausedAtMillis = currentTimeMillis
        )
    }

    /**
     * Transitions workout state to active, accumulating paused duration (AK 3.2).
     */
    fun resumeWorkout(
        state: WorkoutPauseState,
        currentTimeMillis: Long
    ): WorkoutPauseState {
        if (!state.isPaused || state.pausedAtMillis == null) return state
        val pauseDuration = maxOf(0L, currentTimeMillis - state.pausedAtMillis)
        return state.copy(
            isPaused = false,
            pausedAtMillis = null,
            totalPausedDurationMillis = state.totalPausedDurationMillis + pauseDuration
        )
    }

    /**
     * Calculates the adjusted session start time such that (endTime - adjustedStartTime)
     * excludes the total paused duration (AK 3.2).
     */
    fun calculateAdjustedStartTime(
        originalStartTime: Long,
        endTime: Long,
        totalPausedMillis: Long
    ): Long {
        val totalElapsed = maxOf(0L, endTime - originalStartTime)
        val clampedPause = totalPausedMillis.coerceIn(0L, totalElapsed)
        return originalStartTime + clampedPause
    }

    /**
     * Finds the index of the next non-skipped station in the session, or -1 if none remains (AK 3.5).
     */
    fun findNextStationIndex(
        currentIndex: Int,
        instances: List<SessionMachineInstance>
    ): Int {
        if (instances.isEmpty()) return -1
        for (i in (currentIndex + 1) until instances.size) {
            if (!instances[i].isSkipped) return i
        }
        return -1
    }

    /**
     * Returns true if there is at least one non-skipped station after [currentIndex] (AK 3.5).
     */
    fun hasNextStation(
        currentIndex: Int,
        instances: List<SessionMachineInstance>
    ): Boolean {
        return findNextStationIndex(currentIndex, instances) != -1
    }

    /**
     * Returns true if the set input screen should offer "Finish Exercise" instead of "Cancel".
     * Shown when logging an uncompleted set for a station that already has at least one logged set (setNumber > 1).
     */
    fun shouldShowFinishExerciseAction(isEditing: Boolean, setNumber: Int): Boolean {
        return !isEditing && setNumber > 1
    }
}

/**
 * State representing whether the active workout session is paused (AK 3.1, AK 3.2).
 */
data class WorkoutPauseState(
    val isPaused: Boolean = false,
    val pausedAtMillis: Long? = null,
    val totalPausedDurationMillis: Long = 0L
)
