package com.lockerlift.core.model

import com.lockerlift.core.model.InsightsEngine.DayActivity
import com.lockerlift.core.model.InsightsEngine.MachineInstanceSnapshot
import com.lockerlift.core.model.InsightsEngine.SessionSnapshot
import com.lockerlift.core.model.InsightsEngine.SetSnapshot
import com.lockerlift.core.model.InsightsEngine.TrainingVerdict
import com.lockerlift.core.model.InsightsEngine.VolumePoint
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Unit test suite for the InsightsEngine analytics core (Insights Dashboard feature).
 * Adheres strictly to the AAA pattern and LockerLift unit testing guidelines.
 */
class InsightsEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private fun millisAt(date: LocalDate, hour: Int = 18): Long =
        ZonedDateTime.of(date.atTime(hour, 0), zone).toInstant().toEpochMilli()

    private fun set(weight: Float, reps: Int) = SetSnapshot(setNumber = 1, reps = reps, weightKg = weight)

    private fun instance(machineId: String, name: String, vararg sets: SetSnapshot) =
        MachineInstanceSnapshot(machineId, name, isSkipped = false, sets = sets.toList())

    private fun sessionAt(date: LocalDate, vararg instances: MachineInstanceSnapshot) =
        SessionSnapshot(
            sessionId = "session-$date",
            startTime = millisAt(date),
            endTime = millisAt(date) + 3_600_000L,
            machineInstances = instances.toList()
        )

    // --- trainingDays ---

    @Test
    fun testTrainingDays_groupsSessionsByLocalDateAndSumsVolume() {
        // Arrange
        val monday = LocalDate.of(2025, 6, 2)
        val sessions = listOf(
            sessionAt(monday, instance("m1", "Leg Press", set(100f, 10))),
            sessionAt(monday, instance("m2", "Bench", set(50f, 10))),
            sessionAt(monday.plusDays(1), instance("m1", "Leg Press", set(110f, 10)))
        )

        // Act
        val days = InsightsEngine.trainingDays(sessions, zone)

        // Assert
        assertEquals(2, days.size)
        val mondayActivity = days.first { it.date == monday }
        assertEquals(2, mondayActivity.sessionCount)
        assertEquals(1500f, mondayActivity.totalVolumeKg, 0.01f)
    }

    @Test
    fun testTrainingDays_ignoresUnfinishedSessionsAndWarmupSets() {
        // Arrange
        val date = LocalDate.of(2025, 6, 2)
        val warmup = SetSnapshot(setNumber = 1, reps = 20, weightKg = 20f, setType = SetType.WARMUP)
        val working = set(80f, 8)
        val unfinished = SessionSnapshot(
            sessionId = "active",
            startTime = millisAt(date),
            endTime = null,
            machineInstances = listOf(instance("m1", "Row", warmup, working))
        )

        // Act
        val days = InsightsEngine.trainingDays(listOf(unfinished), zone)

        // Assert
        assertTrue(days.isEmpty())
    }

    // --- buildCalendarMonth ---

    @Test
    fun testBuildCalendarMonth_june2025_startsSundayAndMarksTrainedDays() {
        // Arrange
        // June 1st 2025 is a Sunday; Monday-first grid needs 6 leading blanks
        val trained = LocalDate.of(2025, 6, 4)
        val sessions = listOf(sessionAt(trained, instance("m1", "Leg Press", set(100f, 10))))

        // Act
        val month = InsightsEngine.buildCalendarMonth(sessions, YearMonth.of(2025, 6), zone)

        // Assert
        assertEquals("2025-06", month.yearMonth)
        assertEquals(6, month.weeks.first().count { it == null })
        val trainedDay = month.weeks.first { week -> week.any { it?.date == trained } }
            .first { it?.date == trained }!!
        assertTrue(trainedDay.hasTrained)
        assertEquals(1000f, trainedDay.totalVolumeKg, 0.01f)
        val untrainedDay = month.weeks.flatten().first { it != null && it.date == LocalDate.of(2025, 6, 10) }!!
        assertFalse(untrainedDay.hasTrained)
    }

    // --- rateRegularity ---

    @Test
    fun testRateRegularity_tooLittle_whenBelowMinimum() {
        // Arrange
        val upTo = LocalDate.of(2025, 6, 30)
        val sessions = listOf(sessionAt(upTo.minusDays(20), instance("m1", "Leg Press", set(100f, 10))))

        // Act
        val result = InsightsEngine.rateRegularity(sessions, upTo, zone)

        // Assert
        assertEquals(TrainingVerdict.TOO_LITTLE, result.verdict)
        assertTrue(result.averageSessionsPerWeek < 1f)
    }

    @Test
    fun testRateRegularity_onTrack_whenBetweenMinAndMax() {
        // Arrange
        val upTo = LocalDate.of(2025, 6, 30)
        val sessions = (1..8).map { i ->
            sessionAt(upTo.minusDays(i * 3L), instance("m1", "Leg Press", set(100f, 10)))
        }

        // Act
        val result = InsightsEngine.rateRegularity(sessions, upTo, zone)

        // Assert
        assertEquals(TrainingVerdict.ON_TRACK, result.verdict)
    }

    @Test
    fun testRateRegularity_doMore_whenAboveMaximum() {
        // Arrange
        val upTo = LocalDate.of(2025, 6, 30)
        val sessions = (1..20).map { i ->
            sessionAt(upTo.minusDays(i.toLong()), instance("m1", "Leg Press", set(100f, 10)))
        }

        // Act
        val result = InsightsEngine.rateRegularity(sessions, upTo, zone)

        // Assert
        assertEquals(TrainingVerdict.DO_MORE, result.verdict)
        assertTrue(result.averageSessionsPerWeek > 4f)
    }

    @Test
    fun testRateRegularity_emptyHistory_isTooLittle() {
        // Arrange
        val upTo = LocalDate.of(2025, 6, 30)

        // Act
        val result = InsightsEngine.rateRegularity(emptyList(), upTo, zone)

        // Assert
        assertEquals(TrainingVerdict.TOO_LITTLE, result.verdict)
        assertEquals(0f, result.averageSessionsPerWeek, 0.001f)
    }

    // --- averageSessionsPerWeek ---

    @Test
    fun testAverageSessionsPerWeek_fourSessionsAcrossFourWeeks() {
        // Arrange
        val start = LocalDate.of(2025, 6, 2)
        val sessions = (0..3).map { i ->
            sessionAt(start.plusWeeks(i.toLong()), instance("m1", "Leg Press", set(100f, 10)))
        }

        // Act
        val average = InsightsEngine.averageSessionsPerWeek(sessions, zone)

        // Assert: span is 22 days = 3.14 weeks, so 4 sessions ≈ 1.27 per week
        assertEquals(1.27f, average, 0.05f)
    }

    // --- exerciseProgression ---

    @Test
    fun testExerciseProgression_detectsImprovement() {
        // Arrange
        val start = LocalDate.of(2025, 6, 2)
        val sessions = listOf(
            sessionAt(start, instance("m1", "Leg Press", set(100f, 10))),
            sessionAt(start.plusWeeks(1), instance("m1", "Leg Press", set(110f, 10))),
            sessionAt(start.plusWeeks(2), instance("m1", "Leg Press", set(120f, 10)))
        )

        // Act
        val progression = InsightsEngine.exerciseProgression(sessions, "m1", zone)!!

        // Assert
        assertEquals("Leg Press", progression.machineName)
        assertEquals(3, progression.points.size)
        assertTrue(progression.trend!!.hasImproved)
        assertEquals(1000f, progression.trend!!.firstVolumeKg, 0.01f)
        assertEquals(1200f, progression.trend!!.lastVolumeKg, 0.01f)
    }

    @Test
    fun testExerciseProgression_detectsRegression() {
        // Arrange
        val start = LocalDate.of(2025, 6, 2)
        val sessions = listOf(
            sessionAt(start, instance("m1", "Leg Press", set(120f, 10))),
            sessionAt(start.plusWeeks(1), instance("m1", "Leg Press", set(110f, 10))),
            sessionAt(start.plusWeeks(2), instance("m1", "Leg Press", set(100f, 10)))
        )

        // Act
        val progression = InsightsEngine.exerciseProgression(sessions, "m1", zone)!!

        // Assert
        assertFalse(progression.trend!!.hasImproved)
        assertTrue(progression.trend!!.changePercent < 0f)
    }

    @Test
    fun testExerciseProgression_skippedInstanceAndUnknownMachineExcluded() {
        // Arrange
        val date = LocalDate.of(2025, 6, 2)
        val skipped = MachineInstanceSnapshot("m1", "Leg Press", isSkipped = true, sets = listOf(set(100f, 10)))
        val sessions = listOf(
            SessionSnapshot("s1", millisAt(date), millisAt(date) + 1, listOf(skipped))
        )

        // Act
        val progression = InsightsEngine.exerciseProgression(sessions, "m1", zone)
        val otherMachine = InsightsEngine.exerciseProgression(sessions, "unknown", zone)

        // Assert
        assertNull(progression)
        assertNull(otherMachine)
    }

    // --- linearTrend ---

    @Test
    fun testLinearTrend_isNullForFewerThanTwoPoints() {
        // Arrange & Act
        val trend = InsightsEngine.linearTrend(listOf(100f))

        // Assert
        assertNull(trend)
    }

    @Test
    fun testLinearTrend_singleOutlierDoesNotDominate() {
        // Arrange: mostly flat with one spike at the start
        val values = listOf(5000f, 100f, 100f, 100f, 110f)

        // Act
        val trend = InsightsEngine.linearTrend(values)!!

        // Assert
        assertFalse(trend.hasImproved)
    }

    // --- overallVolumeProgression ---

    @Test
    fun testOverallVolumeProgression_returnsSortedVolumesPerSession() {
        // Arrange
        val start = LocalDate.of(2025, 6, 2)
        val sessions = listOf(
            sessionAt(start.plusWeeks(1), instance("m1", "Leg Press", set(120f, 10))),
            sessionAt(start, instance("m1", "Leg Press", set(100f, 10)))
        )

        // Act
        val points = InsightsEngine.overallVolumeProgression(sessions, zone)

        // Assert
        assertEquals(2, points.size)
        assertTrue(points[0].date.isBefore(points[1].date))
        assertEquals(1000f, points[0].volumeKg, 0.01f)
        assertEquals(1200f, points[1].volumeKg, 0.01f)
    }

    // --- personalRecords ---

    @Test
    fun testPersonalRecords_countsOnlyCompletedSessions() {
        // Arrange
        val date = LocalDate.of(2025, 6, 2)
        val sessions = listOf(
            sessionAt(date, instance("m1", "Leg Press", set(100f, 10))),
            SessionSnapshot("active", millisAt(date), null, listOf(instance("m1", "Leg Press", set(100f, 10))))
        )

        // Act
        val records = InsightsEngine.personalRecords(sessions)

        // Assert
        assertEquals(1, records.totalSessions)
        assertEquals(1000f, records.totalVolumeKg, 0.01f)
        assertEquals(1, records.totalSets)
    }

    // --- trainedMachines ---

    @Test
    fun testTrainedMachines_deduplicatesAndSortsByName() {
        // Arrange
        val date = LocalDate.of(2025, 6, 2)
        val sessions = listOf(
            sessionAt(date, instance("m2", "Bench", set(50f, 10))),
            sessionAt(date, instance("m1", "Leg Press", set(100f, 10)))
        )

        // Act
        val machines = InsightsEngine.trainedMachines(sessions)

        // Assert
        assertEquals(listOf("m2" to "Bench", "m1" to "Leg Press"), machines)
    }

    // --- DayActivity default ---

    @Test
    fun testDayActivity_untrainedDayHasNoActivity() {
        // Arrange
        val day = DayActivity(LocalDate.of(2025, 6, 2), 0, 0f)

        // Act & Assert
        assertFalse(day.hasTrained)
    }
}
