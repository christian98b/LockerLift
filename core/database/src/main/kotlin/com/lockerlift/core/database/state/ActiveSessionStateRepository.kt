package com.lockerlift.core.database.state

import com.lockerlift.core.database.dao.ActiveSessionStateDao
import com.lockerlift.core.database.entity.ActiveSessionStateEntity
import com.lockerlift.core.database.entity.ActiveSessionStateEntity.Companion.ACTIVE_ROW_ID
import com.lockerlift.core.model.ActiveSessionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Persists the in-progress workout as a single JSON snapshot row.
 * Save is called after every session mutation; load is called on app
 * startup for crash recovery. All operations run on Dispatchers.IO because
 * they are fire-and-forget from the UI layer.
 */
class ActiveSessionStateRepository(
    private val dao: ActiveSessionStateDao,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        // WorkoutSet defaults (completedAt, setType, cadence) carry real data
        // and must survive the snapshot round-trip, so they must be encoded.
        encodeDefaults = true
    }
) {

    suspend fun save(snapshot: ActiveSessionSnapshot) = withContext(Dispatchers.IO) {
        dao.upsert(
            ActiveSessionStateEntity(
                id = ACTIVE_ROW_ID,
                payloadJson = json.encodeToString(ActiveSessionSnapshot.serializer(), snapshot)
            )
        )
    }

    suspend fun load(): ActiveSessionSnapshot? = withContext(Dispatchers.IO) {
        dao.get()?.let { entity ->
            runCatching {
                json.decodeFromString(ActiveSessionSnapshot.serializer(), entity.payloadJson)
            }.getOrNull()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clear()
    }
}
