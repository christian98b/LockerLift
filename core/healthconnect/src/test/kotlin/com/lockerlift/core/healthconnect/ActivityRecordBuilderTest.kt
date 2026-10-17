package com.lockerlift.core.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.lockerlift.core.model.ActivityEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for the Health Connect mapping of logged non-gym
 * activities (Issue #50).
 */
class ActivityRecordBuilderTest {

    @Test
    fun testResolveExerciseType_runningAndBoulderingAndFallback() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ActivityRecordBuilder.resolveExerciseType("Running")
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING,
            ActivityRecordBuilder.resolveExerciseType("Bouldering")
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            ActivityRecordBuilder.resolveExerciseType("Table Tennis")
        )
    }

    @Test
    fun testBuildActivitySessionRecord_pointInTimeActivity() {
        val date = 1700000000000L
        val entry = ActivityEntry(
            id = "activity-1",
            activityTypeId = "type-1",
            date = date,
            notes = "easy jog"
        )
        val record = ActivityRecordBuilder.buildActivitySessionRecord(entry, typeName = "Running")

        assertEquals(Instant.ofEpochMilli(date), record.startTime)
        assertEquals(Instant.ofEpochMilli(date).plusSeconds(1), record.endTime)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, record.exerciseType)
        assertEquals("Running", record.title)
        assertEquals("easy jog", record.notes)
        assertEquals("activity-1", record.metadata.clientRecordId)
    }
}
