package com.lockerlift.mobile.ui.activities

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.ActivityEntryEntity
import com.lockerlift.core.database.entity.ActivityTypeEntity
import com.lockerlift.core.model.ActivityEntry
import com.lockerlift.core.model.ActivityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the ActivitiesViewModel (Issue #56): entry CRUD, type
 * creation and the type deletion flow (entries removed, Health Connect
 * cleanup invoked for every entry) previously inlined in the composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivitiesViewModelTest {

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

    private fun createViewModel(): ActivitiesViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return ActivitiesViewModel(application = app, database = database)
    }

    @Test
    fun testLogEntry_insertsEntryAndDelegatesExport() = runTest(testDispatcher) {
        var exportedEntry: ActivityEntry? = null
        var exportedTypeName: String? = null
        val entry = ActivityEntry(
            id = "a-1",
            activityTypeId = "t-1",
            date = 1000L,
            fieldValues = emptyMap(),
            notes = null
        )

        val viewModel = createViewModel()
        viewModel.logEntry(entry, "Running") { e, name ->
            exportedEntry = e
            exportedTypeName = name
        }
        viewModel.awaitActions()

        assertEquals("a-1", database.activityEntryDao().getEntryById("a-1")!!.id)
        assertEquals("a-1", exportedEntry!!.id)
        assertEquals("Running", exportedTypeName)
    }

    @Test
    fun testUpdateEntry_persistsNewValues() = runTest(testDispatcher) {
        database.activityTypeDao().insertActivityType(
            ActivityTypeEntity(id = "t-1", name = "Running")
        )
        database.activityEntryDao().insertEntry(
            ActivityEntryEntity(id = "a-1", activityTypeId = "t-1", date = 1000L)
        )
        val updated = ActivityEntry(
            id = "a-1",
            activityTypeId = "t-1",
            date = 2000L,
            fieldValues = emptyMap(),
            notes = "updated"
        )

        val viewModel = createViewModel()
        viewModel.updateEntry(updated, "Running") { _, _ -> }
        viewModel.awaitActions()

        val stored = database.activityEntryDao().getEntryById("a-1")
        assertEquals(2000L, stored!!.date)
        assertEquals("updated", stored.notes)
    }

    @Test
    fun testDeleteEntry_removesEntryAndDelegatesHealthConnect() = runTest(testDispatcher) {
        database.activityTypeDao().insertActivityType(
            ActivityTypeEntity(id = "t-1", name = "Running")
        )
        database.activityEntryDao().insertEntry(
            ActivityEntryEntity(id = "a-1", activityTypeId = "t-1", date = 1000L)
        )
        val deleted = mutableListOf<String>()

        val viewModel = createViewModel()
        viewModel.deleteEntry("a-1") { deleted.add(it) }
        viewModel.awaitActions()

        assertTrue(deleted.contains("a-1"))
        assertEquals(null, database.activityEntryDao().getEntryById("a-1"))
    }

    @Test
    fun testDeleteType_removesTypeEntriesAndDelegatesHealthConnect() = runTest(testDispatcher) {
        database.activityTypeDao().insertActivityType(
            ActivityTypeEntity(id = "t-1", name = "Running", fieldsJson = "[]")
        )
        database.activityEntryDao().insertEntry(
            ActivityEntryEntity(id = "a-1", activityTypeId = "t-1", date = 1000L)
        )
        database.activityEntryDao().insertEntry(
            ActivityEntryEntity(id = "a-2", activityTypeId = "t-1", date = 2000L)
        )
        val deleted = mutableListOf<String>()

        val viewModel = createViewModel()
        viewModel.deleteType(ActivityType(id = "t-1", name = "Running")) { deleted.add(it) }
        viewModel.awaitActions()

        assertEquals(setOf("a-1", "a-2"), deleted.toSet())
        assertEquals(null, database.activityTypeDao().getActivityTypeById("t-1"))
        assertTrue(database.activityEntryDao().getEntriesByTypeId("t-1").isEmpty())
    }

    @Test
    fun testCreateType_insertsType() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.createType(ActivityType(id = "t-1", name = "Cycling"))
        viewModel.awaitActions()
        assertEquals("Cycling", database.activityTypeDao().getActivityTypeById("t-1")!!.name)
    }
}
