package com.lockerlift.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Type of a user-defined field on an activity type (Issue #50).
 * NUMBER fields are validated as numeric input in the UI; TEXT accepts anything.
 */
enum class ActivityFieldType {
    TEXT,
    NUMBER
}

/**
 * A single user-defined field definition of an activity type (Issue #50):
 * e.g. "km" or "time" for running. The field id is the stable key that
 * ActivityEntry values refer to; the label is what the user sees.
 */
@Serializable
data class ActivityFieldDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ActivityFieldType = ActivityFieldType.TEXT
)

/**
 * A customizable activity type (Issue #50): running, bouldering, or any
 * sport the user creates. Each type defines its own set of fields the user
 * fills in when logging an entry. Built-in types (running, bouldering) are
 * seeded by the database migration and cannot be deleted, but can be edited.
 */
@Serializable
data class ActivityType(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fields: List<ActivityFieldDefinition> = emptyList(),
    val isBuiltin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val BUILTIN_RUNNING_ID = "activity-type-builtin-running"
        const val BUILTIN_BOULDERING_ID = "activity-type-builtin-bouldering"

        fun fieldsToJson(fields: List<ActivityFieldDefinition>): String {
            return Json.encodeToString(ListSerializer(ActivityFieldDefinition.serializer()), fields)
        }

        fun fieldsFromJson(json: String): List<ActivityFieldDefinition> {
            return runCatching {
                Json.decodeFromString(ListSerializer(ActivityFieldDefinition.serializer()), json)
            }.getOrDefault(emptyList())
        }
    }
}

/**
 * A logged occurrence of an activity (Issue #50): the record that the
 * activity was performed on a given date. Field values are stored keyed
 * by the field id of the owning type's field definitions.
 */
@Serializable
data class ActivityEntry(
    val id: String = UUID.randomUUID().toString(),
    val activityTypeId: String,
    val date: Long = System.currentTimeMillis(),
    val fieldValues: Map<String, String> = emptyMap(),
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun valuesToJson(values: Map<String, String>): String {
            return Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), values)
        }

        fun valuesFromJson(json: String): Map<String, String> {
            return runCatching {
                Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), json)
            }.getOrDefault(emptyMap())
        }
    }
}
