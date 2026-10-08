package com.lockerlift.core.sync

object SyncConstants {
    // DataClient paths (Master data replication)
    const val PATH_EQUIPMENT_CATALOG = "/equipment_catalog"
    const val PATH_WORKOUT_TEMPLATES = "/workout_templates"

    // ChannelClient paths (Large workout session stream)
    const val PATH_WORKOUT_CHANNEL = "/workout_payload_transfer"

    // MessageClient paths (RPC & Acknowledgment)
    const val PATH_WORKOUT_ACK = "/workout_ack"
    const val PATH_WORKOUT_NACK = "/workout_nack"
    const val PATH_WORKOUT_DELETE = "/workout_delete"
    const val PATH_WORKOUT_MESSAGE = "/workout_payload_message"
    const val PATH_SYNC_REQUEST_FLUSH = "/sync/request_flush"
    const val PATH_SYNC_FLUSH_COMPLETED = "/sync/flush_completed"
    const val PATH_REQUEST_MASTER_DATA = "/sync/request_master_data"
    const val PATH_MASTER_DATA_ACK = "/sync/master_data_ack"
    const val PATH_PING = "/sync_ping"

    // Sync queue action constants
    const val ACTION_DELETE = "DELETE"

    // Sync queue item types
    const val ITEM_TYPE_WORKOUT = "WORKOUT"
    const val ITEM_TYPE_MASTER_CATALOG = "MASTER_CATALOG"
    const val ITEM_TYPE_MASTER_TEMPLATES = "MASTER_TEMPLATES"

    // NACK error codes
    const val NACK_DATABASE_ERROR = "DB_ERROR"
    const val NACK_INVALID_PAYLOAD = "INVALID_PAYLOAD"
    const val NACK_ZOMBIE_DETECTED = "ZOMBIE_DETECTED"
    const val NACK_VERSION_CONFLICT = "VERSION_CONFLICT"
    const val NACK_UNKNOWN_ERROR = "UNKNOWN_ERROR"

    // Wearable Data Layer Capabilities (Node verification)
    const val CAPABILITY_WEAR = "lockerlift_wear_app"
    const val CAPABILITY_MOBILE = "lockerlift_mobile_app"

    // Timeouts
    const val CHANNEL_READ_TIMEOUT_MS = 30000L // 30 seconds
    const val ACK_TIMEOUT_MS = 5000L // 5 seconds
    const val MAX_RETRY_ATTEMPTS = 5
}
