# LockerLift – Local-First Open-Source Gym Tracker (Wear OS & Android)

LockerLift ist eine datenschutzfreundliche, modulare Open-Source-Lösung für Kraftsport-Tracking. Das System ist speziell für das Szenario konzipiert, dass das Smartphone während des Trainings im Spind verbleibt, während die Erfassung vollständig autark über eine Wear OS Smartwatch (z. B. Samsung Galaxy Watch) erfolgt.

---

## 1. Systemarchitektur & Kernprinzipien

### 1.1 Local-First & Offline-Autonomie
* **Keine Cloud-Pflicht:** Sämtliche Daten liegen primär in lokalen SQLite-/Room-Datenbanken auf dem jeweiligen Gerät.
* **Autarkes Wear OS Modul:** Das Wearable fungiert nicht als Fernbedienung, sondern betreibt eine vollwertige, eigenständige App mit lokaler Persistenz. Ein Training kann ohne aktive Bluetooth-Verbindung gestartet, editiert und beendet werden.
* **Store-and-Forward Synchronisation:** Trainingsdaten werden auf der Uhr in eine Synchronisations-Queue geschrieben und automatisch übertragen, sobald eine Verbindung zum Smartphone besteht.

### 1.2 Entkoppeltes Domänenmodell
* **Maschinenkatalog (Global):** Maschinen und Übungen existieren als globale Entitäten unabhängig von Trainingsplänen.
* **Templates (Vorlagen):** Definieren wiederverwendbare Hüllen ($n$-Routinen wie Push, Pull, Beine), die flexibel mit Maschinen bestückt oder initial komplett leer („Cold-Start“) sein können.
* **Workout Sessions (Aktive Instanzen):** Eine aktive Session instanziiert eine isolierte Kopie der konfigurierten Maschinen. Modifikationen während des Trainings (Hinzufügen, Austauschen, Löschen) beeinflussen das Template nicht, es sei denn, der Nutzer konsolidiert die Änderungen beim Abschluss explizit.

### 1.3 Health Connect als Integrations-Broker
Das Smartphone-Modul fungiert als Bridge zur Android Health Connect API (`EXERCISE_TYPE_STRENGTH_TRAINING`), um abgeschlossene Einheiten aggregiert an Google Fit, Samsung Health und Drittsysteme zu übergeben.

---

## 2. Tech-Stack

* **Programmiersprache:** Kotlin (100%)
* **UI Framework:** 
  * Mobile: Jetpack Compose
  * Wear OS: Compose for Wear OS & Horologist
* **Lokale Persistenz:** Room Database (SQLite) mit SQLCipher-Unterstützung
* **Asynchronität:** Kotlin Coroutines & StateFlow / SharedFlow
* **Hintergrundverarbeitung:** Android WorkManager (Sync-Worker, Health Connect Push)
* **Gerätekommunikation:** Google Play Services Wearable Data Layer API (`ChannelClient` & `DataClient`)
* **Gesundheitsschnittstelle:** AndroidX Health Connect Client API
* **Dependency Injection:** Jetpack Hilt / Koin

---

## 3. Datenmodell & Schema

