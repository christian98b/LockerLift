package com.lockerlift.core.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import com.lockerlift.core.model.WorkoutSession
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object ExerciseRecordBuilder {

    /**
     * Resolves the ZoneOffset for a given Instant, using explicit override or system default rules.
     */
    fun resolveZoneOffset(instant: Instant, explicitOffset: ZoneOffset? = null): ZoneOffset {
        if (explicitOffset != null) return explicitOffset
        return runCatching {
            ZoneId.systemDefault().rules.getOffset(instant)
        }.getOrDefault(ZoneOffset.UTC)
    }

    fun buildExerciseSessionRecord(
        session: WorkoutSession,
        title: String = "LockerLift Krafttraining",
        zoneOffset: ZoneOffset? = null
    ): ExerciseSessionRecord {
        val startInstant = Instant.ofEpochMilli(session.startTime)
        val endInstant = session.endTime?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
        val finalEndInstant = if (!endInstant.isAfter(startInstant)) startInstant.plusSeconds(1) else endInstant

        val startOffset = resolveZoneOffset(startInstant, zoneOffset)
        val endOffset = resolveZoneOffset(finalEndInstant, zoneOffset)

        return ExerciseSessionRecord(
            startTime = startInstant,
            startZoneOffset = startOffset,
            endTime = finalEndInstant,
            endZoneOffset = endOffset,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = title,
            notes = session.notes,
            metadata = Metadata(
                clientRecordId = session.id,
                recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY
            )
        )
    }

    fun buildTotalCaloriesBurnedRecord(
        session: WorkoutSession,
        energyKcal: Double,
        zoneOffset: ZoneOffset? = null
    ): TotalCaloriesBurnedRecord {
        val startInstant = Instant.ofEpochMilli(session.startTime)
        val endInstant = session.endTime?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
        val finalEndInstant = if (!endInstant.isAfter(startInstant)) startInstant.plusSeconds(1) else endInstant

        val startOffset = resolveZoneOffset(startInstant, zoneOffset)
        val endOffset = resolveZoneOffset(finalEndInstant, zoneOffset)

        return TotalCaloriesBurnedRecord(
            startTime = startInstant,
            startZoneOffset = startOffset,
            endTime = finalEndInstant,
            endZoneOffset = endOffset,
            energy = Energy.kilocalories(energyKcal),
            metadata = Metadata(recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY)
        )
    }

    fun buildHeartRateRecord(
        session: WorkoutSession,
        samples: List<Pair<Instant, Long>>,
        zoneOffset: ZoneOffset? = null
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

        val endTime = if (!rawEnd.isAfter(startTime)) startTime.plusSeconds(1) else rawEnd

        val startOffset = resolveZoneOffset(startTime, zoneOffset)
        val endOffset = resolveZoneOffset(endTime, zoneOffset)

        val heartRateSamples = samples.map { (time, bpm) ->
            HeartRateRecord.Sample(
                time = time,
                beatsPerMinute = bpm
            )
        }

        return HeartRateRecord(
            startTime = startTime,
            startZoneOffset = startOffset,
            endTime = endTime,
            endZoneOffset = endOffset,
            samples = heartRateSamples,
            metadata = Metadata(recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY)
        )
    }
}
