package com.lockerlift.core.database.logic

import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.WorkoutSet

enum class CreateMachineMode {
    ADD_NEW,
    REPLACE_CURRENT
}

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data object EmptyName : ValidationResult
    data object DuplicateName : ValidationResult
}

object WorkoutTrackingLogic {

    const val DEFAULT_PROGRESSION_REP_THRESHOLD = 12
    const val MIN_WEIGHT_KG = 0.0f
    const val MAX_WEIGHT_KG = 1000.0f
    const val MIN_REPS = 1
    const val MAX_REPS = 999

    /**
     * Formats weight cleanly, omitting decimal zero (e.g. 80.0 -> "80", 82.5 -> "82.5").
     */
    fun formatWeight(weight: Float): String {
        return if (weight % 1.0f == 0.0f) {
            weight.toInt().toString()
        } else {
            weight.toString()
        }
    }

    /**
     * Extracts sets belonging strictly to the most recent completed session.
     * The input [sets] are assumed to be ordered by s.end_time DESC, ws.set_number ASC.
     */
    fun extractLastSessionSets(sets: List<WorkoutSetEntity>): List<WorkoutSetEntity> {
        if (sets.isEmpty()) return emptyList()
        val latestSmiId = sets.first().sessionMachineId
        return sets.takeWhile { it.sessionMachineId == latestSmiId }
    }

    /**
     * Overload of extractLastSessionSets for domain models.
     */
    fun extractLastSessionSetsFromDomain(sets: List<WorkoutSet>): List<WorkoutSet> {
        if (sets.isEmpty()) return emptyList()
        val latestSmiId = sets.first().sessionMachineId
        return sets.takeWhile { it.sessionMachineId == latestSmiId }
    }

    /**
     * Formats historical sets into a human-readable list string: e.g. "80 kg × 10, 80 kg × 9".
     */
    fun formatHistoricalSetsString(sets: List<WorkoutSetEntity>): String? {
        val lastSessionSets = extractLastSessionSets(sets)
        if (lastSessionSets.isEmpty()) return null
        return lastSessionSets.joinToString(", ") {
            "${formatWeight(it.weightKg)} kg × ${it.reps}"
        }
    }

    /**
     * Overload of formatHistoricalSetsString for domain models.
     */
    fun formatDomainHistoricalSetsString(sets: List<WorkoutSet>): String? {
        val lastSessionSets = extractLastSessionSetsFromDomain(sets)
        if (lastSessionSets.isEmpty()) return null
        return lastSessionSets.joinToString(", ") {
            "${formatWeight(it.weightKg)} kg × ${it.reps}"
        }
    }

    /**
     * Returns a full performance summary with a prefix, e.g. "Last: 80 kg × 10, 80 kg × 9".
     */
    fun formatPerformanceSummary(
        sets: List<WorkoutSetEntity>,
        prefix: String = "Last: "
    ): String? {
        val setsString = formatHistoricalSetsString(sets) ?: return null
        return "$prefix$setsString"
    }

    /**
     * Overload for domain models.
     */
    fun formatDomainPerformanceSummary(
        sets: List<WorkoutSet>,
        prefix: String = "Last: "
    ): String? {
        val setsString = formatDomainHistoricalSetsString(sets) ?: return null
        return "$prefix$setsString"
    }

    /**
     * Double progression trigger check: suggests overload if reps hit or exceed threshold.
     */
    fun isProgressionProposed(reps: Int, threshold: Int = DEFAULT_PROGRESSION_REP_THRESHOLD): Boolean {
        return reps >= threshold
    }

    /**
     * Calculates the weight for the next set based on whether overload was accepted.
     */
    fun calculateNextWeight(currentWeight: Float, increment: Float, shouldProgress: Boolean): Float {
        val target = if (shouldProgress) currentWeight + increment else currentWeight
        return clampWeight(target)
    }

