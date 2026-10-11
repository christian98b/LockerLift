package com.lockerlift.mobile

import com.lockerlift.mobile.ui.insights.DashboardConfigStore
import com.lockerlift.mobile.ui.insights.DashboardWidgetType
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for the insights dashboard widget configuration
 * (order serialization, parsing, and fallback behavior).
 * Adheres strictly to the AAA pattern and LockerLift unit testing guidelines.
 */
class InsightsDashboardTest {

    @Test
    fun testDefaultOrder_containsAllWidgetsInCatalogOrder() {
        // Arrange & Act
        val order = DashboardWidgetType.DEFAULT_ORDER

        // Assert
        assertEquals(DashboardWidgetType.entries.toList(), order)
        assertEquals(DashboardWidgetType.VERDICT, order.first())
        assertEquals(DashboardWidgetType.RECORDS, order.last())
    }

    @Test
    fun testSerializeAndParse_roundTripsCustomOrder() {
        // Arrange
        val custom = listOf(
            DashboardWidgetType.CALENDAR,
            DashboardWidgetType.VERDICT,
            DashboardWidgetType.PROGRESSION,
            DashboardWidgetType.REGULARITY,
            DashboardWidgetType.VOLUME_CHART,
            DashboardWidgetType.RECORDS
        )

        // Act
        val serialized = DashboardConfigStore.serialize(custom)
        val parsed = DashboardConfigStore.parseOrder(serialized)

        // Assert
        assertEquals(custom, parsed)
    }

    @Test
    fun testParseOrder_fallsBackToDefaultOnIncompleteData() {
        // Arrange: unknown widget and a missing entry
        val stored = "CALENDAR|VERDICT|UNKNOWN_WIDGET|PROGRESSION|REGULARITY"

        // Act
        val parsed = DashboardConfigStore.parseOrder(stored)

        // Assert
        assertEquals(DashboardWidgetType.DEFAULT_ORDER, parsed)
    }

    @Test
    fun testParseOrder_fallsBackToDefaultOnGarbage() {
        // Arrange
        val stored = "not|a|widget|list"

        // Act
        val parsed = DashboardConfigStore.parseOrder(stored)

        // Assert
        assertEquals(DashboardWidgetType.DEFAULT_ORDER, parsed)
    }

    @Test
    fun testParseHidden_ignoresUnknownEntries() {
        // Arrange
        val stored = "CALENDAR|RECORDS|DOES_NOT_EXIST"

        // Act
        val hidden = DashboardConfigStore.parseHidden(stored)

        // Assert
        assertEquals(setOf(DashboardWidgetType.CALENDAR, DashboardWidgetType.RECORDS), hidden)
    }

    @Test
    fun testParseHidden_emptyStringYieldsEmptySet() {
        // Arrange
        val stored = ""

        // Act
        val hidden = DashboardConfigStore.parseHidden(stored)

        // Assert
        assertTrue(hidden.isEmpty())
    }
}
