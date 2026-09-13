package com.lockerlift.core.database.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.SessionMachineInstanceEntity
import com.lockerlift.core.database.entity.TemplateMachineCrossRefEntity
import com.lockerlift.core.database.entity.WorkoutSessionEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.entity.WorkoutTemplateEntity

data class WorkoutTemplateWithMachines(
    @Embedded val template: WorkoutTemplateEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TemplateMachineCrossRefEntity::class,
            parentColumn = "template_id",
            entityColumn = "machine_id"
        )
    )
    val machines: List<MachineEntity>
)

data class SessionMachineInstanceWithDetails(
    @Embedded val instance: SessionMachineInstanceEntity,
    @Relation(
        parentColumn = "machine_id",
        entityColumn = "id"
    )
    val machine: MachineEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "session_machine_id"
    )
    val sets: List<WorkoutSetEntity>
)

data class WorkoutSessionWithDetails(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        parentColumn = "template_id",
        entityColumn = "id"
    )
    val template: WorkoutTemplateEntity?,
    @Relation(
        entity = SessionMachineInstanceEntity::class,
        parentColumn = "id",
        entityColumn = "session_id"
    )
    val machineInstances: List<SessionMachineInstanceWithDetails>
)
