package com.lockerlift.mobile.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.dao.WorkoutSessionDao
import com.lockerlift.core.database.dao.WorkoutTemplateDao
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.sync.SessionMachineInstancePayload
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WorkoutSessionPayload
import com.lockerlift.core.sync.WorkoutTemplatePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// ---------------------------------------------------------------------------
// Backup payload – top-level JSON envelope stored in each .json.gz file
// ---------------------------------------------------------------------------

/**
 * Top-level serialisable envelope written to every backup archive.
 *
 * @property schemaVersion  Bump when the format changes. Current: 1.
 * @property createdAt      Unix epoch millis at time of backup creation.
 * @property machines       JSON-encoded [List<Machine>] via [SyncPayloadSerializer.encodeMachines].
 * @property templates      JSON-encoded [List<WorkoutTemplatePayload>] via [SyncPayloadSerializer.encodeTemplates].
 * @property sessions       One JSON string per session, encoded via [SyncPayloadSerializer.encodeSessionPayload].
 */
@Serializable
data class BackupPayload(
    val schemaVersion: Int = 1,
    val createdAt: Long,
    val machines: String,
    val templates: String,
    val sessions: List<String>
)

// ---------------------------------------------------------------------------
// Manager
// ---------------------------------------------------------------------------

/**
 * Handles local backup and restore of LockerLift data to/from a SAF directory.
 *
 * All heavy IO is dispatched to [Dispatchers.IO]. Callers must be inside a
 * coroutine scope.
 *
 * Backup file naming: `lockerlift_backup_YYYY-MM-DD_HHmmss.json.gz`
 */
