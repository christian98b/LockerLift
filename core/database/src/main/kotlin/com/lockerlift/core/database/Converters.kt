package com.lockerlift.core.database

import androidx.room.TypeConverter
import com.lockerlift.core.model.QueueStatus
import com.lockerlift.core.model.SetType
import com.lockerlift.core.model.SyncStatus
import com.lockerlift.core.model.WeightUnit

class Converters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = runCatching {
        SyncStatus.valueOf(value)
    }.getOrDefault(SyncStatus.LOCAL_ONLY)

    @TypeConverter
    fun fromSetType(value: SetType): String = value.name

    @TypeConverter
    fun toSetType(value: String): SetType = runCatching {
        SetType.valueOf(value)
    }.getOrDefault(SetType.NORMAL)

    @TypeConverter
    fun fromQueueStatus(value: QueueStatus): String = value.name

    @TypeConverter
    fun toQueueStatus(value: String): QueueStatus = runCatching {
        QueueStatus.valueOf(value)
    }.getOrDefault(QueueStatus.PENDING)
}

    @TypeConverter
    fun fromWeightUnit(value: WeightUnit): String = value.name

    @TypeConverter
    fun toWeightUnit(value: String): WeightUnit = runCatching {
        WeightUnit.valueOf(value)
    }.getOrDefault(WeightUnit.KG)
