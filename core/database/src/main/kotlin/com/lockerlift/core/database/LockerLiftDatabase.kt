package com.lockerlift.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lockerlift.core.database.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.lockerlift.core.database.dao.ActiveSessionStateDao
import com.lockerlift.core.database.dao.ActivityEntryDao
import com.lockerlift.core.database.dao.ActivityTypeDao
import com.lockerlift.core.database.dao.DeletedSessionDao
import com.lockerlift.core.database.dao.MachineDao
import com.lockerlift.core.database.dao.SyncQueueDao
import com.lockerlift.core.database.dao.WorkoutSessionDao
import com.lockerlift.core.database.dao.WorkoutTemplateDao
import com.lockerlift.core.database.entity.ActiveSessionStateEntity
import com.lockerlift.core.database.entity.ActivityEntryEntity
import com.lockerlift.core.database.entity.ActivityTypeEntity
import com.lockerlift.core.database.entity.DeletedSessionEntity
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
        SyncQueueEntity::class,
        DeletedSessionEntity::class,
        ActiveSessionStateEntity::class,
        ActivityTypeEntity::class,
        ActivityEntryEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LockerLiftDatabase : RoomDatabase() {

    abstract fun machineDao(): MachineDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun deletedSessionDao(): DeletedSessionDao
    abstract fun activeSessionStateDao(): ActiveSessionStateDao
    abstract fun activityTypeDao(): ActivityTypeDao
    abstract fun activityEntryDao(): ActivityEntryDao

    companion object {
        private const val DATABASE_NAME = "lockerlift.db"

        private const val BUILTIN_RUNNING_ID = com.lockerlift.core.model.ActivityType.BUILTIN_RUNNING_ID
        private const val BUILTIN_BOULDERING_ID = com.lockerlift.core.model.ActivityType.BUILTIN_BOULDERING_ID

        /**
         * Built-in Running type (Issue #50) seeds two fields: km and time,
         * per the owner's customization comment on the issue.
         */
        private val BUILTIN_RUNNING_FIELDS = listOf(
            com.lockerlift.core.model.ActivityFieldDefinition(
                id = "field-builtin-running-km",
                name = "km",
                type = com.lockerlift.core.model.ActivityFieldType.NUMBER
            ),
            com.lockerlift.core.model.ActivityFieldDefinition(
                id = "field-builtin-running-time",
                name = "time",
                type = com.lockerlift.core.model.ActivityFieldType.TEXT
            )
        )
        private val BUILTIN_RUNNING_FIELDS_JSON =
            com.lockerlift.core.model.ActivityType.fieldsToJson(BUILTIN_RUNNING_FIELDS)

        /**
         * v4 adds soft-delete flags (Issue #40): machines.is_deleted and
         * workout_templates.is_deleted. The unique index on machines.name is
         * dropped so a soft-deleted machine no longer blocks its name for
         * newly created machines.
         */
        /**
         * v5 adds per-machine weight unit (Issue #37): machines.weight_unit
         * with default KG so existing historical data stays untouched.
         */
        /**
         * v6 adds the weight direction flag (Issue #35):
         * machines.weight_direction with default NORMAL. Machines flagged
         * INVERSE are assisted machines (Assist-Dip, Assist-Chin) where less
         * selected weight means more load; the default keeps existing data
         * untouched.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_types` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `fields_json` TEXT NOT NULL,
                        `is_builtin` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_entries` (
                        `id` TEXT NOT NULL,
                        `activity_type_id` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `field_values_json` TEXT NOT NULL,
                        `notes` TEXT,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`activity_type_id`) REFERENCES `activity_types`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activity_entries_activity_type_id` " +
                        "ON `activity_entries` (`activity_type_id`)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `activity_types` (`id`, `name`, `fields_json`, `is_builtin`, `created_at`) " +
                        "VALUES (?, ?, ?, 1, ?)",
                    arrayOf(
                        BUILTIN_RUNNING_ID,
                        "Running",
                        BUILTIN_RUNNING_FIELDS_JSON,
                        System.currentTimeMillis()
                    )
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `activity_types` (`id`, `name`, `fields_json`, `is_builtin`, `created_at`) " +
                        "VALUES (?, ?, ?, 1, ?)",
                    arrayOf(
                        BUILTIN_BOULDERING_ID,
                        "Bouldering",
                        "[]",
                        System.currentTimeMillis()
                    )
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE machines ADD COLUMN weight_direction TEXT NOT NULL DEFAULT 'NORMAL'")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE machines ADD COLUMN weight_unit TEXT NOT NULL DEFAULT 'KG'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_machines_name`")
                db.execSQL("ALTER TABLE machines ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_templates ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `active_session_state` (
                        `id` INTEGER NOT NULL,
                        `payload_json` TEXT NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: LockerLiftDatabase? = null

        fun getInstance(context: Context): LockerLiftDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): LockerLiftDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                LockerLiftDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }

        private fun buildDatabase(context: Context): LockerLiftDatabase {
            val appContext = context.applicationContext
            // The sqlcipher-android library requires the native library to be loaded
            // into memory BEFORE any database operation. Unlike the legacy
            // android-database-sqlcipher (SQLiteDatabase.loadLibs), sqlcipher-android
            // no longer loads it implicitly. The 4.17 native binary registers its JNI
            // methods via JNI_OnLoad/RegisterNatives, so without this call the first DB
            // access throws UnsatisfiedLinkError on nativeOpen.
            System.loadLibrary("sqlcipher")

            val passphrase = DatabaseKeyManager.getOrCreatePassphrase(appContext)
            migratePlaintextIfNeeded(appContext, passphrase)

            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                appContext,
                LockerLiftDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
                    SQLiteDatabase.OPEN_READWRITE,
                    null
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
