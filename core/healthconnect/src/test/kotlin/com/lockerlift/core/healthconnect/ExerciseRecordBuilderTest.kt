package com.lockerlift.core.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.units.Energy
import com.lockerlift.core.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ExerciseRecordBuilderTest {

    @Test
    fun testBuildExerciseSessionRecord_standardWithCustomZoneOffset() {
        val startTime = 1700000000000L
        val endTime = 1700003600000L
        val session = WorkoutSession(
            id = "session-1",
            startTime = startTime,
            endTime = endTime,
            notes = "Chest and Triceps focus"
        )
        val customOffset = ZoneOffset.ofHours(2)

        val record = ExerciseRecordBuilder.buildExerciseSessionRecord(
            session = session,
            title = "Push Day A",
            zoneOffset = customOffset
        )

        assertEquals(Instant.ofEpochMilli(startTime), record.startTime)
        assertEquals(Instant.ofEpochMilli(endTime), record.endTime)
        assertEquals(customOffset, record.startZoneOffset)
        assertEquals(customOffset, record.endZoneOffset)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, record.exerciseType)
        assertEquals("Push Day A", record.title)
        assertEquals("Chest and Triceps focus", record.notes)
    }

    @Test
    fun testBuildExerciseSessionRecord_defaultTitleAndDynamicZoneOffset() {
        val startTime = 1700000000000L
        val session = WorkoutSession(
            id = "session-2",
            startTime = startTime,
            endTime = null
        )

        val record = ExerciseRecordBuilder.buildExerciseSessionRecord(session)

        assertEquals(Instant.ofEpochMilli(startTime), record.startTime)
        assertTrue(!record.endTime.isBefore(record.startTime))
        assertEquals(ExerciseRecordBuilder.resolveZoneOffset(record.startTime), record.startZoneOffset)
        assertEquals(ExerciseRecordBuilder.resolveZoneOffset(record.endTime), record.endZoneOffset)
        assertEquals("LockerLift Krafttraining", record.title)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, record.exerciseType)
    }

    @Test
    fun testBuildTotalCaloriesBurnedRecord_matchingEnergyAndTimeBoundaries() {
        val startTime = 1700000000000L
        val endTime = 1700003600000L
        val energyKcal = 412.5
        val session = WorkoutSession(
            id = "session-3",
            startTime = startTime,
            endTime = endTime
        )
        val customOffset = ZoneOffset.ofHours(-5)

        val record = ExerciseRecordBuilder.buildTotalCaloriesBurnedRecord(
            session = session,
            energyKcal = energyKcal,
            zoneOffset = customOffset
        )

        assertEquals(Instant.ofEpochMilli(startTime), record.startTime)
        assertEquals(Instant.ofEpochMilli(endTime), record.endTime)
        assertEquals(customOffset, record.startZoneOffset)
        assertEquals(customOffset, record.endZoneOffset)
        assertEquals(energyKcal, record.energy.inKilocalories, 0.001)
        assertEquals(Energy.kilocalories(energyKcal), record.energy)
    }

    @Test
    fun testBuildTotalCaloriesBurnedRecord_nullEndTime() {
        val startTime = 1700000000000L
        val energyKcal = 250.0
        val session = WorkoutSession(
            id = "session-4",
            startTime = startTime,
            endTime = null
        )

        val record = ExerciseRecordBuilder.buildTotalCaloriesBurnedRecord(
            session = session,
            energyKcal = energyKcal
        )

        assertEquals(Instant.ofEpochMilli(startTime), record.startTime)
        assertTrue(!record.endTime.isBefore(record.startTime))
        assertEquals(ExerciseRecordBuilder.resolveZoneOffset(record.startTime), record.startZoneOffset)
        assertEquals(energyKcal, record.energy.inKilocalories, 0.001)
    }

    @Test
    fun testBuildHeartRateRecord_withSamplePointsAndCorrectRange() {
        val startTime = 1700000000000L
        val endTime = 1700003600000L
        val session = WorkoutSession(
            id = "session-5",
            startTime = startTime,
            endTime = endTime
        )
        val customOffset = ZoneOffset.UTC

        val t1 = Instant.ofEpochMilli(1700000600000L)
        val t2 = Instant.ofEpochMilli(1700001800000L)
        val t3 = Instant.ofEpochMilli(1700003000000L)

        val samples = listOf(
            Pair(t1, 120L),
            Pair(t2, 155L),
            Pair(t3, 110L)
        )

        val record = ExerciseRecordBuilder.buildHeartRateRecord(
            session = session,
            samples = samples,
            zoneOffset = customOffset
        )

        assertEquals(Instant.ofEpochMilli(startTime), record.startTime)
        assertEquals(Instant.ofEpochMilli(endTime), record.endTime)
        assertEquals(ZoneOffset.UTC, record.startZoneOffset)
        assertEquals(ZoneOffset.UTC, record.endZoneOffset)
        assertEquals(3, record.samples.size)

        assertEquals(t1, record.samples[0].time)
        assertEquals(120L, record.samples[0].beatsPerMinute)

        assertEquals(t2, record.samples[1].time)
        assertEquals(155L, record.samples[1].beatsPerMinute)

        assertEquals(t3, record.samples[2].time)
        assertEquals(110L, record.samples[2].beatsPerMinute)
    }

    @Test
    fun testBuildHeartRateRecord_enclosesSamplesOutsideSessionBounds() {
        val startTime = 1700001000000L
        val endTime = 1700002000000L
        val session = WorkoutSession(
            id = "session-6",
            startTime = startTime,
            endTime = endTime
        )

        val tEarlier = Instant.ofEpochMilli(1700000500000L)
        val tLater = Instant.ofEpochMilli(1700002500000L)

        val samples = listOf(
            Pair(tEarlier, 105L),
            Pair(tLater, 138L)
        )

        val record = ExerciseRecordBuilder.buildHeartRateRecord(
            session = session,
            samples = samples
        )

        assertEquals(tEarlier, record.startTime)
        assertEquals(tLater, record.endTime)
        assertEquals(2, record.samples.size)
        assertEquals(105L, record.samples[0].beatsPerMinute)
        assertEquals(138L, record.samples[1].beatsPerMinute)
    }

    @Test
    fun testBuildHeartRateRecord_emptySamples() {
        val startTime = 1700000000000L
        val endTime = 1700003600000L
        val session = WorkoutSession(
            id = "session-7",
            startTime = startTime,
            endTime = endTime
        )

        val record = ExerciseRecordBuilder.buildHeartRateRecord(
            session = session,
            samples = emptyList()
        )

        assertEquals(Instant.ofEpochMilli(startTime), record.startTime)
        assertEquals(Instant.ofEpochMilli(endTime), record.endTime)
        assertTrue(record.samples.isEmpty())
    }

    @Test
    fun testTimeOrderingSafeguard_whenEndTimeBeforeStartTime() {
        val startTime = 1700002000000L
        val corruptEndTime = 1700001000000L
        val session = WorkoutSession(
            id = "session-corrupt",
            startTime = startTime,
            endTime = corruptEndTime
        )

        val sessionRecord = ExerciseRecordBuilder.buildExerciseSessionRecord(session)
        assertTrue(!sessionRecord.endTime.isBefore(sessionRecord.startTime))

        val caloriesRecord = ExerciseRecordBuilder.buildTotalCaloriesBurnedRecord(session, 100.0)
        assertTrue(!caloriesRecord.endTime.isBefore(caloriesRecord.startTime))

        val hrRecord = ExerciseRecordBuilder.buildHeartRateRecord(session, emptyList())
        assertTrue(!hrRecord.endTime.isBefore(hrRecord.startTime))
    }
}
