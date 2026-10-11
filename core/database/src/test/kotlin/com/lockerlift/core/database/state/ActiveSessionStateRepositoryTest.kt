package com.lockerlift.core.database.state

import com.lockerlift.core.database.dao.ActiveSessionStateDao
import com.lockerlift.core.database.entity.ActiveSessionStateEntity
import com.lockerlift.core.model.ActiveSessionSnapshot
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SessionMachineInstance
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveSessionStateRepositoryTest {

    private class FakeDao : ActiveSessionStateDao {
        var stored: ActiveSessionStateEntity? = null

        override suspend fun upsert(state: ActiveSessionStateEntity) {
            stored = state
        }

        override suspend fun get(): ActiveSessionStateEntity? = stored

        override suspend fun clear() {
            stored = null
        }
    }

    private fun snapshot(sessionId: String = "session-1") = ActiveSessionSnapshot(
        sessionId = sessionId,
        templateId = null,
        templateName = "Free Workout",
        startTimeMillis = 1_700_000_000_000L,
        originDevice = "WEAR_OS",
        initialMachines = listOf(Machine(id = "m-1", name = "Leg Press", targetMuscleGroup = "Legs")),
        instances = listOf(
            SessionMachineInstance(id = "i-1", sessionId = sessionId, machineId = "m-1", executionOrder = 0)
        )
    )

    @Test
    fun testSave_thenLoad_returnsEqualSnapshot() = runTest {
        // Arrange
        val dao = FakeDao()
        val repository = ActiveSessionStateRepository(dao)
        val original = snapshot()

        // Act
        repository.save(original)
        val loaded = repository.load()

        // Assert
        assertEquals(original, loaded)
    }

    @Test
    fun testSave_whenSavedTwice_overwritesSingletonRow() = runTest {
        // Arrange
        val dao = FakeDao()
        val repository = ActiveSessionStateRepository(dao)

        // Act
        repository.save(snapshot(sessionId = "first"))
        repository.save(snapshot(sessionId = "second"))
        val loaded = repository.load()

        // Assert
        assertNotNull(loaded)
        assertEquals("second", loaded?.sessionId)
    }

    @Test
    fun testLoad_whenNothingSaved_returnsNull() = runTest {
        val repository = ActiveSessionStateRepository(FakeDao())

        assertNull(repository.load())
    }

    @Test
    fun testClear_afterSave_removesSnapshot() = runTest {
        // Arrange
        val dao = FakeDao()
        val repository = ActiveSessionStateRepository(dao)
        repository.save(snapshot())

        // Act
        repository.clear()

        // Assert
        assertNull(repository.load())
        assertNull(dao.stored)
    }

    @Test
    fun testLoad_whenPayloadCorrupted_returnsNullInsteadOfCrashing() = runTest {
        // Arrange: simulate a corrupted row, e.g. after an interrupted write
        val dao = FakeDao()
        dao.stored = ActiveSessionStateEntity(payloadJson = "{ not valid json")

        // Act
        val loaded = ActiveSessionStateRepository(dao).load()

        // Assert: recovery must fail soft, never crash the app startup
        assertNull(loaded)
    }
}
