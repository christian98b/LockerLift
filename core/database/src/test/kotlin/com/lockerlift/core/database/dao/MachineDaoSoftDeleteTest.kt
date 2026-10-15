package com.lockerlift.core.database.dao

import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.Machine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the machine soft-delete workflow (Issue #40): a deleted
 * machine disappears from catalog and selection queries but stays in the
 * database so workout history remains readable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MachineDaoSoftDeleteTest {

    private lateinit var database: LockerLiftDatabase

    @Before
    fun setup() {
        database = LockerLiftDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSoftDeleteMachine_hidesFromCatalogButKeepsRow() = runTest {
        val dao = database.machineDao()
        dao.insertMachine(Machine(id = "m-1", name = "Bench Press", targetMuscleGroup = "Chest").toEntity())

        dao.softDeleteMachine("m-1", System.currentTimeMillis())

        assertTrue(dao.getAllMachinesFlow().first().isEmpty())
        assertTrue(dao.getAllMachines().isEmpty())
        assertNull(dao.getMachineByName("Bench Press"))
        assertNotNull(dao.getMachineById("m-1"))
    }

    @Test
    fun testSoftDeleteMachine_allowsReusingNameForNewMachine() = runTest {
        val dao = database.machineDao()
        dao.insertMachine(Machine(id = "m-1", name = "Bench Press", targetMuscleGroup = "Chest").toEntity())
        dao.softDeleteMachine("m-1", System.currentTimeMillis())

        dao.insertMachine(Machine(id = "m-2", name = "Bench Press", targetMuscleGroup = "Chest").toEntity())

        val machines = dao.getAllMachines()
        assertEquals(1, machines.size)
        assertEquals("m-2", machines.first().id)
    }

    @Test
    fun testSoftDeleteMachine_isStillFindableIncludingDeletedForSyncProtection() = runTest {
        val dao = database.machineDao()
        dao.insertMachine(Machine(id = "m-1", name = "Bench Press", targetMuscleGroup = "Chest").toEntity())
        dao.softDeleteMachine("m-1", System.currentTimeMillis())

        val deleted = dao.getMachineByNameIncludingDeleted("Bench Press")

        assertNotNull(deleted)
        assertTrue(deleted!!.isDeleted)
    }
}
