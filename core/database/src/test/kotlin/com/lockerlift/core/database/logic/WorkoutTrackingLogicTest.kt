package com.lockerlift.core.database.logic

import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the entity adapter seam in [WorkoutTrackingLogic]: Room entities coming
 * straight from DAO queries must produce identical results to the pure domain
 * logic in `com.lockerlift.core.model.WorkoutLogic`.
 */
class WorkoutTrackingLogicTest {

    private fun entitySet(
        sessionMachineId: String,
        setNumber: Int,
        reps: Int,
        weightKg: Float
    ) = WorkoutSetEntity(
        id = "set-$sessionMachineId-$setNumber",
        sessionMachineId = sessionMachineId,
        setNumber = setNumber,
        reps = reps,
        weightKg = weightKg,
        completedAt = 1_000L
    )

    @Test
    fun testExtractLastSessionSets_whenEntitiesFromTwoSessions_returnsOnlyLatest() {
        // Arrange: ordered as the DAO returns them (end_time DESC, set_number ASC)
        val entities = listOf(
            entitySet("smi-2", 1, 10, 80f),
            entitySet("smi-2", 2, 9, 80f),
            entitySet("smi-1", 1, 12, 75f)
        )

        // Act
        val result = WorkoutTrackingLogic.extractLastSessionSets(entities)

        // Assert
        assertEquals(2, result.size)
        assertTrue(result.all { it.sessionMachineId == "smi-2" })
    }

    @Test
    fun testFormatHistoricalSetsString_whenEntitiesPassed_formatsLikeDomainLogic() {
        val entities = listOf(
            entitySet("smi-1", 1, 10, 80f),
            entitySet("smi-1", 2, 9, 82.5f)
        )

        val result = WorkoutTrackingLogic.formatHistoricalSetsString(entities)

        assertEquals("80 kg × 10, 82.5 kg × 9", result)
    }

    @Test
    fun testFormatPerformanceSummary_whenEmpty_returnsNull() {
        assertNull(WorkoutTrackingLogic.formatPerformanceSummary(emptyList()))
    }

    @Test
    fun testFormatPerformanceSummary_whenEntitiesPassed_includesPrefix() {
        val entities = listOf(entitySet("smi-1", 1, 10, 80f))

        val result = WorkoutTrackingLogic.formatPerformanceSummary(entities, prefix = "Last: ")

        assertEquals("Last: 80 kg × 10", result)
    }

    @Test
    fun testDeterminePrefillWeightAndReps_whenNoCurrentButHistoricalEntities_prefillsFromHistory() {
        val historical = listOf(entitySet("smi-1", 1, 10, 80f))

        val (weight, reps) = WorkoutTrackingLogic.determinePrefillWeightAndReps(
            currentSets = emptyList(),
            historicalSets = historical
        )

        assertEquals(80f, weight, 0.001f)
        assertEquals(10, reps)
    }

    @Test
    fun testDeterminePrefillWeightAndReps_whenCurrentDomainSetsExist_prefillsFromCurrent() {
        val current = listOf(
            WorkoutSet(
                id = "cur-1",
                sessionMachineId = "smi-1",
                setNumber = 1,
                reps = 8,
                weightKg = 85f
            )
        )
        val historical = listOf(entitySet("smi-1", 1, 10, 80f))

        val (weight, reps) = WorkoutTrackingLogic.determinePrefillWeightAndReps(
            currentSets = current,
            historicalSets = historical
        )

        assertEquals(85f, weight, 0.001f)
        assertEquals(8, reps)
    }

    @Test
    fun testDeterminePrefillWeightAndReps_whenNextSetNumberExistsInHistory_prefillsFromMatchingHistoricalSet() {
        val current = listOf(
            WorkoutSet(
                id = "cur-1",
                sessionMachineId = "smi-cur",
                setNumber = 1,
                reps = 8,
                weightKg = 85f
            )
        )
        val historical = listOf(
            entitySet("smi-2", 1, 10, 80f),
            entitySet("smi-2", 2, 9, 82.5f)
        )

        val (weight, reps) = WorkoutTrackingLogic.determinePrefillWeightAndReps(
            currentSets = current,
            historicalSets = historical
        )

        assertEquals(82.5f, weight, 0.001f)
        assertEquals(9, reps)
    }

    @Test
    fun testDeterminePrefillWeightAndReps_whenHistoryExceeded_fallsBackToLastCurrentSet() {
        val current = listOf(
            WorkoutSet(
                id = "cur-1",
                sessionMachineId = "smi-cur",
                setNumber = 1,
                reps = 8,
                weightKg = 85f
            ),
            WorkoutSet(
                id = "cur-2",
                sessionMachineId = "smi-cur",
                setNumber = 2,
                reps = 7,
                weightKg = 90f
            )
        )
        val historical = listOf(
            entitySet("smi-2", 1, 10, 80f),
            entitySet("smi-2", 2, 9, 82.5f)
        )

        val (weight, reps) = WorkoutTrackingLogic.determinePrefillWeightAndReps(
            currentSets = current,
            historicalSets = historical
        )

        assertEquals(90f, weight, 0.001f)
        assertEquals(7, reps)
    }

    @Test
    fun testDeterminePrefillWeightAndReps_whenNoHistoryAtAll_returnsDefaults() {
        val (weight, reps) = WorkoutTrackingLogic.determinePrefillWeightAndReps(
            currentSets = emptyList(),
            historicalSets = emptyList(),
            defaultWeight = 60f,
            defaultReps = 10
        )

        assertNotNull(weight)
        assertEquals(60f, weight, 0.001f)
        assertEquals(10, reps)
    }
}
