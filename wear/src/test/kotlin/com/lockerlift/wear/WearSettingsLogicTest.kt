package com.lockerlift.wear

import com.lockerlift.core.sync.CompanionDeviceStatus
import com.lockerlift.wear.logic.WearSettingsLogic
import org.junit.Assert.*
import org.junit.Test

class WearSettingsLogicTest {

    @Test
    fun testFormatConnectionStatus_connectedWithName() {
        val status = CompanionDeviceStatus(
            isConnected = true,
            deviceName = "Pixel 8",
            nodeId = "phone-1",
            isNearby = true,
            hasRequiredCapability = true
        )

        val text = WearSettingsLogic.formatConnectionStatus(
            status = status,
            connectedFmt = "Connected: %1$s",
            disconnectedStr = "Disconnected",
            checkingStr = "Checking..."
        )

        assertEquals("Connected: Pixel 8", text)
    }

    @Test
    fun testFormatConnectionStatus_connectedWithBlankNameFallsBackToPhone() {
        val status = CompanionDeviceStatus(
            isConnected = true,
            deviceName = "  ",
            nodeId = "phone-1"
        )

        val text = WearSettingsLogic.formatConnectionStatus(
            status = status,
            connectedFmt = "Connected to %1$s",
            disconnectedStr = "Disconnected",
            checkingStr = "Checking..."
        )

        assertEquals("Connected to Phone", text)
    }

    @Test
    fun testFormatConnectionStatus_disconnected() {
        val status = CompanionDeviceStatus(
            isConnected = false
        )

        val text = WearSettingsLogic.formatConnectionStatus(
            status = status,
            connectedFmt = "Connected: %1$s",
            disconnectedStr = "Phone Disconnected / In Locker",
            checkingStr = "Checking..."
        )

        assertEquals("Phone Disconnected / In Locker", text)
    }

    @Test
    fun testFormatConnectionStatus_null() {
        val text = WearSettingsLogic.formatConnectionStatus(
            status = null,
            connectedFmt = "Connected: %1$s",
            disconnectedStr = "Disconnected",
            checkingStr = "Checking phone..."
        )

        assertEquals("Checking phone...", text)
    }

    @Test
    fun testFormatQueueStatus_withPending() {
        val text = WearSettingsLogic.formatQueueStatus(
            pendingCount = 4,
            pendingFmt = "Pending Workouts: %1$d",
            allSyncedStr = "All workouts synced"
        )

        assertEquals("Pending Workouts: 4", text)
    }

    @Test
    fun testFormatQueueStatus_zeroPending() {
        val text = WearSettingsLogic.formatQueueStatus(
            pendingCount = 0,
            pendingFmt = "Pending Workouts: %1$d",
            allSyncedStr = "All workouts synced"
        )

        assertEquals("All workouts synced", text)
    }

    @Test
    fun testFormatQueueStatus_negativePending() {
        val text = WearSettingsLogic.formatQueueStatus(
            pendingCount = -1,
            pendingFmt = "Pending Workouts: %1$d",
            allSyncedStr = "All workouts synced"
        )

        assertEquals("All workouts synced", text)
    }
}
