package com.lockerlift.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lockerlift.core.database.dao.MachineDao
import com.lockerlift.core.database.dao.SyncQueueDao
import com.lockerlift.core.database.dao.WorkoutSessionDao
import com.lockerlift.core.database.dao.WorkoutTemplateDao
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.SessionMachineInstanceEntity
import com.lockerlift.core.database.entity.SyncQueueEntity
import com.lockerlift.core.database.entity.TemplateMachineCrossRefEntity
import com.lockerlift.core.database.entity.WorkoutSessionEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.entity.WorkoutTemplateEntity

@Database(
    entities = [
        MachineEntity::class,
        WorkoutTemplateEntity::class,
        TemplateMachineCrossRefEntity::class,
        WorkoutSessionEntity::class,
        SessionMachineInstanceEntity::class,
        WorkoutSetEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LockerLiftDatabase : RoomDatabase() {

    abstract fun machineDao(): MachineDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        private const val DATABASE_NAME = "lockerlift.db"

        @Volatile
        private var INSTANCE: LockerLiftDatabase? = null

        fun getInstance(context: Context): LockerLiftDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): LockerLiftDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LockerLiftDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
