package com.lockerlift.core.database.dao

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.logic.ActivityTypeSeeder
import com.lockerlift.core.model.ActivityType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the built-in activity type seeding (Issue #50).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityTypeSeederTest {

    private lateinit var database: LockerLiftDatabase

    @Before
    fun setup() {
        database = LockerLiftDatabase.createInMemory(
            ApplicationProvider.getApplicationContext<Application>()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSeedIfMissing_seedsRunningAndBouldering() = runTest {
        ActivityTypeSeeder.seedIfMissing(database)

        val running = database.activityTypeDao().getActivityTypeById(ActivityType.BUILTIN_RUNNING_ID)
        val bouldering = database.activityTypeDao().getActivityTypeById(ActivityType.BUILTIN_BOULDERING_ID)
        assertNotNull(running)
        assertNotNull(bouldering)
        assertEquals("Running", running!!.name)
        assertEquals("Bouldering", bouldering!!.name)
        assertEquals(true, running.isBuiltin)
        // Running is seeded with km + time fields (owner comment on Issue #50)
        val runningDomain = running.toDomainModel()
        assertEquals(listOf("km", "time"), runningDomain.fields.map { it.name })
    }

    @Test
    fun testSeedIfMissing_isIdempotent() = runTest {
        ActivityTypeSeeder.seedIfMissing(database)
        ActivityTypeSeeder.seedIfMissing(database)

        val count = database.activityTypeDao().getAllActivityTypesFlow().first().size
        assertEquals(2, count)
    }
}
