package com.lockerlift.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row (fixed id = 1) holding the JSON snapshot of the currently
 * active workout session. Written after every session mutation so the
 * workout survives process death (Locker Scenario durability mandate).
 */
@Entity(tableName = "active_session_state")
data class ActiveSessionStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = ACTIVE_ROW_ID,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ACTIVE_ROW_ID = 1
    }
}
