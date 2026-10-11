package com.lockerlift.wear.tracking

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.database.state.ActiveSessionStateRepository
import com.lockerlift.core.model.ActiveSessionSnapshot
import com.lockerlift.core.model.ActiveSessionSnapshotCodec
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.core.model.WorkoutTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the ActiveWorkoutViewModel session lifecycle:
 * - New session initialization from a template
 * - Crash recovery restore from the durable snapshot
 * - Snapshot persistence after every mutation (Locker Scenario durability)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveWorkoutViewModelTest {

    private lateinit var database: LockerLiftDatabase
    private lateinit var repository: ActiveSessionStateRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private val machineA = Machine(id = "machine-a", name = "Bench Press", targetMuscleGroup = "Chest")
    private val machineB = Machine(id = "machine-b", name = "Cable Row", targetMuscleGroup = "Back")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        database = LockerLiftDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repository = ActiveSessionStateRepository(database.activeSessionStateDao())
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun seedTemplate(): String {
        val template = WorkoutTemplate(id = "template-1", name = "Push Day")
        database.machineDao().insertMachine(machineA.toEntity())
        database.machineDao().insertMachine(machineB.toEntity())
        database.workoutTemplateDao().saveTemplateWithMachines(
            template = template.toEntity(),
            machineIdsInOrder = listOf(machineA.id, machineB.id)
        )
        return template.id
    }

    private fun createViewModel(templateId: String?): ActiveWorkoutViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return ActiveWorkoutViewModel(
            application = app,
            database = database,
            templateId = templateId,
            templateName = if (templateId != null) "Push Day" else null
        )
    }

    @Test
    fun testInit_whenNoSnapshotExists_startsNewSessionFromTemplate() = runTest(testDispatcher) {
        // Arrange
        val templateId = seedTemplate()

        // Act
        val viewModel = createViewModel(templateId)
        viewModel.awaitPersistence()
        val state = viewModel.uiState.value

        // Assert
        assertTrue(state.isWorkoutActive)
        assertEquals(2, state.instances.size)
        assertEquals(listOf(machineA.id, machineB.id), state.instances.map { it.machineId })
        assertTrue(state.setsByInstance.keys.containsAll(state.instances.map { it.id }))
        assertFalse(state.resumedFromInterruption)
    }

    @Test
    fun testInit_whenNoTemplateProvided_startsEmptyFreeWorkout() = runTest(testDispatcher) {
        // Act
        val viewModel = createViewModel(templateId = null)
        viewModel.awaitPersistence()
        val state = viewModel.uiState.value

        // Assert
        assertTrue(state.isWorkoutActive)
        assertTrue(state.instances.isEmpty())
        assertTrue(state.setsByInstance.isEmpty())
    }

    @Test
    fun testInit_whenSnapshotExists_restoresInterruptedSessionWithSets() = runTest(testDispatcher) {
        // Arrange: simulate an interrupted workout persisted before the VM exists
        seedTemplate()
        val interruptedInstanceId = "interrupted-instance-1"
        val interruptedSet = WorkoutSet(
            id = "set-1",
            sessionMachineId = interruptedInstanceId,
            setNumber = 1,
            reps = 10,
            weightKg = 80f
        )
        val snapshot = ActiveSessionSnapshot(
            sessionId = "interrupted-session",
            templateId = "template-1",
            templateName = "Push Day",
            startTimeMillis = 1_700_000_000_000L,
            originDevice = "WEAR_OS",
            initialMachines = listOf(machineA),
            instances = listOf(
                com.lockerlift.core.model.SessionMachineInstance(
                    id = interruptedInstanceId,
                    sessionId = "interrupted-session",
                    machineId = machineA.id,
                    executionOrder = 0
                )
            ),
            setsByInstance = listOf(
                com.lockerlift.core.model.InstanceSets(interruptedInstanceId, listOf(interruptedSet))
            )
        )
        repository.save(snapshot)

        // Act
        val viewModel = createViewModel("template-1")
        viewModel.awaitPersistence()
        val state = viewModel.uiState.value

        // Assert
        assertTrue(state.isWorkoutActive)
        assertTrue(state.resumedFromInterruption)
        assertEquals("interrupted-session", state.sessionId)
        assertEquals(1_700_000_000_000L, state.startTimeMillis)
        assertEquals(1, state.instances.size)
        assertEquals(listOf(interruptedSet), state.setsByInstance[interruptedInstanceId])
    }

    @Test
    fun testLogSet_persistsSnapshotToDurableStorage() = runTest(testDispatcher) {
        // Arrange
        val templateId = seedTemplate()
        val viewModel = createViewModel(templateId)
        viewModel.awaitPersistence()
        val instanceId = viewModel.uiState.value.instances.first().id

        // Act
        viewModel.logSet(
            instanceId = instanceId,
            set = WorkoutSet(
                id = "set-new",
                sessionMachineId = instanceId,
                setNumber = 1,
                reps = 10,
                weightKg = 80f
            )
        )
        viewModel.awaitPersistence()

        // Assert: the set survives in the durable snapshot
        val persisted = repository.load()
        assertNotNull(persisted)
        val persistedSets = ActiveSessionSnapshotCodec.setsMapFromSnapshot(persisted!!)
        assertEquals(1, persistedSets[instanceId]?.size)
        assertEquals(80f, persistedSets[instanceId]?.first()?.weightKg ?: 0f, 0.001f)
    }

    @Test
    fun testDeleteSet_persistsRenumberedSetsToDurableStorage() = runTest(testDispatcher) {
        // Arrange
        val templateId = seedTemplate()
        val viewModel = createViewModel(templateId)
        viewModel.awaitPersistence()
        val instanceId = viewModel.uiState.value.instances.first().id
        viewModel.logSet(instanceId, WorkoutSet(id = "s1", sessionMachineId = instanceId, setNumber = 1, reps = 10, weightKg = 60f))
        viewModel.logSet(instanceId, WorkoutSet(id = "s2", sessionMachineId = instanceId, setNumber = 2, reps = 9, weightKg = 62.5f))
        viewModel.awaitPersistence()

        // Act
        viewModel.deleteSet(instanceId, targetIndex = 0)
        viewModel.awaitPersistence()

        // Assert
        val persistedSets = ActiveSessionSnapshotCodec.setsMapFromSnapshot(repository.load()!!)
        val remaining = persistedSets[instanceId].orEmpty()
        assertEquals(1, remaining.size)
        assertEquals("s2", remaining.first().id)
        assertEquals(1, remaining.first().setNumber)
    }

    @Test
    fun testToggleSkipStation_persistsSkippedStateToDurableStorage() = runTest(testDispatcher) {
        // Arrange
        val templateId = seedTemplate()
        val viewModel = createViewModel(templateId)
        viewModel.awaitPersistence()

        // Act
        viewModel.toggleSkipStation(0)
        viewModel.awaitPersistence()

        // Assert
        val persisted = repository.load()!!
        assertTrue(persisted.instances.first().isSkipped)
        assertFalse(persisted.instances[1].isSkipped)
    }

    @Test
    fun testPauseResume_persistsPauseStateToDurableStorage() = runTest(testDispatcher) {
        // Arrange
        val templateId = seedTemplate()
        val viewModel = createViewModel(templateId)
        viewModel.awaitPersistence()

        // Act
        viewModel.pauseWorkout()
        viewModel.awaitPersistence()

        // Assert
        val pausedSnapshot = repository.load()!!
        assertNotNull(pausedSnapshot.pausedAtMillis)

        // Act: resume
        viewModel.resumeWorkout()
        viewModel.awaitPersistence()

        // Assert: resumed snapshot is no longer paused
        val resumedSnapshot = repository.load()!!
        assertNull(resumedSnapshot.pausedAtMillis)
        assertTrue(resumedSnapshot.totalPausedMillis >= 0L)
    }

    @Test
    fun testAppendMachine_addsInstanceAndPersistsIt() = runTest(testDispatcher) {
        // Arrange
        database.machineDao().insertMachine(machineA.toEntity())
        val viewModel = createViewModel(templateId = null)
        viewModel.awaitPersistence()

        // Act
        viewModel.appendMachine(machineA)
        viewModel.awaitPersistence()

        // Assert
        val state = viewModel.uiState.value
        assertEquals(1, state.instances.size)
        val persisted = repository.load()!!
        assertEquals(1, persisted.instances.size)
        assertEquals(machineA.id, persisted.instances.first().machineId)
    }

    @Test
    fun testSnapshot_afterRestoredSessionMutation_containsOriginalSessionId() = runTest(testDispatcher) {
        // Arrange: interrupted session with one set
        seedTemplate()
        val instanceId = "interrupted-instance-1"
        val snapshot = ActiveSessionSnapshot(
            sessionId = "interrupted-session",
            templateId = "template-1",
            templateName = "Push Day",
            startTimeMillis = 1_700_000_000_000L,
            originDevice = "WEAR_OS",
            instances = listOf(
                com.lockerlift.core.model.SessionMachineInstance(
                    id = instanceId,
                    sessionId = "interrupted-session",
                    machineId = machineA.id,
                    executionOrder = 0
                )
            )
        )
        repository.save(snapshot)

        // Act: resume and log another set
        val viewModel = createViewModel("template-1")
        viewModel.awaitPersistence()
        viewModel.logSet(
            instanceId = instanceId,
            set = WorkoutSet(id = "s2", sessionMachineId = instanceId, setNumber = 1, reps = 8, weightKg = 85f)
        )
        viewModel.awaitPersistence()

        // Assert: the durable snapshot still refers to the ORIGINAL session
        val persisted = repository.load()!!
        assertEquals("interrupted-session", persisted.sessionId)
        assertEquals(1_700_000_000_000L, persisted.startTimeMillis)
        assertEquals(1, ActiveSessionSnapshotCodec.setsMapFromSnapshot(persisted)[instanceId]?.size)
    }
}
