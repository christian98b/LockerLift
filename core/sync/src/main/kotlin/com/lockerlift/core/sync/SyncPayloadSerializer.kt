package com.lockerlift.core.sync

import com.lockerlift.core.model.Machine
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

    fun encodeTemplates(templates: List<WorkoutTemplatePayload>): String {
        return json.encodeToString(templates)
    }

    fun decodeTemplates(jsonString: String): List<WorkoutTemplatePayload> {
        return json.decodeFromString(jsonString)
    }

    fun encodeMachines(machines: List<Machine>): String {
        return json.encodeToString(machines)
    }

    fun decodeMachines(jsonString: String): List<Machine> {
        return json.decodeFromString(jsonString)
    }
}

