package com.lockerlift.core.sync

object SyncConstants {
    // DataClient paths (Master data replication)
    const val PATH_EQUIPMENT_CATALOG = "/equipment_catalog"
    const val PATH_WORKOUT_TEMPLATES = "/workout_templates"

    // ChannelClient paths (Large workout session stream)
    const val PATH_WORKOUT_CHANNEL = "/workout_payload_transfer"

    // MessageClient paths (RPC & Acknowledgment)
    const val PATH_WORKOUT_ACK = "/workout_ack"
    const val PATH_WORKOUT_DELETE = "/workout_delete"
    const val PATH_WORKOUT_MESSAGE = "/workout_payload_message"
    const val PATH_SYNC_REQUEST_FLUSH = "/sync/request_flush"
    const val PATH_SYNC_FLUSH_COMPLETED = "/sync/flush_completed"
    const val PATH_REQUEST_MASTER_DATA = "/sync/request_master_data"
    const val PATH_PING = "/sync_ping"

    // Sync queue action constants
    const val ACTION_DELETE = "DELETE"

    // Wearable Data Layer Capabilities (Node verification)
    const val CAPABILITY_WEAR = "lockerlift_wear_app"
    const val CAPABILITY_MOBILE = "lockerlift_mobile_app"
}
