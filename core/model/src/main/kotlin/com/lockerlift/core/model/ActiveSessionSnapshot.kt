package com.lockerlift.core.model

import kotlinx.serialization.Serializable

/**
 * A single bucket of logged sets keyed by session machine instance.
 * Used inside [ActiveSessionSnapshot] because kotlinx.serialization has no
 * built-in Map polymorphism for plain data maps.
 */
@Serializable
data class InstanceSets(
    val instanceId: String,
    val sets: List<WorkoutSet> = emptyList()
)

/**
 * Durable snapshot of an in-progress workout session (Locker Scenario:
 * every CRUD action must survive process death on the watch).
 *
 * The active session is persisted to the local Room database after every
 * mutation (set logged, station modified, pause toggled) and reloaded on
 * startup, so a workout survives OS-initiated process kills in ambient mode
 * instead of living only in composition state.
 */
@Serializable
data class ActiveSessionSnapshot(
    val sessionId: String,
    val templateId: String? = null,
    val templateName: String? = null,
    val startTimeMillis: Long,
    val originDevice: String,
    val initialMachines: List<Machine> = emptyList(),
    val instances: List<SessionMachineInstance> = emptyList(),
    val setsByInstance: List<InstanceSets> = emptyList(),
    val pausedAtMillis: Long? = null,
    val totalPausedMillis: Long = 0L
)

/**
 * Pure mapping helpers between the snapshot shape and the per-instance set
 * map used by the tracking ViewModels. Keeping them in the model layer makes
 * the round-trip unit-testable without any persistence dependency.
 */
object ActiveSessionSnapshotCodec {

    fun setsMapFromSnapshot(snapshot: ActiveSessionSnapshot): Map<String, List<WorkoutSet>> {
        return snapshot.setsByInstance.associate { it.instanceId to it.sets }
    }

    fun snapshotWithSets(
        snapshot: ActiveSessionSnapshot,
        sets: Map<String, List<WorkoutSet>>
    ): ActiveSessionSnapshot {
        return snapshot.copy(
            setsByInstance = sets.map { (instanceId, list) -> InstanceSets(instanceId, list) }
        )
    }
}
