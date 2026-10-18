package com.lockerlift.mobile.tracking

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.database.state.ActiveSessionStateRepository
import com.lockerlift.core.model.ActiveSessionSnapshot
import com.lockerlift.core.model.ActiveSessionSnapshotCodec
import com.lockerlift.core.model.InstanceSets
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SessionMachineInstance
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
 * Unit tests for the TrackingViewModel session lifecycle:
 * - Start from template / free workout
 * - Crash recovery restore from the durable snapshot
 * - Snapshot persistence after mutations
 * - Finish (full session write + sync queue entry + snapshot clear) and discard
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingViewModelTest {

    private lateinit var database: LockerLiftDatabase
    private lateinit var repository: ActiveSessionStateRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private val machineA = Machine(
        id = "machine-a",
        name = "Bench Press",
        targetMuscleGroup = "Chest",
        machineSettingsNote = "Seat 3"
    )
    private val machineB = Machine(id = "machine-b", name = "Cable Row", targetMuscleGroup = "Back")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        database = LockerLiftDatabase.createInMemory(context)
        repository = ActiveSessionStateRepository(database.activeSessionStateDao())
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun seedTemplate(): WorkoutTemplate {
        val template = WorkoutTemplate(id = "template-1", name = "Push Day")
        database.machineDao().insertMachine(machineA.toEntity())
        database.machineDao().insertMachine(machineB.toEntity())
        database.workoutTemplateDao().saveTemplateWithMachines(
            template = template.toEntity(),
            machineIdsInOrder = listOf(machineA.id, machineB.id)
        )
        return template
    }

    private fun createViewModel(): TrackingViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return TrackingViewModel(application = app, database = database)
    }

    @Test
    fun testStartWorkout_whenTemplateGiven_initializesInstancesWithSettingsNotes() = runTest(testDispatcher) {
        // Arrange
        val template = seedTemplate()
        val viewModel = createViewModel()
        viewModel.awaitPersistence()

        // Act
        viewModel.startWorkout(template)
        viewModel.awaitPersistence()
        val state = viewModel.uiState.value

        // Assert
        assertTrue(state.isWorkoutActive)
        assertEquals(2, state.instances.size)
        assertEquals("Seat 3", state.instances.first().customSettingsNote)
        assertEquals(listOf(machineA.id, machineB.id), state.initialMachineIds)
        assertTrue(state.setsByInstance.keys.containsAll(state.instances.map { it.id }))

        // Snapshot persisted with the same session id
        assertEquals(state.sessionId, repository.load()?.sessionId)
    }

    @Test
    fun testStartWorkout_whenNoTemplate_startsEmptyFreeWorkout() = runTest(testDispatcher) {
        // Arrange
        val viewModel = createViewModel()
        viewModel.awaitPersistence()

        // Act
        viewModel.startWorkout(null)
        viewModel.awaitPersistence()
        val state = viewModel.uiState.value

        // Assert
        assertTrue(state.isWorkoutActive)
        assertNull(state.templateId)
        assertTrue(state.instances.isEmpty())
    }

    @Test
    fun testInit_whenSnapshotExists_restoresInterruptedSession() = runTest(testDispatcher) {
        // Arrange: durable snapshot from an interrupted free workout
        seedTemplate()
        val instanceId = "interrupted-instance"
        val set = WorkoutSet(id = "set-1", sessionMachineId = instanceId, setNumber = 1, reps = 8, weightKg = 60f)
        repository.save(
            ActiveSessionSnapshot(
                sessionId = "interrupted-session",
                templateId = null,
                templateName = "Free Workout",
                startTimeMillis = 1_700_000_000_000L,
                originDevice = "MOBILE",
                initialMachines = listOf(machineA),
                instances = listOf(
                    SessionMachineInstance(
                        id = instanceId,
                        sessionId = "interrupted-session",
                        machineId = machineA.id,
                        executionOrder = 0
                    )
                ),
                setsByInstance = listOf(InstanceSets(instanceId, listOf(set)))
            )
        )

        // Act
        val viewModel = createViewModel()
        viewModel.awaitPersistence()
        val state = viewModel.uiState.value

        // Assert
        assertTrue(state.isWorkoutActive)
        assertTrue(state.resumedFromInterruption)
        assertEquals("interrupted-session", state.sessionId)
        assertEquals(listOf(machineA.id), state.initialMachineIds)
        assertEquals(listOf(set), state.setsByInstance[instanceId])
    }

    @Test
    fun testLogSet_thenSnapshotContainsLoggedSet() = runTest(testDispatcher) {
        // Arrange
        val template = seedTemplate()
        val viewModel = createViewModel()
        viewModel.awaitPersistence()
        viewModel.startWorkout(template)
        viewModel.awaitPersistence()
        val instanceId = viewModel.uiState.value.instances.first().id

        // Act
        viewModel.logSet(
            instanceId = instanceId,
            set = WorkoutSet(id = "s1", sessionMachineId = instanceId, setNumber = 1, reps = 10, weightKg = 80f)
        )
        viewModel.awaitPersistence()

        // Assert
        val persistedSets = ActiveSessionSnapshotCodec.setsMapFromSnapshot(repository.load()!!)
        assertEquals(1, persistedSets[instanceId]?.size)
    }

    @Test
    fun testDeleteSet_thenSnapshotHasRenumberedSets() = runTest(testDispatcher) {
        // Arrange
        val template = seedTemplate()
        val viewModel = createViewModel()
        viewModel.awaitPersistence()
        viewModel.startWorkout(template)
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
        assertEquals(listOf("s2"), remaining.map { it.id })
        assertEquals(1, remaining.first().setNumber)
    }

    @Test
    fun testReorderAndSwap_persistInstanceChanges() = runTest(testDispatcher) {
        // Arrange
        val machineC = Machine(id = "machine-c", name = "Leg Press", targetMuscleGroup = "Legs")
        database.machineDao().insertMachine(machineC.toEntity())
        val template = seedTemplate()
        val viewModel = createViewModel()
        viewModel.awaitPersistence()
        viewModel.startWorkout(template)
        viewModel.awaitPersistence()

        // Act: swap station 1 to machine C, then move it below station 2
        viewModel.swapMachine(0, machineC)
        viewModel.reorderInstance(0, 1)
        viewModel.awaitPersistence()

        // Assert: durable snapshot reflects [machine-b, machine-c]
        val persisted = repository.load()!!
        assertEquals(listOf(machineB.id, machineC.id), persisted.instances.map { it.machineId })
    }

    @Test
    fun testAppendMachine_persistsNewInstance() = runTest(testDispatcher) {
        // Arrange
        database.machineDao().insertMachine(machineA.toEntity())
        val viewModel = createViewModel()
        viewModel.awaitPersistence()
        viewModel.startWorkout(null)
        viewModel.awaitPersistence()

        // Act
        viewModel.appendMachine(machineA)
        viewModel.awaitPersistence()

        // Assert
        val persisted = repository.load()!!
        assertEquals(1, persisted.instances.size)
        assertEquals(machineA.id, persisted.instances.first().machineId)
    }

    @Test
    fun testDiscardWorkout_clearsSnapshotAndResetsState() = runTest(testDispatcher) {
        // Arrange
        val viewModel = createViewModel()
        viewModel.awaitPersistence()
        viewModel.startWorkout(null)
        viewModel.awaitPersistence()

        // Act
        viewModel.discardWorkout()
        viewModel.awaitPersistence()

        // Assert
        assertNull(repository.load())
        assertFalse(viewModel.uiState.value.isWorkoutActive)
    }

    @Test
    fun testFinishWorkout_writesSessionGraphAndClearsSnapshot() = runTest(testDispatcher) {
        // Arrange
        val template = seedTemplate()
        val viewModel = createViewModel()
        viewModel.awaitPersistence()
        viewModel.startWorkout(template)
        viewModel.awaitPersistence()
        val instanceId = viewModel.uiState.value.instances.first().id
        viewModel.logSet(
            instanceId,
            WorkoutSet(id = "s1", sessionMachineId = instanceId, setNumber = 1, reps = 10, weightKg = 80f)
        )
        viewModel.awaitPersistence()
        val sessionId = viewModel.uiState.value.sessionId
        var finished = false

        // Act
        viewModel.finishWorkout(consolidateTemplate = false, notes = "Felt strong") { finished = true }
        viewModel.awaitPersistence()

        // Assert: full session graph is in the DB
        val withDetails = database.workoutSessionDao().getSessionWithDetailsById(sessionId)
        assertNotNull(withDetails)
        assertEquals("Felt strong", withDetails?.session?.toDomainModel()?.notes)
        assertEquals(1, withDetails?.machineInstances?.first()?.sets?.size)
        assertEquals(1, database.syncQueueDao().getPendingQueueItems().size)
        // Durable snapshot is cleared and the workout is inactive
        assertNull(repository.load())
        assertFalse(viewModel.uiState.value.isWorkoutActive)
        assertTrue(finished)
    }

    @Test
    fun testStartRestTimer_whenWorkoutActive_runsCountdown() = runTest(testDispatcher) {
        // Arrange
        val viewModel = createViewModel()
        viewModel.awaitPersistence()
        viewModel.startWorkout(null)
        viewModel.awaitPersistence()

        // Act
        viewModel.startRestTimer(90)

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state.isRestTimerRunning)
        assertEquals(90, state.restTimerRemainingSeconds)
    }

    @Test
    fun testAddRestTimerSeconds_extendsRunningCountdown() = runTest(testDispatcher) {
        // Arrange
        val viewModel = createViewModel()
        viewModel.awaitPersistence()
        viewModel.startWorkout(null)
        viewModel.awaitPersistence()
        viewModel.startRestTimer(30)

        // Act
        viewModel.addRestTimerSeconds(15)

        // Assert
        assertEquals(45, viewModel.uiState.value.restTimerRemainingSeconds)
    }

    @Test
    fun testSkipRestTimer_clearsCountdown() = runTest(testDispatcher) {
        // Arrange
        val viewModel = createViewModel()
        viewModel.awaitPersistence()
        viewModel.startWorkout(null)
        viewModel.awaitPersistence()
        viewModel.startRestTimer(90)

        // Act
        viewModel.skipRestTimer()

        // Assert
        val state = viewModel.uiState.value
        assertFalse(state.isRestTimerRunning)
        assertEquals(0, state.restTimerRemainingSeconds)
    }

    @Test
    fun testRestTimer_ignoredWhenNoWorkoutActive() = runTest(testDispatcher) {
        // Arrange
        val viewModel = createViewModel()
        viewModel.awaitPersistence()

        // Act
        viewModel.startRestTimer(90)

        // Assert
        assertFalse(viewModel.uiState.value.isRestTimerRunning)
    }
}