    /**
     * Validates a candidate machine name against empty text and existing machine names (case-insensitive).
     */
    fun validateNewMachine(name: String, existingMachineNames: List<String>): ValidationResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ValidationResult.EmptyName
        if (existingMachineNames.any { it.trim().equals(trimmed, ignoreCase = true) }) {
            return ValidationResult.DuplicateName
        }
        return ValidationResult.Valid
    }

    /**
     * Replaces a planned machine in the active session instances list (AK 3.3.2).
     */
    fun replaceMachineInSession(
        instances: List<SessionMachineInstance>,
        targetIndex: Int,
        newMachine: Machine
    ): List<SessionMachineInstance> {
        require(targetIndex in instances.indices) { "Invalid targetIndex: $targetIndex" }
        return instances.mapIndexed { index, instance ->
            if (index == targetIndex) {
                instance.copy(
                    machineId = newMachine.id,
                    customSettingsNote = newMachine.machineSettingsNote
                )
            } else {
                instance
            }
        }
    }

    /**
     * Appends an existing or newly created machine to the active session (AK 3.2.3).
     */
    fun appendMachineToSession(
        instances: List<SessionMachineInstance>,
        sessionId: String,
        machine: Machine
    ): SessionMachineInstance {
        return SessionMachineInstance(
            sessionId = sessionId,
            machineId = machine.id,
            executionOrder = instances.size,
            customSettingsNote = machine.machineSettingsNote
        )
    }

    /**
     * Toggles the skipped state of a station in the session (AK 3.3.1).
     */
    fun toggleSkipStation(
        instances: List<SessionMachineInstance>,
        targetIndex: Int
    ): List<SessionMachineInstance> {
        require(targetIndex in instances.indices) { "Invalid targetIndex: $targetIndex" }
        return instances.mapIndexed { index, instance ->
            if (index == targetIndex) {
                instance.copy(isSkipped = !instance.isSkipped)
            } else {
                instance
            }
        }
    }

    /**
     * Determines prefill weight and reps for input.
     */
    fun determinePrefillWeightAndReps(
        currentSets: List<WorkoutSet>,
        historicalSets: List<WorkoutSetEntity>,
        defaultWeight: Float = 60f,
        defaultReps: Int = 10
    ): Pair<Float, Int> {
        val lastCurrentSet = currentSets.lastOrNull()
        if (lastCurrentSet != null) {
            return Pair(clampWeight(lastCurrentSet.weightKg), clampReps(lastCurrentSet.reps))
        }

        val lastSessionSets = extractLastSessionSets(historicalSets)
        val firstHistoricalSet = lastSessionSets.firstOrNull()
        if (firstHistoricalSet != null) {
            return Pair(clampWeight(firstHistoricalSet.weightKg), clampReps(firstHistoricalSet.reps))
        }

        return Pair(clampWeight(defaultWeight), clampReps(defaultReps))
    }

    /**
     * Updates an existing set in the list with clamped weight and reps.
     */
    fun updateSetInList(
        sets: List<WorkoutSet>,
        targetIndex: Int,
        weightKg: Float,
        reps: Int,
        cadence: String? = null
    ): List<WorkoutSet> {
        if (targetIndex !in sets.indices) return sets
        val clampedWeight = clampWeight(weightKg)
        val clampedReps = clampReps(reps)
        return sets.mapIndexed { index, existingSet ->
            if (index == targetIndex) {
                existingSet.copy(
                    weightKg = clampedWeight,
                    reps = clampedReps,
                    cadence = cadence ?: existingSet.cadence
                )
            } else {
                existingSet
            }
        }
    }

    /**
     * Deletes a set at [targetIndex] and renumbers subsequent sets sequentially (1..N).
     */
    fun deleteSetAndRenumber(
        sets: List<WorkoutSet>,
        targetIndex: Int
    ): List<WorkoutSet> {
        if (targetIndex !in sets.indices) return sets
        return sets.filterIndexed { index, _ -> index != targetIndex }
            .mapIndexed { newIndex, set ->
                set.copy(setNumber = newIndex + 1)
            }
    }

    /**
     * Reorders session instances, moving an item from [fromIndex] to [toIndex] and re-indexing executionOrder.
     */
    fun reorderInstances(
        instances: List<SessionMachineInstance>,
        fromIndex: Int,
        toIndex: Int
    ): List<SessionMachineInstance> {
        if (fromIndex !in instances.indices || toIndex !in instances.indices || fromIndex == toIndex) return instances
        val mutable = instances.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable.mapIndexed { idx, inst -> inst.copy(executionOrder = idx) }
    }

    /**
     * Removes an instance from the session and re-indexes executionOrder.
     */
    fun removeInstanceFromSession(
        instances: List<SessionMachineInstance>,
        targetIndex: Int
    ): List<SessionMachineInstance> {
        if (targetIndex !in instances.indices) return instances
        return instances.filterIndexed { idx, _ -> idx != targetIndex }
            .mapIndexed { idx, inst -> inst.copy(executionOrder = idx) }
    }

    /**
     * Swaps the machine for an instance while keeping the instance ID and existing logged sets intact.
     */
    fun swapMachinePreservingSets(
        instances: List<SessionMachineInstance>,
        targetIndex: Int,
        newMachine: Machine
    ): List<SessionMachineInstance> {
        require(targetIndex in instances.indices) { "Invalid targetIndex: $targetIndex" }
        return instances.mapIndexed { index, instance ->
            if (index == targetIndex) {
                instance.copy(
                    machineId = newMachine.id,
                    customSettingsNote = newMachine.machineSettingsNote
                )
            } else {
                instance
            }
        }
    }

    /**
     * Finds the index of the next unfinished station (a station that is not skipped and has 0 logged sets).
     */
    fun findNextUnfinishedStationIndex(
        currentIndex: Int,
        instances: List<SessionMachineInstance>,
        loggedSets: Map<String, List<WorkoutSet>>
    ): Int? {
        if (instances.isEmpty()) return null
        for (i in (currentIndex + 1) until instances.size) {
            val inst = instances[i]
            if (!inst.isSkipped && (loggedSets[inst.id]?.isEmpty() != false)) {
                return i
            }
        }
        for (i in 0 until currentIndex) {
            val inst = instances[i]
            if (!inst.isSkipped && (loggedSets[inst.id]?.isEmpty() != false)) {
                return i
            }
        }
        return null
    }

    /**
     * Clamps weight to a realistic non-negative training range [0.0, 1000.0] kg (SEC-08).
     */
    fun clampWeight(weight: Float): Float = weight.coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)

    /**
     * Clamps reps to a valid non-zero training range [1, 999] (SEC-08).
     */
    fun clampReps(reps: Int): Int = reps.coerceIn(MIN_REPS, MAX_REPS)

    /**
     * Calculates weight stepper deltas based on the machine's [incrementKg].
     * Returns a Pair of (minusDeltas, plusDeltas).
     * Minus deltas are sorted ascending (e.g. [-5f, -2.5f, -1.25f]),
     * and plus deltas are sorted ascending (e.g. [1.25f, 2.5f, 5f]).
     */
    fun calculateWeightSteppers(incrementKg: Float = 2.5f): Pair<List<Float>, List<Float>> {
        val inc = if (incrementKg > 0f) incrementKg else 2.5f
        val steps = when {
            inc == 1.25f -> listOf(1.25f, 2.5f, 5.0f)
            inc == 1.0f -> listOf(1.0f, 2.0f, 5.0f)
            inc == 0.5f -> listOf(0.5f, 1.0f, 2.0f)
            else -> listOf(inc * 0.5f, inc * 1.0f, inc * 2.0f)
        }
        val minusSteps = steps.map { -it }.sorted()
        val plusSteps = steps.sorted()
        return Pair(minusSteps, plusSteps)
    }

    /**
     * Formats a delta weight value with explicit sign and clean precision (e.g. +5, -2.5, +1.25).
     */
    fun formatDelta(delta: Float): String {
        val absFormatted = formatWeight(kotlin.math.abs(delta))
        return when {
            delta > 0f -> "+$absFormatted"
            delta < 0f -> "-$absFormatted"
            else -> absFormatted
        }
    }
}