```text
+-----------------------------+         +-------------------------------+
|         Machine             |         |        WorkoutTemplate        |
+-----------------------------+         +-------------------------------+
| id: UUID (PK)               |         | id: UUID (PK)                 |
| name: String                |         | name: String                  |
| target_muscle_group: String |         | description: String?          |
| machine_settings_note: String?        | is_archived: Boolean          |
| default_increment_kg: Float |         | created_at: Long              |
| default_cadence: String?    |         +---------------+---------------+
+--------------+--------------+                         |
               |                                        | 1:n
               | 1:n                                    v
               |                        +-------------------------------+
               |                        |    TemplateMachineCrossRef    |
               |                        +-------------------------------+
               |                        | template_id: UUID (FK)        |
               |                        | machine_id: UUID (FK)         |
               |                        | sort_order: Int               |
               |                        +-------------------------------+
               |
               +----------------------------------------+
               |                                        |
               v 1:n                                    v 1:n
+-------------------------------+       +-------------------------------+
|     SessionMachineInstance    |       |        WorkoutSession         |
+-------------------------------+       +-------------------------------+
| id: UUID (PK)                 |       | id: UUID (PK)                 |
| session_id: UUID (FK)         |<----->| template_id: UUID? (FK, opt.) |
| machine_id: UUID (FK)         |  n:1  | start_time: Long              |
| execution_order: Int          |       | end_time: Long?               |
| is_skipped: Boolean           |       | origin_device: String         |
+---------------+---------------+       | sync_status: SyncStatus       |
                |                       +-------------------------------+
                | 1:n
                v
+-------------------------------+
|          WorkoutSet           |
+-------------------------------+
| id: UUID (PK)                 |
| session_machine_id: UUID (FK) |
| set_number: Int               |
| reps: Int                     |
| weight_kg: Float              |
| cadence: String? (z.B. 3-1-1-0)
| set_type: SetType (WARMUP,..) |
| completed_at: Long            |
+-------------------------------+
```

---

## 4. Synchronisations-Protokoll (Wearable Data Layer)

### 4.1 Kanalwahl & Strategie

* **Stammdaten (Maschinen & Vorlagen):** Übertragung via `DataClient` (DataItem API) zur automatischen, zustandsbasierten Replikation auf allen gekoppelten Geräten.
* **Workout Payloads (Historie & Sessions):** Übertragung serialisierter JSON- oder Protocol Buffers-Payloads via `ChannelClient` für atomare Datentransfers mit Streaming-Integrität.

### 4.2 Sync-Queue Ablauf (Watch $\to$ Phone)

1. Bei Workout-Abschluss auf der Uhr wird die `WorkoutSession` inklusive aller `SessionMachineInstance`- und `WorkoutSet`-Datensätze als Payload in die lokale Tabelle `SyncQueue` geschrieben (`status = PENDING`).
2. Der `WearableSyncService` lauscht auf Verbindungsänderungen (`onPeerConnected`).
3. Sobald eine Verbindung besteht, wird die Payload per `ChannelClient` an das Smartphone gestreamt.
4. Das Smartphone validiert den Payload, persistiert ihn in der Room-DB, triggert den Health Connect Export und sendet ein Acknowledgment-Paket via `MessageClient`.
5. Nach Empfang des Acks markiert die Uhr den Queue-Eintrag als `SYNCED` oder entfernt ihn.

---

## 5. Projektstruktur

```text
LockerLift/
├── core/
│   ├── model/                  # Reine Domain Models & Enums
│   ├── database/               # Room Entities, DAOs, Migrations
│   ├── sync/                   # Data Layer Manager, Payload Serializer
│   └── healthconnect/          # Health Connect Client & Record Builder
├── mobile/                     # Smartphone Android App
│   ├── ui/                     # Jetpack Compose Screens (Katalog, Historie, Templates)
│   ├── tracking/               # Paralleles Mobile-Tracking Interface
│   └── service/                # Background Listener & Sync-Handling
└── wear/                       # Wear OS Smartwatch App
    ├── presentation/           # Horologist / Wear Compose UIs (Active Workout, Reps Input)
    ├── tracking/               # Foreground Service für aktives Workout
    └── communication/          # Queue Worker & Data Layer Connector
```

---

## 6. Dokumentation & Richtlinien

* [USER_STORIES.md](USER_STORIES.md) – Anforderungsprofile, Epics und Akzeptanzkriterien.
* [ARCHITECTURE.md](ARCHITECTURE.md) – Detaillierte Architekturentscheidungen, DB-Schemas und Sync-Flows.
* [AGENT.md](AGENT.md) – Konventionen, Invarianten und Implementierungsrichtlinien für Entwickler und Coding-Agents.
