<div align="center">

# 🏋️‍♂️ LockerLift

### **Local-First, Open-Source Gym Tracker für Android & Wear OS**
*Das Smartphone bleibt im Spind – volle Autonomie auf deiner Smartwatch.*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2028+-3DDC84.svg?style=for-the-badge&logo=android)](https://developer.android.com)
[![Wear OS](https://img.shields.io/badge/Wear%20OS-API%2030+-4285F4.svg?style=for-the-badge&logo=google)](https://developer.android.com/wear)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Health Connect](https://img.shields.io/badge/Health%20Connect-Integrated-00C853.svg?style=for-the-badge)](https://developer.android.com/health-and-fitness/guides/health-connect)
[![Local-First](https://img.shields.io/badge/Database-Room%20%2F%20Offline--First-orange.svg?style=for-the-badge)](https://developer.android.com/training/data-storage/room)

---

[📖 Dokumentation](#-dokumentation--ressourcen) •
[🎯 Das Spind-Szenario](#-das-spind-szenario-locker-isolation) •
[✨ Kernfunktionen](#-kernfunktionen) •
[🏗️ Architektur & Module](#-systemarchitektur--modulaufbau) •
[🔄 Sync-Protokoll](#-synchronisations-protokoll-wearable-data-layer) •
[🧪 Testing & Qualität](#-unit-testing--qualitätsgarantie) •
[🛠️ Setup & Build](#-getting-started--build)

---

</div>

## 🎯 Das Spind-Szenario (Locker Isolation)

> **Die Kernphilosophie:**  
> Viele Kraftsportler lassen ihr Smartphone bewusst im Umkleidespind – um Ablenkungen zu vermeiden, Diebstahl vorzubeugen oder weil sperrige Smartphones bei Übungen stören.  
> **LockerLift ist exakt für dieses Szenario gebaut.**

```mermaid
sequenceDiagram
    autonumber
    actor User as 🏋️ Sportler
    participant Watch as ⌚ Wear OS Watch
    participant Locker as 🔒 Spind (Außer Funkreichweite)
    participant Phone as 📱 Smartphone
    participant HC as 💚 Health Connect

    User->>Watch: Startet Workout im Freihantelbereich
    Note over Watch,Phone: Keine Bluetooth- oder WLAN-Verbindung!
    User->>Watch: Erfasst Sätze, Gewichte & Kadenz via Lünette (Rotary)
    Watch->>Watch: Speichert lokal in Room DB & SyncQueue
    User->>Watch: Workout beenden (Template-Konsolidierung)
    Note over User,Phone: Sportler kehrt nach dem Training zum Spind zurück
    Note over Watch,Phone: 📶 Bluetooth / WLAN Reconnect
    Watch->>Phone: Automatischer Stream via ChannelClient
    Phone->>Phone: Atomares DB-Insert (Room)
    Phone->>HC: Exportiert ExerciseSessionRecord
    Phone->>Watch: Quittierung (ACK via MessageClient)
    Watch->>Watch: SyncQueue-Eintrag archiviert/gelöscht
```

* **Kein Thin-Client:** Die Smartwatch-App ist eine vollständige, autarke Applikation mit lokaler Room-Datenbank.
* **Keine Cloud-Pflicht:** Deine Trainingsdaten gehören dir. Sie verbleiben verschlüsselt und lokal auf deinen Geräten.
* **Store-and-Forward Synchronisation:** Datensätze werden lokal gequeued und erst übertragen, wenn du nach dem Workout wieder am Spind bist.

---

## ✨ Kernfunktionen

### 📱 Smartphone App (`:mobile`)
* 📋 **Globaler Maschinen- & Übungskatalog:** Maschinen mit Zielmuskelgruppe, individueller Schrittweite (z. B. 2,5 kg) und Notizen für Geräteeinstellungen (z. B. *„Sitzhöhe Stufe 4“*).
* 📑 **$n$-Vorlagenverwaltung:** Beliebig viele Trainingspläne (Push, Pull, Legs, etc.) – inklusive Cold-Start Vorlagen (initial leer anlegen und spontan befüllen).
* 📈 **Trainingshistorie:** Detaillierte Einsicht in vergangene Sessions mit Satz-für-Satz Aufschlüsselung.
* 🌉 **Health Connect Bridge:** Nahtlose Übergabe an Google Fit, Samsung Health und Drittanbieter.

### ⌚ Standalone Wear OS App (`:wear`)
* 🚀 **100% Autonomes Tracking:** Funktioniert komplett ohne Handyverbindung.
* ⚙️ **Rotary Input Support:** Blitzschnelle Eingabe von Gewicht und Wiederholungen über die drehbare Lünette oder Touch-Buttons.
* 🔄 **Cold-Start & Ad-Hoc Erweiterung:** Maschinen können mitten im Training spontan hinzugefügt, ausgetauscht oder übersprungen werden.
* 💡 **Template-Konsolidierung:** Wurde der Plan im Training variiert? Die App fragt beim Abschluss: *„Änderungen als Standard im Plan speichern?“*.
* ⏱️ **Pausentimer mit Haptik:** Automatischer Countdown nach jedem Satz mit diskreter Vibration beim Ablauf.
* 🛡️ **Ongoing Activity Foreground Service:** Verhindert, dass Wear OS das Workout im Ambient-Mode oder bei Speicherdruck beendet.
* 📊 **Progressive Overload Empfehlungen:** Anzeige des letzten Leistungsstands an der Station und visuelle Vorschläge zur Gewichtssteigerung (z. B. Double Progression).

---

## 🏗️ Systemarchitektur & Modulaufbau

LockerLift setzt auf ein hochgradig modulares Monorepo mit strikter Trennung von Verantwortlichkeiten:

```mermaid
graph TD
    subgraph Applications ["Apps"]
        mobile[":mobile (Android Smartphone App)"]
        wear[":wear (Wear OS Smartwatch App)"]
    end

    subgraph Core ["Core Modules"]
        core_model[":core:model (Domain Entities & Enums)"]
        core_database[":core:database (Room Entities, DAOs, Cipher)"]
        core_sync[":core:sync (Data Layer Manager & Serializer)"]
        core_healthconnect[":core:healthconnect (Health Connect Bridge)"]
    end

    mobile --> core_model
    mobile --> core_database
    mobile --> core_sync
    mobile --> core_healthconnect

    wear --> core_model
    wear --> core_database
    wear --> core_sync

    core_database --> core_model
    core_sync --> core_model
    core_sync --> core_database
    core_healthconnect --> core_model

    classDef app fill:#2962FF,stroke:#fff,stroke-width:2px,color:#fff;
    classDef core fill:#00897B,stroke:#fff,stroke-width:2px,color:#fff;
    class mobile,wear app;
    class core_model,core_database,core_sync,core_healthconnect core;
```

### Modul-Matrix

| Modul | Typ | Zweck & Enthaltene Komponenten |
|---|---|---|
| [`:core:model`](file:///C:/Users/Chris/code/LockerLift/core/model) | Pure Kotlin | Plattformunabhängige Domain Models (`Machine`, `WorkoutSession`, `WorkoutSet`, Enums). Keine Android-Abhängigkeiten. |
| [`:core:database`](file:///C:/Users/Chris/code/LockerLift/core/database) | Android Library | Room Database, SQLite TypeConverters, DAOs (`MachineDao`, `WorkoutSessionDao`), Relationen & Cipher-Support. |
| [`:core:sync`](file:///C:/Users/Chris/code/LockerLift/core/sync) | Android Library | Google Play Services Wearable API Kapselung (`DataClient`, `ChannelClient`, `MessageClient`), JSON-Serializer, WorkManager Queue-Worker. |
| [`:core:healthconnect`](file:///C:/Users/Chris/code/LockerLift/core/healthconnect) | Android Library | AndroidX Health Connect Client, Permission Controller, Record Builder für `EXERCISE_TYPE_STRENGTH_TRAINING`. |
| [`:mobile`](file:///C:/Users/Chris/code/LockerLift/mobile) | Android App | Smartphone UI (Jetpack Compose Material 3), Katalog-, Vorlagen- und Historienverwaltung, Background Sync Listener. |
| [`:wear`](file:///C:/Users/Chris/code/LockerLift/wear) | Wear OS App | Wear OS UI (Horologist & Wear Compose), Rotary Input Steuerung, Workout Foreground Service, Rest Timer mit Vibration. |

---

## 🗄️ Datenbankschema & Entitäten

Alle Entitäten nutzen **UUIDv4 (`String`) als Primärschlüssel**, um dezentral auf Uhr und Smartphone Datensätze kollisionsfrei erzeugen zu können.

```mermaid
erDiagram
    Machine ||--o{ TemplateMachineCrossRef : "zugeordnet in"
    WorkoutTemplate ||--o{ TemplateMachineCrossRef : "enthält"
    Machine ||--o{ SessionMachineInstance : "instanziiert als"
    WorkoutSession ||--o{ SessionMachineInstance : "besteht aus"
    WorkoutSession }o--|| WorkoutTemplate : "basiert optional auf"
    SessionMachineInstance ||--o{ WorkoutSet : "beinhaltet"
    WorkoutSession ||--o| SyncQueue : "abgelegt in"

    Machine {
        string id PK "UUID"
        string name "Unique Index"
        string target_muscle_group
        string machine_settings_note
        float default_increment_kg
        string default_cadence
        long updated_at
    }

    WorkoutTemplate {
        string id PK "UUID"
        string name
        string description
        boolean is_archived
        long created_at
    }

    TemplateMachineCrossRef {
        string template_id FK, PK
        string machine_id FK, PK
        int sort_order
    }

    WorkoutSession {
        string id PK "UUID"
        string template_id FK "Nullable"
        long start_time
        long end_time "Nullable"
        string origin_device "WEAR_OS / MOBILE"
        string sync_status
    }

    SessionMachineInstance {
        string id PK "UUID"
        string session_id FK
        string machine_id FK
        int execution_order
        boolean is_skipped
    }

    WorkoutSet {
        string id PK "UUID"
        string session_machine_id FK
        int set_number
        int reps
        float weight_kg
        string cadence
        string set_type "NORMAL / WARMUP / DROPSET"
        long completed_at
    }

    SyncQueue {
        string id PK "UUID"
        string session_id FK
        string payload_json
        string status "PENDING / IN_TRANSIT / ACKNOWLEDGED"
        int retry_count
    }
```

---

## 🔄 Synchronisations-Protokoll (Wearable Data Layer)

LockerLift trennt bewusst die Kanäle der Wearable Data Layer API:

<details>
<summary><b>1. Stammdaten (Maschinen & Vorlagen) ➔ <code>DataClient</code></b></summary>

* **Pfad:** `/equipment_catalog`, `/workout_templates`
* **Mechanik:** Automatisch replizierter Key-Value-Speicher (DataItem API).
* **Konfliktregel:** Smartphone ist **Master (Single Source of Truth)**. Auf dem Smartphone gepflegte Maschinen und Pläne werden automatisch auf gekoppelte Uhren gespiegelt.
</details>

<details>
<summary><b>2. Workout Sessions (Historie & Sätze) ➔ <code>ChannelClient</code></b></summary>

* **Pfad:** `/workout_payload_transfer`
* **Mechanik:** Bidirektionaler Byte-Stream für atomare Datentransfers großer JSON-Payloads inklusive aller Sätze, Notizen und Zeiten.
* **Konfliktregel:** Smartwatch ist **Master** für Einheiten, die auf der Smartwatch ausgeführt wurden.
</details>

<details>
<summary><b>3. Quittierung & RPC ➔ <code>MessageClient</code></b></summary>

* **Pfad:** `/workout_ack`, `/sync_ping`
* **Mechanik:** Schnelle, unbestätigte Nachrichten mit geringer Latenz zur Übermittlung von Bestätigungs-IDs (Zwei-Wege-Handshake). Nach Empfang des ACKs wird die Session in der Queue der Uhr bereinigt.
</details>

---

## 🧪 Unit Testing & Qualitätsgarantie

> [!IMPORTANT]
> In LockerLift gilt ein **strenges Unit-Testing-Mandat**. Alle mathematischen Logiken, Progressionen, Enums, Converter, Mappings und Serialisierungen sind zu 100 % mit Unit-Tests abgedeckt.
> 
> **Regressionstest-Garantie:** Alle Tests müssen bei künftigen Änderungen fehlerfrei durchlaufen. Ein Test darf nur modifiziert oder gelöscht werden, wenn das Feature bewusst aus der Spezifikation entfernt wurde.

### Enthaltene Unit-Test-Suites:
* [`DomainModelTest.kt`](file:///C:/Users/Chris/code/LockerLift/core/model/src/test/kotlin/com/lockerlift/core/model/DomainModelTest.kt): Validierung von Instanziierung, UUID-Kollisionsfreiheit, Standardwerten und JSON-Serialisierung.
* [`SyncPayloadSerializerTest.kt`](file:///C:/Users/Chris/code/LockerLift/core/sync/src/test/kotlin/com/lockerlift/core/sync/SyncPayloadSerializerTest.kt): Verlustfreier Roundtrip komplexer Workout-Payload-Graphen.
* [`EntityMappingTest.kt`](file:///C:/Users/Chris/code/LockerLift/core/database/src/test/kotlin/com/lockerlift/core/database/EntityMappingTest.kt): Bidirektionale Mappings zwischen Domain Models und Room Entities sowie Validierung aller TypeConverter.

---

## 🤖 AI Agent & Developer Skills

Das Repository stattet Coding-Assistenten (und Entwickler) mit vordefinierten Skills im Ordner `.agents/skills/` aus:

* 🧭 [**`git-commit-guidelines`**](file:///C:/Users/Chris/code/LockerLift/.agents/skills/git-commit-guidelines/SKILL.md): Verbindliche Formatvorgaben nach Conventional Commits (v1.0.0), Scopes und Test-Commit-Pflicht.
* 🧪 [**`unit-testing-guidelines`**](file:///C:/Users/Chris/code/LockerLift/.agents/skills/unit-testing-guidelines/SKILL.md): Standards zur Teststrukturierung (AAA-Pattern, Room In-Memory, Coroutine Testing).
* 🚀 [**`feature-implementation-workflow`**](file:///C:/Users/Chris/code/LockerLift/.agents/skills/feature-implementation-workflow/SKILL.md): 8-Schritte-Workflow von der User Story über Architekturchecks und TDD bis zum atomaren Commit.

---

## 📖 Dokumentation & Ressourcen

| Dokument | Beschreibung |
|---|---|
| [**`USER_STORIES.md`**](file:///C:/Users/Chris/code/LockerLift/USER_STORIES.md) | Vollständiger Anforderungskatalog (Epics 1 bis 6) inklusive aller Akzeptanzkriterien. |
| [**`ARCHITECTURE.md`**](file:///C:/Users/Chris/code/LockerLift/ARCHITECTURE.md) | Detailliertes technisches Systemdesign, ERDs, Sequenzdiagramme und Wear OS Invarianten. |
| [**`AGENT.md`**](file:///C:/Users/Chris/code/LockerLift/AGENT.md) / [**`agents.md`**](file:///C:/Users/Chris/code/LockerLift/agents.md) | Verbindliche Entwicklerrichtlinien, Invarianten und aktuelles Implementierungsprotokoll. |

---

## 🛠️ Getting Started & Build

### Voraussetzungen
* **Java Development Kit (JDK):** Version 21
* **Android SDK:** Build-Tools 35, Compile SDK 35 (Min SDK: 28 für Phone, 30 für Wear OS)
* **Gradle:** 8.7+ (wird via Gradle Wrapper bereitgestellt)

### Unit Tests ausführen
```bash
# Führe alle Unit-Tests der Core-Module aus
./gradlew test
```

### Apps bauen
```bash
# Smartphone App (APK) bauen
./gradlew :mobile:assembleDebug

# Wear OS Smartwatch App (APK) bauen
./gradlew :wear:assembleDebug
```

---

<div align="center">
<b>LockerLift</b> • Entwickelt für Kraftsportler, die volle Konzentration und absolute Datensouveränität verlangen.
</div>
