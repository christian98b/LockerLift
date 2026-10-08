package com.lockerlift.core.sync

import android.util.Log
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.DeletedSessionEntity
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.DeletedSession
import com.lockerlift.core.model.SyncStatus
import kotlinx.coroutines.flow.first

/**
 * Shared, unified synchronization ingestion engine.
 *
 * Implements deterministic master data and workout session ingestion, machine reconciliation,
 * duplicate handling, zombie workout detection, and ACK/delete management across both
 * Mobile and Wear OS modules.
 *
 * NEW: Uses tombstone table for zombie detection across both devices.
 */
object SyncIngestionEngine {

    sealed class IngestionResult {
        data class Success(val sessionId: String, val payload: WorkoutSessionPayload) : IngestionResult()
        data class RejectedZombie(val sessionId: String) : IngestionResult()
    }

    /**
     * Ingests a received workout payload into the target database.
     *
     * 1. Checks for zombie deletion using tombstone table (both devices).
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

        // NEW: Check tombstone table for zombie detection (works on both Mobile and Wear)
        val deletedSessionDao = database.deletedSessionDao()
        val isZombie = deletedSessionDao.getBySessionId(session.id) != null
        if (isZombie) {
            Log.i("SyncIngestionEngine", "Rejecting zombie session ${session.id} - found in tombstone table")
            return IngestionResult.RejectedZombie(session.id)
        }

        // Legacy check for Mobile (backward compatibility)
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
     * 
     * NEW: Uses upsert instead of insert to handle existing machines.
     */
    suspend fun ingestEquipmentCatalog(
        database: LockerLiftDatabase,
        catalogJson: String
    ): Int {
        val machines = SyncPayloadSerializer.decodeMachines(catalogJson)
        if (machines.isNotEmpty()) {
            // Use upsert to handle existing machines gracefully
            val machineDao = database.machineDao()
            for (machine in machines) {
                val existingMachine = machineDao.getMachineByName(machine.name)
                if (existingMachine == null) {
                    machineDao.insertMachine(machine.toEntity())
                } else {
                    // Update existing machine if needed
                    machineDao.updateMachine(
                        existingMachine.copy(
                            name = machine.name,
                            targetMuscleGroup = machine.targetMuscleGroup,
                            machineSettingsNote = machine.machineSettingsNote,
                            defaultIncrementKg = machine.defaultIncrementKg,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
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
     * 
     * NEW: Also adds tombstone entry for zombie detection.
     */
    suspend fun handleWorkoutDelete(
        database: LockerLiftDatabase,
        sessionId: String
    ) {
        database.workoutSessionDao().deleteSession(sessionId)
        database.syncQueueDao().deleteQueueItemBySessionId(sessionId)
        
        // Add tombstone entry for zombie detection
        val deletedSessionDao = database.deletedSessionDao()
        val existingTombstone = deletedSessionDao.getBySessionId(sessionId)
        if (existingTombstone == null) {
            val tombstone = DeletedSessionEntity(
                sessionId = sessionId,
                deletedAt = System.currentTimeMillis(),
                originDevice = "RECEIVED", // Indicates this was received from another device
                ttlDays = 30
            )
            deletedSessionDao.insert(tombstone)
            Log.i("SyncIngestionEngine", "Added tombstone for deleted session $sessionId")
        }
    }

    /**
     * Adds a tombstone entry for a locally deleted workout session.
     * Call this when a user deletes a workout on the local device.
     */
    suspend fun addDeletedSessionTombstone(
        database: LockerLiftDatabase,
        sessionId: String,
        originDevice: String = "LOCAL"
    ) {
        val deletedSessionDao = database.deletedSessionDao()
        val existingTombstone = deletedSessionDao.getBySessionId(sessionId)
        if (existingTombstone == null) {
            val tombstone = DeletedSessionEntity(
                sessionId = sessionId,
                deletedAt = System.currentTimeMillis(),
                originDevice = originDevice,
                ttlDays = 30
            )
            deletedSessionDao.insert(tombstone)
            Log.i("SyncIngestionEngine", "Added tombstone for locally deleted session $sessionId")
        }
    }

    /**
     * Cleans up expired tombstone entries.
     * Should be called periodically (e.g., once per day).
     */
    suspend fun cleanupExpiredTombstones(database: LockerLiftDatabase): Int {
        val deletedSessionDao = database.deletedSessionDao()
        val cutoffTimestamp = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days
        return deletedSessionDao.deleteExpiredSessions(cutoffTimestamp)
    }
}