class LocalBackupManager(
    private val context: Context,
    private val database: LockerLiftDatabase
) {

    companion object {
        const val BACKUP_PREFIX = "lockerlift_backup_"
        private val TIMESTAMP_FMT = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // -----------------------------------------------------------------------
    // Export
    // -----------------------------------------------------------------------

    /**
     * Serialises all Room data to a GZip-compressed JSON file inside [directoryUri]
     * (a SAF persistent-permission tree URI).
     *
     * @return [BackupResult.Success] with the created filename, or
     *         [BackupResult.Error] with the failure message.
     */
    suspend fun exportToDirectory(directoryUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val machineDao = database.machineDao()
            val templateDao = database.workoutTemplateDao()
            val sessionDao = database.workoutSessionDao()

            // -- Machines --
            val machines = machineDao.getAllMachines().map { it.toDomainModel() }

            // -- Templates --
            val templatePayloads = buildTemplatePayloads(templateDao)

            // -- Sessions --
            val sessionPayloads = buildSessionPayloads(sessionDao)

            // -- Encode --
            val payload = BackupPayload(
                createdAt = System.currentTimeMillis(),
                machines = SyncPayloadSerializer.encodeMachines(machines),
                templates = SyncPayloadSerializer.encodeTemplates(templatePayloads),
                sessions = sessionPayloads.map { SyncPayloadSerializer.encodeSessionPayload(it) }
            )

            val jsonBytes = json.encodeToString(BackupPayload.serializer(), payload)
                .toByteArray(Charsets.UTF_8)
            val compressed = gzip(jsonBytes)

            // -- Write via SAF --
            val dir = DocumentFile.fromTreeUri(context, directoryUri)
                ?: return@runCatching BackupResult.Error("Cannot open directory URI")

            val fileName = buildFileName()
            val file = dir.createFile("application/gzip", fileName)
                ?: return@runCatching BackupResult.Error("Cannot create file in the selected directory")

            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                out.write(compressed)
            } ?: return@runCatching BackupResult.Error("Cannot open output stream for backup file")

            BackupResult.Success(fileName)
        }.getOrElse { e ->
            BackupResult.Error("Export failed: ${e.message}")
        }
    }

    // -----------------------------------------------------------------------
    // Restore
    // -----------------------------------------------------------------------

    /**
     * Reads, decompresses, and validates a backup file at [fileUri], then
     * upserts all data into Room using REPLACE-strategy insert methods already
     * present on the DAOs.
     *
     * @return [BackupResult.Success] on success, or [BackupResult.Error] if the
     *         schema version is unsupported or any IO/parse error occurs.
     */
    suspend fun restoreFromFile(fileUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val compressed = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                ?: return@runCatching BackupResult.Error("Cannot open backup file")

            val jsonString = ungzip(compressed).toString(Charsets.UTF_8)
            val payload = json.decodeFromString(BackupPayload.serializer(), jsonString)

            if (payload.schemaVersion != 1) {
                return@runCatching BackupResult.Error(
                    "Unsupported backup schema version: ${payload.schemaVersion}. Expected 1."
                )
            }

            val machineDao = database.machineDao()
            val templateDao = database.workoutTemplateDao()
            val sessionDao = database.workoutSessionDao()

            // -- Restore machines --
            val machines = SyncPayloadSerializer.decodeMachines(payload.machines)
            machineDao.insertMachines(machines.map { it.toEntity() })

            // -- Restore templates (entity + cross-refs) --
            val templatePayloads = SyncPayloadSerializer.decodeTemplates(payload.templates)
            for (tp in templatePayloads) {
                // saveTemplateWithMachines is @Transaction; handles insert + cross-refs atomically
                templateDao.saveTemplateWithMachines(
                    template = tp.template.toEntity(),
                    machineIdsInOrder = tp.machineIdsInOrder
                )
            }

            // -- Restore sessions (session + machine instances + sets) --
            for (sessionJson in payload.sessions) {
                val sp = SyncPayloadSerializer.decodeSessionPayload(sessionJson)
                val instances = sp.machineInstances.map { it.instance.toEntity() }
                val sets = sp.machineInstances.flatMap { mi -> mi.sets.map { it.toEntity() } }
                // upsertFullSession is @Transaction; handles all three tables atomically
                sessionDao.upsertFullSession(
                    session = sp.session.toEntity(),
                    instances = instances,
                    sets = sets
                )
            }

            BackupResult.Success("Restore complete")
        }.getOrElse { e ->
            BackupResult.Error("Restore failed: ${e.message}")
        }
    }

    // -----------------------------------------------------------------------
    // Housekeeping
    // -----------------------------------------------------------------------

    /**
     * Lists `lockerlift_backup_*.json.gz` files in [directoryUri], sorts them
     * by filename descending (ISO-prefixed names ensure chronological order),
     * and deletes any beyond [keepCount].
     */
    fun deleteOldBackups(directoryUri: Uri, keepCount: Int) {
        val dir = DocumentFile.fromTreeUri(context, directoryUri) ?: return
        val backups = dir.listFiles()
            .filter { doc ->
                doc.isFile &&
                    doc.name?.startsWith(BACKUP_PREFIX) == true &&
                    doc.name?.endsWith(".json.gz") == true
            }
            .sortedByDescending { it.name }

        if (backups.size > keepCount) {
            backups.drop(keepCount).forEach { it.delete() }
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    /**
     * Computes a SHA-256 hex digest of [bytes].
     * Useful for integrity verification of the raw compressed payload.
     */
    fun computeChecksum(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun buildFileName(): String =
        "${BACKUP_PREFIX}${TIMESTAMP_FMT.format(Date())}.json.gz"

    private fun gzip(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }

    private fun ungzip(data: ByteArray): ByteArray =
        GZIPInputStream(data.inputStream()).use { it.readBytes() }

    /**
     * Builds [WorkoutTemplatePayload] list by enumerating all non-archived template IDs
     * via a raw cursor (avoids collecting a Flow) then fetching each via the existing
     * suspend DAO helpers.
     */
    private suspend fun buildTemplatePayloads(
        templateDao: WorkoutTemplateDao
    ): List<WorkoutTemplatePayload> {
        val templateIds = mutableListOf<String>()
        database.openHelper.readableDatabase
            .query("SELECT id FROM workout_templates WHERE is_archived = 0")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    templateIds.add(cursor.getString(0))
                }
            }

        return templateIds.mapNotNull { id ->
            val withMachines = templateDao.getTemplateWithMachinesById(id) ?: return@mapNotNull null
            val machineIds = templateDao.getCrossRefsForTemplate(id)
                .sortedBy { it.sortOrder }
                .map { it.machineId }
            WorkoutTemplatePayload(
                template = withMachines.template.toDomainModel(),
                machineIdsInOrder = machineIds
            )
        }
    }

    /**
     * Builds [WorkoutSessionPayload] list by enumerating all session IDs via a raw cursor
     * then fetching the full graph for each via the existing suspend DAO helper.
     */
    private suspend fun buildSessionPayloads(
        sessionDao: WorkoutSessionDao
    ): List<WorkoutSessionPayload> {
        val sessionIds = mutableListOf<String>()
        database.openHelper.readableDatabase
            .query("SELECT id FROM workout_sessions ORDER BY start_time DESC")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    sessionIds.add(cursor.getString(0))
                }
            }

        return sessionIds.mapNotNull { id ->
            val details = sessionDao.getSessionWithDetailsById(id) ?: return@mapNotNull null
            WorkoutSessionPayload(
                session = details.session.toDomainModel(),
                templateName = null,
                machineInstances = details.machineInstances.map { instanceWithSets ->
                    SessionMachineInstancePayload(
                        instance = instanceWithSets.instance.toDomainModel(),
                        machine = instanceWithSets.machine.toDomainModel(),
                        sets = instanceWithSets.sets.map { it.toDomainModel() }
                    )
                }
            )
        }
    }
}
