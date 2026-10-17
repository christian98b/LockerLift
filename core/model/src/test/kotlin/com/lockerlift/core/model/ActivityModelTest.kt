package com.lockerlift.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the activity tracking domain models (Issue #50):
 * field-definition JSON serialization and entry value serialization.
 */
class ActivityModelTest {

    @Test
    fun testFieldsToJson_andFromJson_roundTrip() {
        val fields = listOf(
            ActivityFieldDefinition(id = "f-1", name = "km", type = ActivityFieldType.NUMBER),
            ActivityFieldDefinition(id = "f-2", name = "time", type = ActivityFieldType.TEXT)
        )
        val json = ActivityType.fieldsToJson(fields)
        val decoded = ActivityType.fieldsFromJson(json)
        assertEquals(fields, decoded)
    }

    @Test
    fun testFieldsFromJson_invalidJson_returnsEmptyList() {
        assertTrue(ActivityType.fieldsFromJson("not json {{{").isEmpty())
    }

    @Test
    fun testValuesToJson_andFromJson_roundTrip() {
        val values = mapOf("f-1" to "12.5", "f-2" to "55:30")
        val json = ActivityEntry.valuesToJson(values)
        val decoded = ActivityEntry.valuesFromJson(json)
        assertEquals(values, decoded)
    }

    @Test
    fun testValuesFromJson_invalidJson_returnsEmptyMap() {
        assertTrue(ActivityEntry.valuesFromJson("]]not json").isEmpty())
    }

    @Test
    fun testBuiltinTypeIds_areStable() {
        assertEquals("activity-type-builtin-running", ActivityType.BUILTIN_RUNNING_ID)
        assertEquals("activity-type-builtin-bouldering", ActivityType.BUILTIN_BOULDERING_ID)
    }
}
