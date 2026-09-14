package com.lockerlift.wear.logic

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.lockerlift.core.sync.CompanionDeviceStatus

object WearSettingsLogic {

    fun formatConnectionStatus(
        status: CompanionDeviceStatus?,
        connectedFmt: String,
        disconnectedStr: String,
        checkingStr: String
    ): String {
        return when {
            status == null -> checkingStr
            status.isConnected -> {
                val name = status.deviceName?.takeIf { it.isNotBlank() } ?: "Phone"
                String.format(connectedFmt, name)
            }
            else -> disconnectedStr
        }
    }

    fun formatQueueStatus(
        pendingCount: Int,
        pendingFmt: String,
        allSyncedStr: String
    ): String {
        return if (pendingCount > 0) {
            String.format(pendingFmt, pendingCount)
        } else {
            allSyncedStr
        }
    }

    fun getAppVersionString(context: Context): String {
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val versionName = packageInfo.versionName ?: "1.2.1"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            "v$versionName ($versionCode)"
        }.getOrDefault("v1.2.1 (4)")
    }

    fun triggerHapticSuccess(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 80), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        }
    }

    fun triggerHapticError(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 250), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(300)
            }
        }
    }
}
