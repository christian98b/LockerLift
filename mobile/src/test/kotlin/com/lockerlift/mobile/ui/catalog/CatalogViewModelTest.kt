package com.lockerlift.mobile.ui.catalog

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.Machine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the CatalogViewModel add/update validation and persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogViewModelTest {

    private lateinit var database: LockerLiftDatabase
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        database = LockerLiftDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CatalogViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return CatalogViewModel(application = app, database = database)
    }

    @Test
    fun testAddMachine_whenRequiredFieldsMissing_reportsEmptyFields() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        var result: MachineEditResult? = null

        viewModel.addMachine(name = "", muscleGroup = "Chest", settingsNote = "", incrementKgText = "2.5") {
            result = it
        }

        assertEquals(MachineEditResult.EMPTY_FIELDS, result)
        assertTrue(database.machineDao().getAllMachines().isEmpty())
    }

    @Test
    fun testAddMachine_whenNameValid_persistsMachine() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        var result: MachineEditResult? = null

        viewModel.addMachine(
            name = "Bench Press",
            muscleGroup = "Chest",
            settingsNote = "Seat 3",
            incrementKgText = "2.5"
        ) { result = it }
        viewModel.awaitActions()

        assertEquals(MachineEditResult.SUCCESS, result)
        val machines = database.machineDao().getAllMachines()
        assertEquals(1, machines.size)
        assertEquals("Bench Press", machines.first().name)
        assertEquals("Seat 3", machines.first().machineSettingsNote)
    }

    @Test
    fun testAddMachine_whenDuplicateName_reportsDuplicate() = runTest(testDispatcher) {
        // Arrange
        database.machineDao().insertMachine(
            Machine(id = "m-1", name = "Bench Press", targetMuscleGroup = "Chest").toEntity()
        )
        val viewModel = createViewModel()
        var result: MachineEditResult? = null

        // Act
        viewModel.addMachine(name = "bench press", muscleGroup = "Chest", settingsNote = "", incrementKgText = "2.5") {
            result = it
        }
        viewModel.awaitActions()

        // Assert: duplicate detection is case-insensitive on the trimmed name
        assertEquals(MachineEditResult.DUPLICATE_NAME, result)
        assertEquals(1, database.machineDao().getAllMachines().size)
    }

    @Test
    fun testUpdateMachine_whenRenamingToOwnName_succeeds() = runTest(testDispatcher) {
        // Arrange
        database.machineDao().insertMachine(
            Machine(id = "m-1", name = "Bench Press", targetMuscleGroup = "Chest").toEntity()
        )
        val viewModel = createViewModel()
        var result: MachineEditResult? = null

        // Act: rename to the same name (own id must not count as duplicate)
        viewModel.updateMachine(
            current = database.machineDao().getAllMachines().first(),
            name = "Bench Press",
            muscleGroup = "Chest",
            settingsNote = "Pin 4",
            incrementKgText = "5"
        ) { result = it }
        viewModel.awaitActions()

        // Assert
        assertEquals(MachineEditResult.SUCCESS, result)
        val updated = database.machineDao().getAllMachines().first()
        assertEquals("Pin 4", updated.machineSettingsNote)
        assertEquals(5f, updated.defaultIncrementKg, 0.001f)
    }

    @Test
    fun testUpdateMachine_whenAnotherMachineHasName_reportsDuplicate() = runTest(testDispatcher) {
        // Arrange
        database.machineDao().insertMachine(Machine(id = "m-1", name = "Bench Press", targetMuscleGroup = "Chest").toEntity())
        database.machineDao().insertMachine(Machine(id = "m-2", name = "Cable Row", targetMuscleGroup = "Back").toEntity())
        val viewModel = createViewModel()
        var result: MachineEditResult? = null

        // Act: rename m-2 to a name owned by m-1
        viewModel.updateMachine(
            current = database.machineDao().getMachineById("m-2")!!,
            name = "Bench Press",
            muscleGroup = "Back",
            settingsNote = "",
            incrementKgText = "2.5"
        ) { result = it }
        viewModel.awaitActions()

        // Assert
        assertEquals(MachineEditResult.DUPLICATE_NAME, result)
        assertEquals("Cable Row", database.machineDao().getMachineById("m-2")!!.name)
    }

    @Test
    fun testMachinesFlow_emitsInsertedMachines() = runTest(testDispatcher) {
        // Arrange
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.machines.collect {} }

        // Act
        viewModel.addMachine(name = "Lat Pulldown", muscleGroup = "Back", settingsNote = "", incrementKgText = "2.5") {}
        viewModel.awaitActions()

        // Assert
        assertEquals(1, viewModel.machines.value.size)
        assertEquals("Lat Pulldown", viewModel.machines.value.first().name)
        assertNull(viewModel.machines.value.first().machineSettingsNote)
    }
}
