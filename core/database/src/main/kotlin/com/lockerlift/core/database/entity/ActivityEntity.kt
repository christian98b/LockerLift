package com.lockerlift.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lockerlift.core.model.ActivityEntry
import com.lockerlift.core.model.ActivityType

/**
 * Customizable activity type (Issue #50): running, bouldering, or any
 * user-created sport. Field definitions are stored as JSON so the user can
 * define an arbitrary number of fields per type (owner comment on #50).
 */
@Entity(tableName = "activity_types")
data class ActivityTypeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "fields_json")
    val fieldsJson: String = "[]",
    @ColumnInfo(name = "is_builtin")
    val isBuiltin: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

fun ActivityTypeEntity.toDomainModel() = ActivityType(
    id = id,
    name = name,
    fields = ActivityType.fieldsFromJson(fieldsJson),
    isBuiltin = isBuiltin,
    createdAt = createdAt
)

fun ActivityType.toEntity() = ActivityTypeEntity(
    id = id,
    name = name,
    fieldsJson = ActivityType.fieldsToJson(fields),
    isBuiltin = isBuiltin,
    createdAt = createdAt
)

/**
 * Logged occurrence of an activity (Issue #50): records that the activity
 * was performed on a given date. Values for the type's custom fields are
 * stored as JSON keyed by field id. Deleting a type cascades to its entries.
 */
@Entity(
    tableName = "activity_entries",
    foreignKeys = [
        ForeignKey(
            entity = ActivityTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_type_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["activity_type_id"])]
)
data class ActivityEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "activity_type_id")
    val activityTypeId: String,
    @ColumnInfo(name = "date")
    val date: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "field_values_json")
    val fieldValuesJson: String = "{}",
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

fun ActivityEntryEntity.toDomainModel() = ActivityEntry(
    id = id,
    activityTypeId = activityTypeId,
    date = date,
    fieldValues = ActivityEntry.valuesFromJson(fieldValuesJson),
    notes = notes,
    createdAt = createdAt
)

fun ActivityEntry.toEntity() = ActivityEntryEntity(
    id = id,
    activityTypeId = activityTypeId,
    date = date,
    fieldValuesJson = ActivityEntry.valuesToJson(fieldValues),
    notes = notes,
    createdAt = createdAt
)
