# US 7.2: Local Storage Backup & Restore (`:mobile`)

  - `LocalBackupManager` (SAF-based export/restore, GZip + JSON via `SyncPayloadSerializer`, SHA-256 checksum, retention pruning).
  - `LocalBackupWorker` (`CoroutineWorker` / `PeriodicWorkRequest` for scheduled backups with configurable interval and keep-count).
  - `BackupResult` sealed class (`Success(fileName)`, `Error(message)`).
  - `SettingsScreen` (new Settings tab in bottom navigation: folder picker with `takePersistableUriPermission`, manual export, restore with schema validation, schedule dropdown (Disabled / Daily / Weekly), keep-count filter chips (3 / 5 / 10), share via `Intent.ACTION_SEND`).
  - `MainActivity` updated with `SETTINGS` tab and `Icons.Default.Settings` nav bar item.
  - `libs.versions.toml`: added `androidx-documentfile = 1.0.1` catalog entry.
  - `mobile/build.gradle.kts`: added `kotlin.serialization` plugin, `androidx.documentfile`, `kotlinx.serialization.json` dependencies.
  - `LocalBackupManagerTest.kt`: 8 pure JVM unit tests (GZip roundtrip, SHA-256 checksum consistency, filename pattern, schema version, `BackupResult.Success/Error`, empty GZip, distinct hash collision resistance).
  - All user-facing strings in EN (`values/strings.xml`) and DE (`values-de/strings.xml`): `tab_settings`, `settings_title`, and 22 `settings_*` keys.
  - Covers AK 7.2.1 (SAF directory picker), AK 7.2.2 (manual backup), AK 7.2.3 (scheduled backups + retention), AK 7.2.4 (restore + integrity validation), AK 7.2.5 (sharesheet).
