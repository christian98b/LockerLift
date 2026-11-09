package com.lockerlift.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migration coverage for the schemas that were never exported (v2 shipped at
 * tag v1.9.1) through the current exported schema.
 *
 * v2 is recreated from the entity definitions at that tag. Room then runs the
 * same [LockerLiftDatabase.migrations] list production uses. The result is
 * checked against the committed schema export and against Room's own
 * post-migration validation (opening the database throws if the migrated
 * schema does not match the entities).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockerLiftMigrationTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true }
    private val databases = mutableListOf<LockerLiftDatabase>()

    @Before
    fun cleanFiles() {
        context.databaseList().forEach { name ->
            context.deleteDatabase(name)
        }
    }

    @After
    fun closeDatabases() {
        databases.forEach { runCatching { it.close() } }
    }

    @Test
    fun migrations_coverEveryStepFromShippedV2ToExportedSchema() {
        val schemaVersion = exportedSchemaVersion()
        val migrations = LockerLiftDatabase.migrations()
        assertEquals((2 until schemaVersion).toList(), migrations.map { it.startVersion })
        assertEquals((3..schemaVersion).toList(), migrations.map { it.endVersion })
    }

    @Test
    fun exportedSchema_matchesFreshDatabase() {
        val expected = exportedTables()
        val fresh = openFresh()
        val actual = readTables(fresh.openHelper.readableDatabase)
        assertEquals(exportedSchemaVersion(), fresh.openHelper.readableDatabase.version)
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (table, columns) ->
            assertEquals(table, columns, actual.getValue(table))
        }
    }

    @Test
    fun migrateFromVersion2_reachesExportedSchemaAndKeepsRows() {
        val name = "migration-v2.db"
        context.deleteDatabase(name)
        createVersion2Database(context.getDatabasePath(name))

        val migrated = RoomMigrationOpener.open(context, name)
        databases += migrated
        val db = migrated.openHelper.writableDatabase

        assertEquals(exportedSchemaVersion(), db.version)
        val actual = readTables(db)
        val expected = exportedTables()
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (table, columns) ->
            assertEquals(table, columns, actual.getValue(table))
        }

        db.query(
            "SELECT weight_unit, weight_direction, is_deleted, weight_stack_values_kg " +
                "FROM machines WHERE id = 'm-leg-press'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("KG", cursor.getString(0))
            assertEquals("NORMAL", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
            assertTrue(cursor.isNull(3))
        }

        db.query(
            "SELECT id FROM activity_types WHERE id IN (?, ?)",
            arrayOf(
                com.lockerlift.core.model.ActivityType.BUILTIN_RUNNING_ID,
                com.lockerlift.core.model.ActivityType.BUILTIN_BOULDERING_ID
            )
        ).use { cursor ->
            assertEquals(2, cursor.count)
        }

        db.query("SELECT COUNT(*) FROM workout_plans").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private fun openFresh(): LockerLiftDatabase {
        return LockerLiftDatabase.createInMemory(context).also { databases += it }
    }

    private fun schemaFile(): File {
        val relative = "schemas/com.lockerlift.core.database.LockerLiftDatabase/${exportedSchemaVersionGuess()}.json"
        val candidates = listOf(File(relative), File("core/database/$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: error("Exported schema not found. Looked in ${candidates.map { it.absolutePath }} from ${File(".").absolutePath}")
    }

    private fun exportedSchemaVersionGuess(): Int {
        val dirCandidates = listOf(
            File("schemas/com.lockerlift.core.database.LockerLiftDatabase"),
            File("core/database/schemas/com.lockerlift.core.database.LockerLiftDatabase")
        )
        val dir = dirCandidates.firstOrNull { it.isDirectory }
            ?: error("Schema directory missing. cwd=${File(".").absolutePath}")
        return dir.listFiles().orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .maxOrNull()
            ?: error("No schema json in ${dir.absolutePath}")
    }

    private fun exportedSchemaVersion(): Int {
        val root = json.parseToJsonElement(schemaFile().readText()).jsonObject
        return root.getValue("database").jsonObject.getValue("version").jsonPrimitive.int
    }

    private fun exportedTables(): Map<String, Set<String>> {
        val root = json.parseToJsonElement(schemaFile().readText()).jsonObject
        val entities = root.getValue("database").jsonObject.getValue("entities").jsonArray
        return entities.associate { element ->
            val entity = element.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content
            val columns = entity.getValue("fields").jsonArray.map { field ->
                field.jsonObject.getValue("columnName").jsonPrimitive.content
            }.toSet()
            table to columns
        }
    }

    private fun readTables(db: SupportSQLiteDatabase): Map<String, Set<String>> {
        val tables = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table', 'android_metadata')"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tables += cursor.getString(0)
            }
        }
        return tables.associateWith { table ->
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
            }
            columns
        }
    }

    /**
     * Schema v2 as Room created it at tag v1.9.1 (`@Database(version = 2)`):
     * machines still have the unique name index, and none of the columns added
     * by migrations 3 through 9 exist yet.
     */
    private fun createVersion2Database(file: File) {
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        sqlite.version = 2
        sqlite.execSQL("PRAGMA foreign_keys = ON")
        sqlite.execSQL(
            """
            CREATE TABLE `machines` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `target_muscle_group` TEXT NOT NULL,
                `machine_settings_note` TEXT,
                `default_increment_kg` REAL NOT NULL,
                `default_cadence` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        sqlite.execSQL("CREATE UNIQUE INDEX `index_machines_name` ON `machines` (`name`)")
        sqlite.execSQL(
            """
            CREATE TABLE `workout_templates` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `is_archived` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE `template_machine_cross_ref` (
                `template_id` TEXT NOT NULL,
                `machine_id` TEXT NOT NULL,
                `sort_order` INTEGER NOT NULL,
                PRIMARY KEY(`template_id`, `machine_id`),
                FOREIGN KEY(`template_id`) REFERENCES `workout_templates`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        sqlite.execSQL("CREATE INDEX `index_template_machine_cross_ref_template_id` ON `template_machine_cross_ref` (`template_id`)")
        sqlite.execSQL("CREATE INDEX `index_template_machine_cross_ref_machine_id` ON `template_machine_cross_ref` (`machine_id`)")
        sqlite.execSQL(
            """
            CREATE TABLE `workout_sessions` (
                `id` TEXT NOT NULL,
                `template_id` TEXT,
                `start_time` INTEGER NOT NULL,
                `end_time` INTEGER,
                `origin_device` TEXT NOT NULL,
                `sync_status` TEXT NOT NULL,
                `notes` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`template_id`) REFERENCES `workout_templates`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        sqlite.execSQL("CREATE INDEX `index_workout_sessions_template_id` ON `workout_sessions` (`template_id`)")
        sqlite.execSQL(
            """
            CREATE TABLE `session_machine_instances` (
                `id` TEXT NOT NULL,
                `session_id` TEXT NOT NULL,
                `machine_id` TEXT NOT NULL,
                `execution_order` INTEGER NOT NULL,
                `is_skipped` INTEGER NOT NULL,
                `custom_settings_note` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`session_id`) REFERENCES `workout_sessions`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        sqlite.execSQL("CREATE INDEX `index_session_machine_instances_session_id` ON `session_machine_instances` (`session_id`)")
        sqlite.execSQL("CREATE INDEX `index_session_machine_instances_machine_id` ON `session_machine_instances` (`machine_id`)")
        sqlite.execSQL(
            """
            CREATE TABLE `workout_sets` (
                `id` TEXT NOT NULL,
                `session_machine_id` TEXT NOT NULL,
                `set_number` INTEGER NOT NULL,
                `reps` INTEGER NOT NULL,
                `weight_kg` REAL NOT NULL,
                `cadence` TEXT,
                `set_type` TEXT NOT NULL,
                `completed_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`session_machine_id`) REFERENCES `session_machine_instances`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        sqlite.execSQL("CREATE INDEX `index_workout_sets_session_machine_id` ON `workout_sets` (`session_machine_id`)")
        sqlite.execSQL(
            """
            CREATE TABLE `sync_queue` (
                `id` TEXT NOT NULL,
                `session_id` TEXT NOT NULL,
                `payload_json` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `retry_count` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `last_attempt_at` INTEGER,
                `item_type` TEXT NOT NULL,
                `error_message` TEXT,
                `target_device_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE `deleted_sessions` (
                `session_id` TEXT NOT NULL,
                `deleted_at` INTEGER NOT NULL,
                `origin_device` TEXT NOT NULL,
                `ttl_days` INTEGER NOT NULL,
                PRIMARY KEY(`session_id`)
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            INSERT INTO machines (
                id, name, target_muscle_group, machine_settings_note,
                default_increment_kg, default_cadence, updated_at
            ) VALUES ('m-leg-press', 'Leg Press', 'Legs', NULL, 2.5, NULL, 1000)
            """.trimIndent()
        )
        sqlite.close()
    }
}

/**
 * Opens a file database with the production migration list and the framework
 * SQLite driver. Kept out of [LockerLiftDatabase] so the test does not go
 * through the SQLCipher factory (migration SQL itself is driver-agnostic).
 */
private object RoomMigrationOpener {
    fun open(context: android.content.Context, name: String): LockerLiftDatabase {
        return androidx.room.Room.databaseBuilder(context, LockerLiftDatabase::class.java, name)
            .addMigrations(*LockerLiftDatabase.migrations())
            .allowMainThreadQueries()
            .build()
    }
}
