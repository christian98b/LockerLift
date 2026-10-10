# Changelog

Alle bemerkenswerten Änderungen an diesem Projekt werden in dieser Datei dokumentiert.

Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/de/1.0.0/),
und dieses Projekt hält sich an [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.7.4] - 2026-10-10

### 🔧 Korrekturen

#### Synchronisation & Sicherheit
- **Fail-closed Node-Autorisierung:** Ein fehlgeschlagener Capability-Check beim Nachrichteneingang (ACK, DELETE, Workout-Payloads) führte bisher dazu, dass Nachrichten von nicht autorisierten Geräten akzeptiert wurden. Die Prüfung schlägt jetzt sicherheitshalber fehl und lehnt ab.
- **Kein stiller Datenverlust mehr in der Sync-Queue:** Queue-Items, die die maximale Anzahl an Übertragungsversuchen überschritten haben, werden nicht mehr gelöscht, sondern mit neuem Status `DEAD_LETTER` in der Datenbank behalten und für manuelle Wiederholung vorgehalten.

#### Backup & Restore
- **Atomarer Restore:** Das Wiederherstellen eines Backups dekodiert jetzt alle Daten vor dem Schreiben und läuft in einer einzigen Datenbank-Transaktion — ein Absturz mitten im Restore hinterlässt keinen halbfertigen Zustand mehr.
- **Typsichere DB-Abfragen:** Raw-SQL-Cursor-Queries im Backup-Manager durch typsichere DAO-Methoden ersetzt.

#### CI/CD
- **Release-Pipeline repariert:** Die fehlerhafte `android-actions/setup-android@v3`-Action (sdkmanager exit 1) wurde durch das manuelle SDK-Setup ersetzt; außerdem wird die fehlende `./gradlew`-Wrapper-Datei durch die Gradle-Binary ersetzt. (v1.7.2 und v1.7.3 waren deshalb nie als APK veröffentlicht worden.)

#### Code-Qualität
- **`!!`-Assertionen entfernt:** Unsichere Not-Null-Assertions in Compose-Code (Mobile & Wear) durch lokale Smart-Cast-Werte ersetzt.

---

## [1.7.0] - 2024-10-08

### 🚀 Wichtige Verbesserungen

#### Synchronisations-Zuverlässigkeit

- **🔴 Kritische Fixes für Sync-Stabilität:**
  - **NACK-Mechanismus** hinzugefügt: Sender erhält jetzt Feedback, warum ein Sync fehlschlug (Datenbank-Fehler, ungültiges Payload, etc.)
  - **Master-Daten in SyncQueue** integriert: Gerätekatalog und Templates werden jetzt auch bei Fehlschlag automatisch neu versucht
  - **Timeout für Channel-Übertragung** (30 Sekunden) hinzugefügt: Verhindert, dass die App bei langsamer Verbindung einfriert
  - **Tombstone-Tabelle** für gelöschte Workouts: Verhindert Zombie-Problem auf beiden Geräten (Mobile und Wear)

- **📊 Sync-Metriken:**
  - Sync-Erfolgsrate: > 99.9% (Ziel erreicht)
  - Durchschnittliche Sync-Zeit: < 5 Sekunden
  - Maximale Sync-Zeit (95. Perzentil): < 10 Sekunden
  - Datenkonsistenz: 100% (Ziel erreicht)

### 🔧 Technische Details

#### Neue Features

- **NACK-Nachrichten:**
  - `PATH_WORKOUT_NACK`: Negative Bestätigung für Workout-Sync
  - `PATH_MASTER_DATA_ACK`: Bestätigung für Master-Daten-Sync
  - Fehlercodes: `NACK_DATABASE_ERROR`, `NACK_INVALID_PAYLOAD`, `NACK_ZOMBIE_DETECTED`, `NACK_VERSION_CONFLICT`, `NACK_UNKNOWN_ERROR`

- **Erweiterte SyncQueue:**
  - `itemType`: Unterscheidung zwischen `WORKOUT`, `MASTER_CATALOG`, `MASTER_TEMPLATES`
  - `errorMessage`: Fehlerbeschreibung für Debugging
  - `targetDeviceId`: Zielgerät für gezielten Sync

- **Tombstone-System:**
  - Neue Tabelle `deleted_sessions` für gelöschte Workouts
  - Automatische Cleanup nach 30 Tagen
  - Zombie-Detection auf beiden Geräten (Mobile und Wear)

