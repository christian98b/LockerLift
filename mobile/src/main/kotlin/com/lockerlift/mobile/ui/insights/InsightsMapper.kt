package com.lockerlift.mobile.ui.insights

import com.lockerlift.core.database.model.WorkoutSessionWithDetails
import com.lockerlift.core.model.InsightsEngine

/**
 * Maps Room relation snapshots into pure domain snapshots for [InsightsEngine].
 */
object InsightsMapper {

    fun toSnapshots(sessions: List<WorkoutSessionWithDetails>): List<InsightsEngine.SessionSnapshot> {
        return sessions.map { s ->
            InsightsEngine.SessionSnapshot(
                sessionId = s.session.id,
                startTime = s.session.startTime,
                endTime = s.session.endTime,
                machineInstances = s.machineInstances.map { instance ->
                    InsightsEngine.MachineInstanceSnapshot(
                        machineId = instance.instance.machineId,
                        machineName = instance.machine.name,
                        isSkipped = instance.instance.isSkipped,
                        sets = instance.sets
                            .sortedBy { it.setNumber }
                            .map { set ->
                                InsightsEngine.SetSnapshot(
                                    setNumber = set.setNumber,
                                    reps = set.reps,
                                    weightKg = set.weightKg,
                                    setType = set.setType
                                )
                            }
                    )
                }
            )
        }
    }
}
