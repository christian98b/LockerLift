package com.lockerlift.core.sync

import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.WorkoutSession
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.core.model.WorkoutTemplate
import kotlinx.serialization.Serializable

@Serializable
data class SessionMachineInstancePayload(
    val instance: SessionMachineInstance,
    val machine: Machine,
    val sets: List<WorkoutSet>
)

@Serializable
data class WorkoutSessionPayload(
    val session: WorkoutSession,
    val templateName: String? = null,
    val machineInstances: List<SessionMachineInstancePayload>
)

@Serializable
data class WorkoutTemplatePayload(
    val template: WorkoutTemplate,
    val machineIdsInOrder: List<String>
)
