package com.lockerlift.core.database.dao

import androidx.test.core.app.ApplicationProvider
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.TemplateMachineCrossRefEntity
import com.lockerlift.core.database.entity.WorkoutTemplateEntity
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.Machine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the template soft-delete workflow (Issue #40): a deleted
 * template disappears from all lists and its machine cross-refs are removed,
 * while the row itself is retained because historical sessions reference
 * templates by id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutTemplateDaoSoftDeleteTest {

    private lateinit var database: LockerLiftDatabase

    @Before
    fun setup() {
        database = LockerLiftDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertTemplateWithMachine(templateId: String, machineId: String) {
        val machineDao = database.machineDao()
        val templateDao = database.workoutTemplateDao()
        machineDao.insertMachine(
            Machine(id = machineId, name = "Machine $machineId", targetMuscleGroup = "Chest").toEntity()
        )
        templateDao.insertTemplate(
            WorkoutTemplateEntity(id = templateId, name = "Template $templateId")
        )
        templateDao.insertCrossRefs(
            listOf(
                TemplateMachineCrossRefEntity(templateId = templateId, machineId = machineId, sortOrder = 0)
            )
        )
    }

    @Test
    fun testSoftDeleteTemplate_hidesFromListsButKeepsRow() = runTest {
        val templateDao = database.workoutTemplateDao()
        insertTemplateWithMachine("t-1", "m-1")

        templateDao.softDeleteTemplateWithCrossRefs("t-1")

        assertTrue(templateDao.getAllActiveTemplatesWithMachinesFlow().first().isEmpty())
        assertTrue(templateDao.getActiveTemplateIds().isEmpty())
        assertNotNull(templateDao.getTemplateEntityById("t-1"))
    }

    @Test
    fun testSoftDeleteTemplate_removesCrossRefsButKeepsMachine() = runTest {
        val templateDao = database.workoutTemplateDao()
        insertTemplateWithMachine("t-1", "m-1")

        templateDao.softDeleteTemplateWithCrossRefs("t-1")

        assertTrue(templateDao.getCrossRefsForTemplate("t-1").isEmpty())
        assertNotNull(database.machineDao().getMachineById("m-1"))
    }

    @Test
    fun testSoftDeleteMachine_removesItFromTemplateCrossRefs() = runTest {
        val templateDao = database.workoutTemplateDao()
        insertTemplateWithMachine("t-1", "m-1")

        templateDao.deleteCrossRefsByMachineId("m-1")
        database.machineDao().softDeleteMachine("m-1", System.currentTimeMillis())

        assertTrue(templateDao.getCrossRefsForTemplate("t-1").isEmpty())
        assertNull(templateDao.getMachinesForTemplateOrdered("t-1").firstOrNull())
    }
}
