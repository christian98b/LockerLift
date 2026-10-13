package com.lockerlift.core.database.dao

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.Machine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the distinct target muscle group query used by the
 * machine form autocomplete (Issue #36).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MachineDaoMuscleGroupTest {

    private lateinit var database: LockerLiftDatabase

    @Before
    fun setup() {
        database = LockerLiftDatabase.createInMemory(ApplicationProvider.getApplicationContext<Application>())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testGetAllTargetMuscleGroupsFlow_returnsDistinctSortedGroups() = runTest {
        database.machineDao().insertMachines(
            listOf(
                Machine(id = "m-1", name = "Bench Press", targetMuscleGroup = "Chest").toEntity(),
                Machine(id = "m-2", name = "Lat Pulldown", targetMuscleGroup = "Back").toEntity(),
                Machine(id = "m-3", name = "Chest Fly", targetMuscleGroup = "chest").toEntity()
            )
        )

        val groups = database.machineDao().getAllTargetMuscleGroupsFlow().first()

        assertEquals(listOf("Back", "Chest", "chest"), groups)
    }

    @Test
    fun testGetAllTargetMuscleGroupsFlow_whenCatalogEmpty_returnsEmptyList() = runTest {
        val groups = database.machineDao().getAllTargetMuscleGroupsFlow().first()

        assertEquals(emptyList<String>(), groups)
    }
}
