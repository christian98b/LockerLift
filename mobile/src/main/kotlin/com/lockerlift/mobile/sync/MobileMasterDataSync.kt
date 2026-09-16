package com.lockerlift.mobile.sync

import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WearableDataLayerManager
import com.lockerlift.core.sync.WorkoutTemplatePayload
import kotlinx.coroutines.flow.first

object MobileMasterDataSync {

    /**
     * Simultaneously pushes the current equipment catalog and all active workout templates
     * to connected Wear OS smartwatches via DataClient (US 5.2, AK 5.4.4).
     */
    suspend fun pushAllMasterData(
        database: LockerLiftDatabase,
        dataLayerManager: WearableDataLayerManager
    ) {
        // 1. Sync equipment catalog
        val allMachines = database.machineDao().getAllMachines()
        val catalogJson = SyncPayloadSerializer.encodeMachines(allMachines.map { it.toDomainModel() })
        dataLayerManager.syncEquipmentCatalog(catalogJson)

        // 2. Sync workout templates
        val allActiveTemplates = database.workoutTemplateDao().getAllActiveTemplatesWithMachinesFlow().first()
        val payloads = allActiveTemplates.map { item ->
            val orderedMachineIds = database.workoutTemplateDao()
                .getCrossRefsForTemplate(item.template.id)
                .map { it.machineId }
            WorkoutTemplatePayload(
                template = item.template.toDomainModel(),
                machineIdsInOrder = orderedMachineIds
            )
        }
        val templatesJson = SyncPayloadSerializer.encodeTemplates(payloads)
        dataLayerManager.syncTemplates(templatesJson)
    }
}
