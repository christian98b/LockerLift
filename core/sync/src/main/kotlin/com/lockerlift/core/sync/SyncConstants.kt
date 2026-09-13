package com.lockerlift.core.sync

object SyncConstants {
    // DataClient paths (Master data replication)
    const val PATH_EQUIPMENT_CATALOG = "/equipment_catalog"
    const val PATH_WORKOUT_TEMPLATES = "/workout_templates"

    // ChannelClient paths (Large workout session stream)
    const val PATH_WORKOUT_CHANNEL = "/workout_payload_transfer"

    // MessageClient paths (RPC & Acknowledgment)
    const val PATH_WORKOUT_ACK = "/workout_ack"
    const val PATH_PING = "/sync_ping"

    // Wearable Data Layer Capabilities (Node verification)
    const val CAPABILITY_WEAR = "lockerlift_wear_app"
    const val CAPABILITY_MOBILE = "lockerlift_mobile_app"
}
