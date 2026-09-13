package com.lockerlift.core.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
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

        return ExerciseSessionRecord(
            startTime = startInstant,
            startZoneOffset = ZoneOffset.UTC,
            endTime = endInstant,
            endZoneOffset = ZoneOffset.UTC,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = title,
            notes = session.notes,
            metadata = Metadata.manualEntry()
        )
    }
}
