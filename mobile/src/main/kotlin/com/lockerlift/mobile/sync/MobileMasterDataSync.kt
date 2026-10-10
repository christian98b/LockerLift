package com.lockerlift.mobile.sync

import android.content.Context
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.SyncQueueEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.model.QueueStatus
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WearableDataLayerManager
import com.lockerlift.core.sync.WorkoutTemplatePayload
import kotlinx.coroutines.flow.first
import java.util.UUID

object MobileMasterDataSync {

    /**
     * Pushes the current equipment catalog and all active workout templates
     * to connected Wear OS smartwatches via DataClient (US 5.2, AK 5.4.4).
     * 
     * NEW: Also queues master data in sync queue for reliable delivery with retries.
     */
    suspend fun pushAllMasterData(
        context: Context,
        database: LockerLiftDatabase,
        dataLayerManager: WearableDataLayerManager
    ) {
        val syncQueueDao = database.syncQueueDao()

        // 1. Sync equipment catalog via DataClient (immediate)
        val allMachines = database.machineDao().getAllMachines()
        val catalogJson = SyncPayloadSerializer.encodeMachines(allMachines.map { it.toDomainModel() })
        dataLayerManager.syncEquipmentCatalog(catalogJson)

        // Deduplicate: remove any pending catalog queue items before inserting a fresh one
        syncQueueDao.deleteQueueItemBySessionIdAndType("MASTER_CATALOG", SyncConstants.ITEM_TYPE_MASTER_CATALOG)
        val catalogQueueItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            sessionId = "MASTER_CATALOG",
            payloadJson = catalogJson,
            status = QueueStatus.PENDING,
            itemType = SyncConstants.ITEM_TYPE_MASTER_CATALOG,
            targetDeviceId = SyncConstants.CAPABILITY_WEAR
        )
        syncQueueDao.insertQueueItem(catalogQueueItem)

        // 2. Sync workout templates via DataClient (immediate)
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

        // Deduplicate: remove any pending templates queue items before inserting a fresh one
        syncQueueDao.deleteQueueItemBySessionIdAndType("MASTER_TEMPLATES", SyncConstants.ITEM_TYPE_MASTER_TEMPLATES)
        val templatesQueueItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            sessionId = "MASTER_TEMPLATES",
            payloadJson = templatesJson,
            status = QueueStatus.PENDING,
            itemType = SyncConstants.ITEM_TYPE_MASTER_TEMPLATES,
            targetDeviceId = SyncConstants.CAPABILITY_WEAR
        )
        syncQueueDao.insertQueueItem(templatesQueueItem)

        // Trigger sync worker to process queued master data
        com.lockerlift.core.sync.SyncQueueWorker.enqueue(context)
    }

    /**
     * Pushes only equipment catalog to wear devices.
     */
    suspend fun pushEquipmentCatalog(
        context: Context,
        database: LockerLiftDatabase,
        dataLayerManager: WearableDataLayerManager
    ) {
        val syncQueueDao = database.syncQueueDao()
        val allMachines = database.machineDao().getAllMachines()
        val catalogJson = SyncPayloadSerializer.encodeMachines(allMachines.map { it.toDomainModel() })
        dataLayerManager.syncEquipmentCatalog(catalogJson)

        syncQueueDao.deleteQueueItemBySessionIdAndType("MASTER_CATALOG", SyncConstants.ITEM_TYPE_MASTER_CATALOG)
        val queueItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            sessionId = "MASTER_CATALOG",
            payloadJson = catalogJson,
            status = QueueStatus.PENDING,
            itemType = SyncConstants.ITEM_TYPE_MASTER_CATALOG,
            targetDeviceId = SyncConstants.CAPABILITY_WEAR
        )
        syncQueueDao.insertQueueItem(queueItem)
        com.lockerlift.core.sync.SyncQueueWorker.enqueue(context)
    }

    /**
     * Pushes only workout templates to wear devices.
     */
    suspend fun pushWorkoutTemplates(
        context: Context,
        database: LockerLiftDatabase,
        dataLayerManager: WearableDataLayerManager
    ) {
        val syncQueueDao = database.syncQueueDao()
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

        syncQueueDao.deleteQueueItemBySessionIdAndType("MASTER_TEMPLATES", SyncConstants.ITEM_TYPE_MASTER_TEMPLATES)
        val queueItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            sessionId = "MASTER_TEMPLATES",
            payloadJson = templatesJson,
            status = QueueStatus.PENDING,
            itemType = SyncConstants.ITEM_TYPE_MASTER_TEMPLATES,
            targetDeviceId = SyncConstants.CAPABILITY_WEAR
        )
        syncQueueDao.insertQueueItem(queueItem)
        com.lockerlift.core.sync.SyncQueueWorker.enqueue(context)
    }
}