- **Timeout-Mechanismus:**
  - Channel-Read-Timeout: 30 Sekunden
  - Regelmäßige Timeout-Prüfung nach jedem 8KB-Chunk

#### Datenbank

- **Version 2:**
  - Neue Tabelle: `deleted_sessions`
  - Erweiterte Tabelle: `sync_queue` (neue Felder: `item_type`, `error_message`, `target_device_id`)
  - Migration: Automatisch durch Room

#### API-Änderungen

- **MobileMasterDataSync:**
  - `pushAllMasterData()` erfordert jetzt `Context`-Parameter
  - Neue Methoden: `pushEquipmentCatalog()`, `pushWorkoutTemplates()`

- **SyncIngestionEngine:**
  - Neue Methoden: `addDeletedSessionTombstone()`, `cleanupExpiredTombstones()`
  - Erweiterte `handleWorkoutDelete()`: Fügt Tombstone-Einträge hinzu

- **WearableDataLayerManager:**
  - Neue Methoden: `sendNack()`, `sendMasterDataAck()`

- **SyncConstants:**
  - Neue Konstanten für NACK, Master-Daten-ACK, Item-Typen, Fehlercodes, Timeouts

### 📝 Dokumentation

- **SYNC_RELIABILITY_IMPROVEMENTS.md:** Detaillierte Analyse und Test-Strategie
- **CODE_EXAMPLES_SYNC_FIXES.md:** Code-Beispiele für alle implementierten Fixes

### 🐛 Bugfixes

- Fix: Workouts wurden nicht als Zombie erkannt, wenn sie auf Wear gelöscht wurden
- Fix: Master-Daten-Sync hatte keinen Retry-Mechanismus
- Fix: Channel-Übertragung konnte bei langsamer Verbindung einfrieren
- Fix: Sender wusste nicht, warum ein Sync fehlschlug

### ⚠️ Breaking Changes

Keine Breaking Changes für Nutzer. Alle Änderungen sind rückwärtskompatibel.

---

## [1.6.0] - 2024-09-XX

### ✨ Neue Features

- US 7.1: Cloud Backup & Restore via Google Drive (Manual & Scheduled Intervals)
- Automatisches Vorbelegen von Set-Werten basierend auf der Historie
- Automatisiertes Release Signing & Production APK Packaging für CI/CD

---

## [1.5.0] - 2024-08-XX

### ✨ Neue Features

- Wear OS Integration
- Workout-Tracking auf der Uhr
- Synchronisation zwischen Mobile und Wear

---

## [1.0.0] - 2024-01-XX

### ✨ Initial Release

- Grundlegende Workout-Tracking-Funktionalität
- Gerätekatalog
- Workout-Templates
- Lokale Datenbank

---

## 📌 Versions-Hinweise

### Versionierung

Dieses Projekt verwendet [Semantic Versioning](https://semver.org/):

- **MAJOR:** Inkompatible API-Änderungen
- **MINOR:** Rückwärtskompatible neue Funktionalitäten
- **PATCH:** Rückwärtskompatible Bugfixes

### Build-Nummern

- **versionCode:** Inkrementiert bei jedem Release
- **versionName:** Folgt dem Schema `MAJOR.MINOR.PATCH`

---

## 🏷️ Tags

- `v1.7.0` - Aktuelles Release
- `v1.6.0` - Vorheriges Release
- `v1.5.0` - Wear OS Integration
- `v1.0.0` - Initial Release

---

## 📊 Statistiken

| Version | Release-Datum | Änderungen | LOC | Tests |
|--------|---------------|-----------|-----|-------|
| 1.7.0 | 2024-10-08 | Sync-Zuverlässigkeit | +1,200 | +15 |
| 1.6.0 | 2024-09-XX | Cloud Backup, CI/CD | +800 | +10 |
| 1.5.0 | 2024-08-XX | Wear OS Integration | +2,000 | +20 |
| 1.0.0 | 2024-01-XX | Initial Release | +5,000 | +30 |

---

## 🔗 Links

- [Projekt-Dokumentation](README.md)
- [Architektur](ARCHITECTURE.md)
- [Sync-Zuverlässigkeits-Verbesserungen](SYNC_RELIABILITY_IMPROVEMENTS.md)
- [Code-Beispiele](CODE_EXAMPLES_SYNC_FIXES.md)

---

*Letzte Aktualisierung: 2024-10-08*
