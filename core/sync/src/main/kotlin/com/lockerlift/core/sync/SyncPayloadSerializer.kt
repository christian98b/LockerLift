package com.lockerlift.core.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SyncPayloadSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun encodeSessionPayload(payload: WorkoutSessionPayload): String {
        return json.encodeToString(payload)
    }

    fun decodeSessionPayload(payloadJson: String): WorkoutSessionPayload {
        return json.decodeFromString(payloadJson)
    }
}
