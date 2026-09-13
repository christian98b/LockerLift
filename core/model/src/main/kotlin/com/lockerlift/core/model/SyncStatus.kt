package com.lockerlift.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SyncStatus {
    LOCAL_ONLY,
    PENDING_SYNC,
    SYNCED,
    CONFLICT
}

@Serializable
enum class QueueStatus {
    PENDING,
    IN_TRANSIT,
    ACKNOWLEDGED,
    ERROR
}
