package com.lockerlift.core.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import com.lockerlift.core.model.WorkoutSession
import java.time.Instant

class HealthConnectManager(
    private val context: Context,
    client: HealthConnectClient? = null
) {

    private val healthConnectClient by lazy {
        client ?: if (isAvailable()) HealthConnectClient.getOrCreate(context) else null
    }

    val sessionPermission: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    val heartRatePermission: String = HealthPermission.getWritePermission(HeartRateRecord::class)
    val caloriesPermission: String = HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)

    val requiredPermissions: Set<String> = setOf(
        sessionPermission,
        heartRatePermission,
        caloriesPermission
    )

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun getGrantedPermissions(): Set<String> {
        val client = healthConnectClient ?: return emptySet()
        return runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())
    }

    suspend fun hasPermissions(permissions: Set<String> = setOf(sessionPermission)): Boolean {
        val granted = getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    suspend fun hasAllPermissions(): Boolean {
        return hasPermissions(requiredPermissions)
    }

    suspend fun hasSessionPermission(): Boolean {
        return getGrantedPermissions().contains(sessionPermission)
    }

    suspend fun exportWorkoutSession(
        session: WorkoutSession,
        templateName: String? = null,
        caloriesKcal: Double? = null,
        heartRateSamples: List<Pair<Instant, Long>> = emptyList()
    ): Boolean {
        val client = healthConnectClient ?: return false
        return runCatching {
            val granted = client.permissionController.getGrantedPermissions()

            // Verify session write permission before attempting export (silent fallback on denied)
            if (!granted.contains(sessionPermission)) {
                return false
            }

            val records = mutableListOf<Record>()

            // 1. Base ExerciseSessionRecord
            records += ExerciseRecordBuilder.buildExerciseSessionRecord(
                session = session,
                title = templateName ?: "LockerLift Workout"
            )

            // 2. Attach TotalCaloriesBurnedRecord if energy is provided and permission granted
            if (caloriesKcal != null && granted.contains(caloriesPermission)) {
                records += ExerciseRecordBuilder.buildTotalCaloriesBurnedRecord(
                    session = session,
                    energyKcal = caloriesKcal
                )
            }

            // 3. Attach HeartRateRecord if telemetry samples are present and permission granted
            if (heartRateSamples.isNotEmpty() && granted.contains(heartRatePermission)) {
                records += ExerciseRecordBuilder.buildHeartRateRecord(
                    session = session,
                    samples = heartRateSamples
                )
            }

            client.insertRecords(records)
            true
        }.getOrDefault(false)
    }

    suspend fun deleteWorkoutSession(sessionId: String): Boolean {
        val client = healthConnectClient ?: return false
        return runCatching {
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.contains(sessionPermission)) {
                return false
            }
            client.deleteRecords(
                recordType = ExerciseSessionRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf(sessionId)
            )
            true
        }.getOrDefault(false)
    }
}
