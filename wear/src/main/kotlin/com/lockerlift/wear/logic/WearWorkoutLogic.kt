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
}
