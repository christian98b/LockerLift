package com.lockerlift.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lockerlift.core.database.security.DatabaseKeyManager
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
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
            val appContext = context.applicationContext
            SQLiteDatabase.loadLibs(appContext)

            val passphrase = DatabaseKeyManager.getOrCreatePassphrase(appContext)
            migratePlaintextIfNeeded(appContext, passphrase)

            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                appContext,
                LockerLiftDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .build()
        }

        private fun migratePlaintextIfNeeded(context: Context, passphrase: ByteArray) {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists() || dbFile.length() == 0L) return

            val isPlaintext = runCatching {
                java.io.FileInputStream(dbFile).use { stream ->
                    val header = ByteArray(16)
                    val read = stream.read(header)
                    read == 16 && header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
                }
            }.getOrDefault(false)

            if (isPlaintext) {
                val tempEncrypted = java.io.File(dbFile.parentFile, "$DATABASE_NAME.encrypted")
                if (tempEncrypted.exists()) tempEncrypted.delete()

                val plaintextDb = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    "",
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
                val hexKey = passphrase.joinToString("") { "%02x".format(it) }
                plaintextDb.rawExecSQL("ATTACH DATABASE '${tempEncrypted.absolutePath}' AS encrypted KEY \"x'$hexKey'\";")
                plaintextDb.rawExecSQL("SELECT sqlcipher_export('encrypted');")
                plaintextDb.rawExecSQL("DETACH DATABASE encrypted;")
                plaintextDb.close()

                dbFile.delete()
                tempEncrypted.renameTo(dbFile)
            }
        }
    }
}
