package com.lockerlift.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model for tracking deleted workout sessions.
 * Used for tombstone-based zombie workout detection during sync.
 */
@Serializable
data class DeletedSession(
    val sessionId: String,
    val deletedAt: Long = System.currentTimeMillis(),
    val originDevice: String, // "MOBILE" or "WEAR_OS"
    val ttlDays: Int = 30 // Default: 30 days retention
)
