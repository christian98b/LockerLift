package com.lockerlift.mobile.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the timer state transitions in TrackingUiState (AK 2.6):
 * - Elapsed time is derived from startTimeMillis
 * - Rest countdown transitions (tick, reach zero, skip, extend)
 */
class TrackingTimerStateTest {

    private val startMillis = 1_700_000_000_000L

    private fun activeState(
        startTimeMillis: Long = startMillis,
        restRemaining: Int = 0,
        restRunning: Boolean = false
    ) = TrackingUiState(
        isWorkoutActive = true,
        sessionId = "session-1",
        startTimeMillis = startTimeMillis,
        restTimerRemainingSeconds = restRemaining,
        isRestTimerRunning = restRunning
    )

    @Test
    fun testDeriveElapsedSeconds_computesFromStartTime() {
        // Arrange
        val state = activeState()

        // Act: 65 seconds later
        val elapsed = state.deriveElapsedSeconds(startMillis + 65_000L)

        // Assert
        assertEquals(65, elapsed)
    }

    @Test
    fun testDeriveElapsedSeconds_whenInactive_returnsZero() {
        // Arrange
        val state = TrackingUiState(isWorkoutActive = false, startTimeMillis = startMillis)

        // Act
        val elapsed = state.deriveElapsedSeconds(startMillis + 60_000L)

        // Assert
        assertEquals(0, elapsed)
    }

    @Test
    fun testDeriveElapsedSeconds_neverNegative() {
        // Arrange: clock slightly behind the start time
        val state = activeState()

        // Act
        val elapsed = state.deriveElapsedSeconds(startMillis - 5_000L)

        // Assert
        assertEquals(0, elapsed)
    }

    @Test
    fun testTickRestTimer_decrementsRemainingSeconds() {
        // Arrange
        val state = activeState(restRemaining = 90, restRunning = true)

        // Act
        val next = state.tickRestTimer()

        // Assert
        assertTrue(next.isRestTimerRunning)
        assertEquals(89, next.restTimerRemainingSeconds)
    }

    @Test
    fun testTickRestTimer_whenReachingZero_stopsTimer() {
        // Arrange
        val state = activeState(restRemaining = 1, restRunning = true)

        // Act
        val next = state.tickRestTimer()

        // Assert
        assertFalse(next.isRestTimerRunning)
        assertEquals(0, next.restTimerRemainingSeconds)
    }

    @Test
    fun testTickRestTimer_whenNotRunning_isNoOp() {
        // Arrange
        val state = activeState(restRemaining = 30, restRunning = false)

        // Act
        val next = state.tickRestTimer()

        // Assert
        assertEquals(state, next)
    }

    @Test
    fun testRestTimer_survivesElapsedDerivation() {
        // Arrange: a tick combines elapsed derivation and the rest tick
        val state = activeState(restRemaining = 10, restRunning = true)

        // Act
        val next = state.copy(elapsedSeconds = state.deriveElapsedSeconds(startMillis + 1000L))
            .tickRestTimer()

        // Assert: both timers advanced in the same tick
        assertEquals(1, next.elapsedSeconds)
        assertEquals(9, next.restTimerRemainingSeconds)
        assertTrue(next.isRestTimerRunning)
    }
}
