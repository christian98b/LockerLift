package com.lockerlift.core.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import com.lockerlift.core.model.WorkoutSession

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy {
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null
    }

    val requiredPermissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
    )

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    suspend fun exportWorkoutSession(
        session: WorkoutSession,
        templateName: String? = null
    ): Boolean {
        val client = healthConnectClient ?: return false
        return runCatching {
            val record = ExerciseRecordBuilder.buildExerciseSessionRecord(
                session = session,
                title = templateName ?: "LockerLift Workout"
            )
            client.insertRecords(listOf(record))
            true
        }.getOrDefault(false)
    }
}
