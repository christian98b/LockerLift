package com.lockerlift.core.sync

import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.SyncStatus
import kotlinx.coroutines.flow.first

/**
 * Shared, unified synchronization ingestion engine.
 *
 * Implements deterministic master data and workout session ingestion, machine reconciliation,
 * duplicate handling, zombie workout detection, and ACK/delete management across both
 * Mobile and Wear OS modules.
 */
object SyncIngestionEngine {

    sealed class IngestionResult {
        data class Success(val sessionId: String, val payload: WorkoutSessionPayload) : IngestionResult()
        data class RejectedZombie(val sessionId: String) : IngestionResult()
    }

    /**
     * Ingests a received workout payload into the target database.
     *
     * 1. Checks for local zombie deletion if [isMobile] is true.
     * 2. Reconciles incoming machines by name (case-insensitive deduplication),
     *    remapping instance foreign keys to existing local machines to prevent SQLite
     *    unique constraint or foreign key violations.
     * 3. Atomically upserts session, instances, and sets with [SyncStatus.SYNCED].
     */
    suspend fun ingestWorkoutPayload(
        database: LockerLiftDatabase,
        payloadJson: String,
        isMobile: Boolean = false
    ): IngestionResult {
        val payload = SyncPayloadSerializer.decodeSessionPayload(payloadJson)
        val session = payload.session.copy(syncStatus = SyncStatus.SYNCED)
        val sessionDao = database.workoutSessionDao()
        val machineDao = database.machineDao()

        if (isMobile) {
            val pendingDelete = database.syncQueueDao().getQueueItemBySessionId(session.id)
            if (pendingDelete != null && pendingDelete.payloadJson == SyncConstants.ACTION_DELETE) {
                return IngestionResult.RejectedZombie(session.id)
            }
        }

        val machinesToInsert = mutableListOf<MachineEntity>()
        val machineIdMapping = mutableMapOf<String, String>()

        for (instPayload in payload.machineInstances) {
            val incomingMachine = instPayload.machine
            val existingMachine = machineDao.getMachineByName(incomingMachine.name)
            if (existingMachine != null) {
                machineIdMapping[incomingMachine.id] = existingMachine.id
            } else {
                machineIdMapping[incomingMachine.id] = incomingMachine.id
                if (machinesToInsert.none { it.id == incomingMachine.id }) {
                    machinesToInsert.add(incomingMachine.toEntity())
                }
            }
        }

        if (machinesToInsert.isNotEmpty()) {
            machineDao.insertMachines(machinesToInsert)
        }

        val instanceEntities = payload.machineInstances.map { instPayload ->
            val resolvedMachineId = machineIdMapping[instPayload.machine.id] ?: instPayload.instance.machineId
            instPayload.instance.copy(machineId = resolvedMachineId).toEntity()
        }
        val setEntities = payload.machineInstances.flatMap { it.sets.map { set -> set.toEntity() } }

        sessionDao.upsertFullSession(
            session = session.toEntity(),
            instances = instanceEntities,
            sets = setEntities
        )

        return IngestionResult.Success(session.id, payload)
    }

    /**
     * Ingests master equipment catalog into database.
     */
    suspend fun ingestEquipmentCatalog(
        database: LockerLiftDatabase,
        catalogJson: String
    ): Int {
        val machines = SyncPayloadSerializer.decodeMachines(catalogJson)
        if (machines.isNotEmpty()) {
            database.machineDao().insertMachines(machines.map { it.toEntity() })
        }
        return machines.size
    }

    /**
     * Ingests master workout templates into database, archiving local templates
     * not present in the incoming list and updating template machine cross references.
     */
    suspend fun ingestWorkoutTemplates(
        database: LockerLiftDatabase,
        templatesJson: String
    ): Int {
        val payloads = SyncPayloadSerializer.decodeTemplates(templatesJson)
        val incomingIds = payloads.map { it.template.id }.toSet()

        val localActiveTemplates = database.workoutTemplateDao().getAllActiveTemplatesWithMachinesFlow().first()
        for (local in localActiveTemplates) {
            if (local.template.id !in incomingIds) {
                database.workoutTemplateDao().archiveTemplate(local.template.id)
            }
        }

        for (payload in payloads) {
            database.workoutTemplateDao().saveTemplateWithMachines(
                template = payload.template.toEntity(),
                machineIdsInOrder = payload.machineIdsInOrder
            )
        }
        return payloads.size
    }

    /**
     * Processes an incoming ACK message: purges sync queue item and marks session as SYNCED.
     */
    suspend fun handleWorkoutAck(
        database: LockerLiftDatabase,
        sessionId: String
    ) {
        database.syncQueueDao().deleteQueueItemBySessionId(sessionId)
        database.workoutSessionDao().updateSyncStatus(sessionId, SyncStatus.SYNCED)
    }

    /**
     * Processes an incoming workout delete message: deletes session and queue item.
     */
    suspend fun handleWorkoutDelete(
        database: LockerLiftDatabase,
        sessionId: String
    ) {
        database.workoutSessionDao().deleteSession(sessionId)
        database.syncQueueDao().deleteQueueItemBySessionId(sessionId)
    }
}
