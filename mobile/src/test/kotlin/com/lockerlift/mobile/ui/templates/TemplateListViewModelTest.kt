package com.lockerlift.mobile.ui.templates

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.WorkoutTemplateEntity
import com.lockerlift.core.database.state.ActiveSessionStateRepository
import com.lockerlift.core.model.ActiveSessionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
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
 * Unit tests for the TemplateListViewModel (Issue #56): template creation,
 * reordering edit flow and the soft-deletion guard previously inlined in
 * the composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemplateListViewModelTest {

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

    private fun createViewModel(): TemplateListViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return TemplateListViewModel(application = app, database = database)
    }

    private suspend fun insertMachine(id: String, name: String) {
        database.machineDao().insertMachine(
            MachineEntity(id = id, name = name, targetMuscleGroup = "Chest")
        )
    }

    @Test
    fun testCreateTemplate_persistsTemplate() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.createTemplate(name = "Push Day", description = "  ")
        viewModel.awaitActions()

        val active = database.workoutTemplateDao()
            .getAllActiveTemplatesWithMachinesFlow().first()
        assertEquals(1, active.size)
        val stored = active.first()
        assertEquals("Push Day", stored.template.name)
        assertNull(stored.template.description)
    }

    @Test
    fun testSaveTemplate_persistsReorderedMachines() = runTest(testDispatcher) {
        insertMachine("m-1", "Bench")
        insertMachine("m-2", "Squat")
        database.workoutTemplateDao().insertTemplate(
            WorkoutTemplateEntity(id = "t-1", name = "Legs")
        )
        val viewModel = createViewModel()

        viewModel.saveTemplate(
            current = WorkoutTemplateEntity(id = "t-1", name = "Legs"),
            name = "Legs Day",
            description = "desc",
            machineIdsInOrder = listOf("m-2", "m-1")
        )
        viewModel.awaitActions()

        val ordered = database.workoutTemplateDao().getMachinesForTemplateOrdered("t-1")
        assertEquals(listOf("m-2", "m-1"), ordered.map { it.id })
        val stored = database.workoutTemplateDao().getTemplateWithMachinesById("t-1")
        assertEquals("Legs Day", stored!!.template.name)
    }

    @Test
    fun testDeleteTemplate_blockedWhileTemplateIsActive() = runTest(testDispatcher) {
        insertMachine("m-1", "Bench")
        database.workoutTemplateDao().insertTemplate(
            WorkoutTemplateEntity(id = "t-active", name = "Active")
        )
        database.workoutTemplateDao().saveTemplateWithMachines(
            template = WorkoutTemplateEntity(id = "t-active", name = "Active"),
            machineIdsInOrder = listOf("m-1")
        )
        ActiveSessionStateRepository(database.activeSessionStateDao()).save(
            ActiveSessionSnapshot(
                sessionId = "s-1",
                templateId = "t-active",
                startTimeMillis = 1000L,
                originDevice = "MOBILE"
            )
        )

        var result: TemplateDeleteResult? = null
        val viewModel = createViewModel()
        viewModel.deleteTemplate("t-active", "Active") { result = it }
        viewModel.awaitActions()

        assertTrue(result is TemplateDeleteResult.BlockedActiveWorkout)
        assertNotNull(database.workoutTemplateDao().getTemplateWithMachinesById("t-active"))
    }

    @Test
    fun testDeleteTemplate_softDeletesAndClearsCrossRefs() = runTest(testDispatcher) {
        insertMachine("m-1", "Bench")
        database.workoutTemplateDao().insertTemplate(
            WorkoutTemplateEntity(id = "t-1", name = "Old")
        )
        database.workoutTemplateDao().saveTemplateWithMachines(
            template = WorkoutTemplateEntity(id = "t-1", name = "Old"),
            machineIdsInOrder = listOf("m-1")
        )

        var result: TemplateDeleteResult? = null
        val viewModel = createViewModel()
        viewModel.deleteTemplate("t-1", "Old") { result = it }
        viewModel.awaitActions()

        assertTrue(result is TemplateDeleteResult.Deleted)
        val active = database.workoutTemplateDao()
            .getAllActiveTemplatesWithMachinesFlow().first()
        assertTrue(active.none { it.template.id == "t-1" })
        assertTrue(database.workoutTemplateDao().getMachinesForTemplateOrdered("t-1").isEmpty())
    }

    @Test
    fun testAssignedMachines_editOrderDraft() = runTest(testDispatcher) {
        insertMachine("m-1", "Bench")
        insertMachine("m-2", "Squat")
        val viewModel = createViewModel()
        viewModel.loadAssignedMachines("t-none")
        viewModel.awaitActions()
        viewModel.setAssignedMachines(
            database.machineDao().getAllMachines()
        )
        viewModel.addAssignedMachine(
            MachineEntity(id = "m-3", name = "Row", targetMuscleGroup = "Back")
        )
        assertEquals(3, viewModel.assignedMachines.value.size)
        assertEquals("m-3", viewModel.assignedMachines.value.last().id)
    }
}
