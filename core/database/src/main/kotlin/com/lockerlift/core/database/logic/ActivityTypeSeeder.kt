package com.lockerlift.core.database.logic

import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.ActivityFieldDefinition
import com.lockerlift.core.model.ActivityType

/**
 * Seeds the built-in activity types (Issue #50) if they are not present yet.
 * Runs on every app start; existing rows are never overwritten.
 */
object ActivityTypeSeeder {

    private val BUILTIN_RUNNING = ActivityType(
        id = ActivityType.BUILTIN_RUNNING_ID,
        name = "Running",
        fields = listOf(
            ActivityFieldDefinition(
                id = "field-builtin-running-km",
                name = "km",
                type = com.lockerlift.core.model.ActivityFieldType.NUMBER
            ),
            ActivityFieldDefinition(
                id = "field-builtin-running-time",
                name = "time"
            )
        ),
        isBuiltin = true
    )

    private val BUILTIN_BOULDERING = ActivityType(
        id = ActivityType.BUILTIN_BOULDERING_ID,
        name = "Bouldering",
        fields = emptyList(),
        isBuiltin = true
    )

    suspend fun seedIfMissing(database: LockerLiftDatabase) {
        val dao = database.activityTypeDao()
        val existing = dao.getAllActivityTypes().map { it.id }.toSet()
        val missing = listOf(BUILTIN_RUNNING, BUILTIN_BOULDERING).filter { it.id !in existing }
        if (missing.isNotEmpty()) {
            dao.insertActivityTypes(missing.map { it.toEntity() })
        }
    }
}
