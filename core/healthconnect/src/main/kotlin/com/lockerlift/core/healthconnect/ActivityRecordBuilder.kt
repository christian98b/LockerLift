package com.lockerlift.core.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.lockerlift.core.model.ActivityEntry
import com.lockerlift.core.model.ActivityType
import java.time.Instant
import java.time.ZoneOffset

/**
 * Builds an ExerciseSessionRecord for a logged non-gym activity (Issue #50).
 * Maps the activity to the closest Health Connect exercise type: running
 * becomes RUNNING, bouldering becomes ROCK_CLIMBING (bouldering is indoor
 * climbing), everything else falls back to WORKOUT.
 */
object ActivityRecordBuilder {

    fun resolveExerciseType(activityTypeName: String): Int {
        val name = activityTypeName.trim().lowercase()
        return when {
            "run" in name -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            "boulder" in name || "climb" in name -> ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING
            "hik" in name -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
            "swim" in name -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER
            "cycl" in name || "bike" in name -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            "yoga" in name -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
            else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }
    }

    fun buildActivitySessionRecord(
        entry: ActivityEntry,
        typeName: String,
        zoneOffset: ZoneOffset? = null
    ): ExerciseSessionRecord {
        val startInstant = Instant.ofEpochMilli(entry.date)
        // Point-in-time activity: minimum valid duration of one second
        val endInstant = startInstant.plusSeconds(1)
        val startOffset = ExerciseRecordBuilder.resolveZoneOffset(startInstant, zoneOffset)
        val endOffset = ExerciseRecordBuilder.resolveZoneOffset(endInstant, zoneOffset)
        return ExerciseSessionRecord(
            startTime = startInstant,
            startZoneOffset = startOffset,
            endTime = endInstant,
            endZoneOffset = endOffset,
            exerciseType = resolveExerciseType(typeName),
            title = typeName,
            notes = entry.notes,
            metadata = Metadata(
                clientRecordId = entry.id,
                recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY
            )
        )
    }
}
