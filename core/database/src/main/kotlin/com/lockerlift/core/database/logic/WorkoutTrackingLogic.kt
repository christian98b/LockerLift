package com.lockerlift.core.database.logic

import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.WorkoutLogic
import com.lockerlift.core.model.WorkoutSet

/**
 * Adapter layer between Room entities and the pure domain logic in
 * `com.lockerlift.core.model.WorkoutLogic`. Business rules live in `:core:model`;
 * this object only maps `WorkoutSetEntity` results from DAO queries to domain
 * models before delegating.
 */
object WorkoutTrackingLogic {

    /**
     * Extracts sets belonging strictly to the most recent completed session.
     * The input [sets] are assumed to be ordered by s.end_time DESC, ws.set_number ASC.
     */
    fun extractLastSessionSets(sets: List<WorkoutSetEntity>): List<WorkoutSetEntity> {
        return WorkoutLogic.extractLastSessionSets(sets.map { it.toDomainModel() })
            .map { it.toEntity() }
    }

    /**
     * Formats historical sets into a human-readable list string: e.g. "80 kg × 10, 80 kg × 9".
     */
    fun formatHistoricalSetsString(sets: List<WorkoutSetEntity>): String? {
        return WorkoutLogic.formatHistoricalSetsString(sets.map { it.toDomainModel() })
    }

    /**
     * Returns a full performance summary with a prefix, e.g. "Last: 80 kg × 10, 80 kg × 9".
     */
    fun formatPerformanceSummary(
        sets: List<WorkoutSetEntity>,
        prefix: String = "Last: "
    ): String? {
        return WorkoutLogic.formatPerformanceSummary(sets.map { it.toDomainModel() }, prefix)
    }

    /**
     * Determines prefill weight and reps for input from current domain sets and historical entity sets.
     */
    fun determinePrefillWeightAndReps(
        currentSets: List<WorkoutSet>,
        historicalSets: List<WorkoutSetEntity>,
        defaultWeight: Float = 60f,
        defaultReps: Int = 10
    ): Pair<Float, Int> {
        return WorkoutLogic.determinePrefillWeightAndReps(
            currentSets = currentSets,
            historicalSets = historicalSets.map { it.toDomainModel() },
            defaultWeight = defaultWeight,
            defaultReps = defaultReps
        )
    }
}
