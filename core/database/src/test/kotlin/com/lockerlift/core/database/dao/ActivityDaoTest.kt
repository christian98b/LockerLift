package com.lockerlift.core.database.dao

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.ActivityEntry
import com.lockerlift.core.model.ActivityFieldDefinition
import com.lockerlift.core.model.ActivityFieldType
import com.lockerlift.core.model.ActivityType
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
 * Unit tests for the activity type / activity entry DAOs and the
 * entry-with-type relation (Issue #50).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityDaoTest {

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

    private fun runningType() = ActivityType(
        id = "type-running",
        name = "Running",
        fields = listOf(
            ActivityFieldDefinition(id = "f-km", name = "km", type = ActivityFieldType.NUMBER),
            ActivityFieldDefinition(id = "f-time", name = "time")
        )
    )

    @Test
    fun testInsertAndReadActivityType_fieldsRoundTrip() = runTest {
        database.activityTypeDao().insertActivityType(runningType().toEntity())

        val loaded = database.activityTypeDao().getActivityTypeById("type-running")
        assertNotNull(loaded)
        assertEquals(runningType(), loaded!!.toDomainModel())
    }

    @Test
    fun testGetActivityTypesFlow_sortedByName() = runTest {
        val dao = database.activityTypeDao()
        dao.insertActivityType(ActivityType(id = "b", name = "Bouldering").toEntity())
        dao.insertActivityType(ActivityType(id = "a", name = "Running").toEntity())

        val names = dao.getAllActivityTypesFlow().first().map { it.name }
        assertEquals(listOf("Bouldering", "Running"), names)
    }

    @Test
    fun testGetActivityTypeByName_caseInsensitive() = runTest {
        database.activityTypeDao().insertActivityType(runningType().toEntity())

        val found = database.activityTypeDao().getActivityTypeByName("RUNNING")
        assertNotNull(found)
        assertEquals("type-running", found!!.id)
    }

    @Test
    fun testDeleteActivityType_builtinIsProtected() = runTest {
        val dao = database.activityTypeDao()
        dao.insertActivityType(
            ActivityType(id = "builtin-1", name = "Running", isBuiltin = true).toEntity()
        )
        dao.deleteActivityTypeById("builtin-1")
        assertNotNull(dao.getActivityTypeById("builtin-1"))
    }

    @Test
    fun testDeleteActivityType_customIsDeleted() = runTest {
        val dao = database.activityTypeDao()
        dao.insertActivityType(ActivityType(id = "custom-1", name = "Swimming").toEntity())
        dao.deleteActivityTypeById("custom-1")
        assertNull(dao.getActivityTypeById("custom-1"))
    }

    @Test
    fun testInsertEntry_valuesRoundTripAndRelation() = runTest {
        val entryDao = database.activityEntryDao()
        val typeDao = database.activityTypeDao()
        typeDao.insertActivityType(runningType().toEntity())

        val entry = ActivityEntry(
            id = "entry-1",
            activityTypeId = "type-running",
            date = 1700000000000L,
            fieldValues = mapOf("f-km" to "12.5", "f-time" to "55:30"),
            notes = "morning run"
        )
        entryDao.insertEntry(entry.toEntity())

        val loaded = entryDao.getEntryWithTypeById("entry-1")
        assertNotNull(loaded)
        assertEquals(entry, loaded!!.entry.toDomainModel())
        assertEquals("Running", loaded.type?.name)
    }

    @Test
    fun testGetAllEntriesWithTypeFlow_sortedByDateDesc() = runTest {
        val entryDao = database.activityEntryDao()
        val typeDao = database.activityTypeDao()
        typeDao.insertActivityType(runningType().toEntity())

        entryDao.insertEntry(
            ActivityEntry(id = "e-old", activityTypeId = "type-running", date = 1000L).toEntity()
        )
        entryDao.insertEntry(
            ActivityEntry(id = "e-new", activityTypeId = "type-running", date = 2000L).toEntity()
        )

        val ids = entryDao.getAllEntriesWithTypeFlow().first().map { it.entry.id }
        assertEquals(listOf("e-new", "e-old"), ids)
    }

    @Test
    fun testDeleteEntryById_removesEntry() = runTest {
        val entryDao = database.activityEntryDao()
        database.activityTypeDao().insertActivityType(runningType().toEntity())
        entryDao.insertEntry(
            ActivityEntry(id = "e-1", activityTypeId = "type-running").toEntity()
        )

        entryDao.deleteEntryById("e-1")
        assertNull(entryDao.getEntryById("e-1"))
    }

    @Test
    fun testDeleteType_cascadesToEntries() = runTest {
        val entryDao = database.activityEntryDao()
        val typeDao = database.activityTypeDao()
        typeDao.insertActivityType(ActivityType(id = "c-1", name = "Swimming").toEntity())
        entryDao.insertEntry(ActivityEntry(id = "e-1", activityTypeId = "c-1").toEntity())

        typeDao.deleteActivityTypeById("c-1")
        assertNull(entryDao.getEntryById("e-1"))
        assertTrue(entryDao.getEntriesByTypeId("c-1").isEmpty())
    }
}
