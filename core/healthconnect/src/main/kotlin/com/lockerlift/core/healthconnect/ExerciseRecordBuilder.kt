package com.lockerlift.core.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import com.lockerlift.core.model.WorkoutSession
import java.time.Instant
import java.time.ZoneOffset

object ExerciseRecordBuilder {

    fun buildExerciseSessionRecord(
        session: WorkoutSession,
        title: String = "LockerLift Krafttraining"
    ): ExerciseSessionRecord {
        val startInstant = Instant.ofEpochMilli(session.startTime)
        val endInstant = session.endTime?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
        val finalEndInstant = if (endInstant.isBefore(startInstant)) startInstant else endInstant

        return ExerciseSessionRecord(
            startTime = startInstant,
            startZoneOffset = ZoneOffset.UTC,
            endTime = finalEndInstant,
            endZoneOffset = ZoneOffset.UTC,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = title,
            notes = session.notes,
            metadata = Metadata(recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY)
        )
    }

    fun buildTotalCaloriesBurnedRecord(
        session: WorkoutSession,
        energyKcal: Double
    ): TotalCaloriesBurnedRecord {
        val startInstant = Instant.ofEpochMilli(session.startTime)
        val endInstant = session.endTime?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
        val finalEndInstant = if (endInstant.isBefore(startInstant)) startInstant else endInstant

        return TotalCaloriesBurnedRecord(
            startTime = startInstant,
            startZoneOffset = ZoneOffset.UTC,
            endTime = finalEndInstant,
            endZoneOffset = ZoneOffset.UTC,
            energy = Energy.kilocalories(energyKcal),
            metadata = Metadata(recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY)
        )
    }

    fun buildHeartRateRecord(
        session: WorkoutSession,
        samples: List<Pair<Instant, Long>>
    ): HeartRateRecord {
        val sessionStart = Instant.ofEpochMilli(session.startTime)
        val sessionEnd = session.endTime?.let { Instant.ofEpochMilli(it) } ?: Instant.now()

        val startTime = if (samples.isNotEmpty()) {
            minOf(sessionStart, samples.minOf { it.first })
        } else {
            sessionStart
        }

        val rawEnd = if (samples.isNotEmpty()) {
            maxOf(sessionEnd, samples.maxOf { it.first })
        } else {
            sessionEnd
        }

        val endTime = if (rawEnd.isBefore(startTime)) startTime else rawEnd

        val heartRateSamples = samples.map { (time, bpm) ->
            HeartRateRecord.Sample(
                time = time,
                beatsPerMinute = bpm
            )
        }

        return HeartRateRecord(
            startTime = startTime,
            startZoneOffset = ZoneOffset.UTC,
            endTime = endTime,
            endZoneOffset = ZoneOffset.UTC,
            samples = heartRateSamples,
            metadata = Metadata(recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY)
        )
    }
}
