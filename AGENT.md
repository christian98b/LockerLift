# LockerLift – AI Agent & Developer Guidelines (`AGENT.md`)

Dieses Dokument dient als verbindliches Handbuch und Fortschrittsprotokoll für Entwickler und KI-Assistenten (z. B. Antigravity, Claude, Copilot), die am Projekt **LockerLift** arbeiten. Es definiert Leitlinien, Architekturvorgaben, Testregeln und den aktuellen Implementierungsstand.

---

## 1. Vision & Leitbild von LockerLift

LockerLift ist ein **datenschutzfreundlicher, lokaler Open-Source-Kraftsport-Tracker** für Android und Wear OS.

### Das Spind-Prinzip (The Locker Scenario)
> Das Smartphone verbleibt während des gesamten Trainings im Spind. Das Wear OS Device agiert vollkommen autonom ohne Funkverbindung (kein Bluetooth, kein WLAN).

**Konsequenz für jeden Code-Beitrag:**
* **Niemals** annehmen, dass während eines Workouts auf der Watch eine Verbindung zum Phone oder Internet existiert.
* Alle CRUD-Aktionen müssen lokal auf der Uhr ohne Latenz und ohne Fehler quittiert werden.
* Datenübertragung erfolgt ausschließlich asynchron nach dem **Store-and-Forward**-Prinzip.

---

## 2. Verbindliche Test-Vorgaben (Strict Unit Testing Mandate)

> [!IMPORTANT]
> **Unit Tests sind für jede Implementierung Pflicht:**
> 1. **100% Testabdeckung für Logik:** Jede neue Funktion, Geschäftslogik, Berechnung (z. B. Double Progression, Kadenz), jedes Datenmodell, Mapping, Konvertierung, Serialisierung und jeder DAO-Workflow **MUSS** durch automatisierte Unit-Tests abgedeckt werden.
> 2. **Bestehende Tests müssen immer funktionieren:** Alle bestehenden Unit-Tests müssen bei zukünftigen Änderungen und Erweiterungen fehlerfrei durchlaufen (Regressionstest-Garantie).
> 3. **Ausnahme für Test-Änderungen:** Ein Unit-Test darf **ausschließlich dann** modifiziert oder entfernt werden, wenn das konkret getestete Feature bewusst gelöscht oder durch eine neue Spezifikation abgelöst wurde.
> 4. **Zukunftssicherheit:** Zukünftige Implementierungen müssen die vorhandenen Test-Suites aktiv nutzen und erweitern. Vor dem Abschluss einer Implementierung ist sicherzustellen, dass die Tests für das Modul grün sind.

---

## 3. Nachverfolgung von Implementierungen (Progress & Tracking)

> [!NOTE]
> **Pflicht für jeden Agenten:** Wenn neue Features implementiert, angepasst oder erweitert werden, **MUSS** dieser Abschnitt in `AGENT.md` (und `agents.md`) sofort aktualisiert werden. Dokumentiere:
> * Welches Feature / welche User Story umgesetzt wurde.
> * Welche Dateien erstellt oder modifiziert wurden.
> * Welche Unit-Tests hinzugefügt wurden.

### Aktueller Implementierungsstand

#### Status-Übersicht

- [x] **Projekt-Setup & Multi-Module-Architektur**
  - Root Gradle Konfiguration: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`.
  - Modulaufteilung: `:core:model`, `:core:database`, `:core:sync`, `:core:healthconnect`, `:mobile`, `:wear`.
- [x] **Dokumentation**
  - `README.md` (Projektübersicht & Tech-Stack)
  - `USER_STORIES.md` (Epics 1–6 mit allen Akzeptanzkriterien)
  - `ARCHITECTURE.md` (Systemdesign, ERD, Wearable Data Layer Protokolle, Health Connect)
  - `AGENT.md` / `agents.md` (Entwickler- & Agent-Guidelines mit Testvorgaben & Changelog)
- [x] **Epic 1 & 2: Datenbasis & Modelle (`:core:model`, `:core:database`)**
  - Domain-Klassen: `Machine`, `WorkoutTemplate`, `TemplateMachineCrossRef`, `WorkoutSession`, `SessionMachineInstance`, `WorkoutSet`, `SyncQueueItem`, `SetType`, `SyncStatus`, `QueueStatus`.
  - Room Entities mit Cascade Delete, Indexen und Mappern (`MachineEntity`, `WorkoutTemplateEntity`, `TemplateMachineCrossRefEntity`, `WorkoutSessionEntity`, `SessionMachineInstanceEntity`, `WorkoutSetEntity`, `SyncQueueEntity`).
  - DAOs: `MachineDao`, `WorkoutTemplateDao`, `WorkoutSessionDao`, `SyncQueueDao`.
  - Relationen: `WorkoutTemplateWithMachines`, `WorkoutSessionWithDetails`.
  - `Converters` für Room Enums und `LockerLiftDatabase` Builder.
- [x] **Epic 5: Wearable Data Layer Synchronisation (`:core:sync`)**
  - `SyncConstants` (Pfade für DataClient, ChannelClient, MessageClient).
  - DTOs: `WorkoutSessionPayload`, `SessionMachineInstancePayload`.
  - `SyncPayloadSerializer` (Kotlinx Serialization JSON mit Typ-Sicherheit).
  - `WearableDataLayerManager` (Streaming via `ChannelClient`, Master-Data per `DataClient`, ACK per `MessageClient`).
  - `SyncQueueWorker` (WorkManager Task für Hintergrundübertragung bei Reconnect).
- [x] **Epic 6: Health Connect Integration (`:core:healthconnect`)**
  - `HealthConnectManager` mit SDK-Status & Berechtigungsprüfung (`WRITE_EXERCISE`, etc.).
  - `ExerciseRecordBuilder` (`EXERCISE_TYPE_STRENGTH_TRAINING`).
- [x] **Epic 1 & 2 & 4: Mobile App (`:mobile`)**
  - `MainActivity` mit Bottom-Navigation.
  - `CatalogScreen` (Maschinenkatalog anlegen, Validierung gegen Namensduplikate).
  - `TemplateListScreen` ($n$-Vorlagen anlegen, Cold-Start Vorlagen).
  - `HistoryScreen` (Vergangene Einheiten, Satzübersicht).
  - `MobileDataLayerListenerService` (Empfang via `ChannelClient`, Room-Insert, Health Connect Trigger & ACK).
- [x] **Epic 3 & 4: Standalone Wear OS Tracking (`:wear`)**
  - `WorkoutForegroundService` mit Ongoing Activity Notification & WakeLock.
  - `MainActivity` mit Vorlagenauswahl & Freies Training.
  - `ActiveWorkoutScreen` (Übungsliste, Ad-hoc Station hinzufügen, Überspringen, Template-Konsolidierungs-Logik, Queue-Speicherung).
  - `RepsWeightInputScreen` (Rotary Input Unterstützung, Quick-Buttons, Double Progression Highlight).
  - `RestTimerScreen` (Pausentimer mit haptischer Vibration).
  - `WearDataLayerListenerService` (Stammdaten-Sync und Quittierungs-Handling).
- [x] **Unit Testing Suite (`:core:model`, `:core:sync`, `:core:database`)**
  - `DomainModelTest.kt`: Tests für Instanziierung, UUIDs, Defaults und JSON-Serialisierung.
  - `SyncPayloadSerializerTest.kt`: Tests für verlustfreie Enkodierung/Dekodierung komplexer Workout-Payloads.
  - `EntityMappingTest.kt`: Tests für bidirektionale Mappings zwischen Domain-Modellen und Room-Entities sowie Type-Converters.
- [x] **Agent Skills (`.agents/skills/`)**
  - `git-commit-guidelines`: Skill zur Durchsetzung von Conventional Commits, Scope-Validierung und automatischem Testing-Mandat.

---

## 4. Unveränderliche Architektur-Regeln (Invariants)

1. **UUIDs als Primärschlüssel:**
   * Verwende für alle Entitäten stets `java.util.UUID.randomUUID().toString()` als Primärschlüssel.
   * **Niemals** `autoGenerate = true` bei Room-IDs verwenden! Verteilte Offline-Datenbanken erfordern kollisionsfreie Schlüssel.
2. **Entkopplung von Vorlage (Template) und Training (Session):**
   * Eine `WorkoutSession` kopiert die Maschinen des Templates in `SessionMachineInstance`-Einträge.
   * Das Ändern, Hinzufügen oder Überspringen von Maschinen während des Trainings verändert das zugrundeliegende Template **nicht** automatisch.
   * Erst bei Trainingsende wird der Nutzer explizit gefragt, ob die Änderungen in das Template übernommen werden sollen (Template-Konsolidierung).
3. **Data Layer API Kanaltrennung:**
   * **`DataClient`**: Für Stammdaten (Maschinen, Vorlagen).
   * **`ChannelClient`**: Für komplette Workout-Sitzungen (Payload JSON via Stream).
   * **`MessageClient`**: Für Bestätigungspakete (ACK) und Steuerungsbefehle.
4. **Health Connect gehört ausschließlich aufs Smartphone:**
   * Die Wear OS App schreibt **nicht** direkt in Health Connect.
   * Die Wear OS App liefert das aggregierte Workout per Sync an das Phone, welches den `ExerciseSessionRecord` in Health Connect persistiert.
5. **Foreground Service für Wear OS Tracking:**
   * Ein aktives Workout auf der Uhr muss in einem Android `Foreground Service` laufen, der eine `Ongoing Activity` Notification registriert. Dadurch wird verhindert, dass das Betriebssystem die App im Ambient Mode oder bei Speicherdruck beendet.

---

## 5. Modulorganisation & Paketstruktur

Das Root-Paket lautet: `com.lockerlift`

| Modul | Gradle Pfad | Zweck | Erlaubte Abhängigkeiten |
|---|---|---|---|
| Domain-Modelle | `:core:model` | Reine Kotlin-Klassen, Enums, Value Objects | **Keine** Android-Framework-Libs |
| Datenbank | `:core:database` | Room DB, Entities, DAOs, SQLCipher | `:core:model`, Room, Coroutines |
| Synchronisation | `:core:sync` | Wearable Data Layer Manager, Serialization | `:core:model`, `:core:database`, Play Services Wearable |
| Health Connect | `:core:healthconnect` | Health Connect Client, Record Builder | `:core:model`, AndroidX Health Connect |
| Mobile App | `:mobile` | Smartphone Compose UI, Tracking, Listener Service | Alle `:core:*` Module |
| Wear OS App | `:wear` | Compose for Wear OS, Horologist, Rotary, Service | `:core:model`, `:core:database`, `:core:sync` |

> [!IMPORTANT]
> Beachte strikt die Modulgrenzen! `:core:model` darf keine Android-Imports enthalten. `:wear` darf nicht `:core:healthconnect` referenzieren.

---

## 6. Code-Konventionen & Best Practices

### 6.1 Kotlin & Coroutines
* **Null-Safety:** Bevorzuge nicht-nullable Typen. Vermeide `!!` ausnahmslos.
* **Coroutines Dispatcher:**
  * UI / ViewModels: `viewModelScope.launch` auf `Dispatchers.Main`.
  * Datenbank & IO: Alle DAO-Funktionen sind `suspend` oder liefern `Flow<T>`. Room wechselt intern automatisch auf einen Hintergrund-Dispatcher.
  * Schwere Berechnungen / Parsing: `withContext(Dispatchers.Default)`.
* **State Management:**
  * ViewModels exponieren `StateFlow<UiState>` als `asStateFlow()`.
  * Einmalige Ereignisse (Navigation, Snackbars, Haptik) werden über `SharedFlow` oder `Channel` gehandhabt.

### 6.2 Jetpack Compose & Wear OS Compose
* **State Hoisting:** UI-Komponenten sind möglichst zustandslos (*stateless*).
* **Wear OS Horologist:**
  * Nutze für scrollbare Listen auf der Uhr Horologist `ScalingLazyColumn` mit passendem Content Padding für runde Displays.
  * Unterstütze Rotary Input (drehbare Lünette) für Gewicht- und Wiederholungseingaben.
* **Haptik:**
  * Nutze `LocalHapticFeedback.current` für Klicks bei Rotary Steps und den Abschluss von Sätzen bzw. Pausentimer-Ablauf.

### 6.3 Room Database
* Alle Foreign Keys definieren ein klares Löschverhalten (`onDelete = ForeignKey.CASCADE` für untergeordnete Elemente wie Sets).
* Erstelle Indizes für alle Fremdschlüssel und häufig abgefragte Spalten (z. B. `machine_id`, `session_id`).
* Komplexe Schreibvorgänge (z. B. Workout-Abschluss, Sync-Import) müssen in `@Transaction`-Methoden gekapselt werden.

---

## 7. Git- & Commit-Richtlinien (Commit Message Guidelines)

Alle Commits müssen dem **Conventional Commits**-Standard (v1.0.0) folgen. Dies stellt eine saubere Projekthistorie, automatische Changelog-Generierung und Nachvollziehbarkeit sicher.

### Format
```text
<type>(<scope>): <kurze Zusammenfassung im Imperativ/Präsens>

[Optionale detailliertere Beschreibung: Kontext, Problem, Lösung]

[Optional: Referenz auf Akzeptanzkriterien/User Stories, z. B. 'Closes US-3.1']
```

### 7.1 Erlaubte Typen (`type`)
* `feat`: Neues Feature für den Anwender (z. B. neuer Screen, Progressionslogik).
* `fix`: Bugfix oder Korrektur eines unerwünschten Verhaltens.
* `test`: Hinzufügen, Ergänzen oder Anpassen von Unit-Tests.
* `docs`: Reine Dokumentationsänderungen (`README.md`, `USER_STORIES.md`, `AGENT.md`, etc.).
* `refactor`: Code-Umbauten, die weder Funktionalität hinzufügen noch Fehler beheben.
* `chore`: Build-Konfiguration, Gradle-Updates, Versionsanpassungen, `.gitignore`.
* `perf`: Performance-Optimierungen.

### 7.2 Gültige Scopes (`scope`)
* `model`: Änderungen in `:core:model`
* `database`: Änderungen in `:core:database`
* `sync`: Änderungen in `:core:sync`
* `healthconnect`: Änderungen in `:core:healthconnect`
* `mobile`: Änderungen in `:mobile`
* `wear`: Änderungen in `:wear`
* `project`: Modulübergreifende Änderungen, Root-Setup oder Dokumentation

### 7.3 Verbindliche Commit-Regeln
1. **Imperativ / Präsens:** Schreibe `feat(wear): add rotary input support` statt `added rotary input`.
2. **Kein Punkt am Zeilenende:** Die erste Betreffzeile endet **nie** mit einem Punkt.
3. **Längenbegrenzung:** Die Betreffzeile sollte maximal 72 Zeichen lang sein.
4. **Atomic Commits:** Ein Commit behandelt genau ein in sich geschlossenes logisches Thema.
5. **Tests mit Feature committen:** Wenn ein neues Feature (`feat(...)`) hinzugefügt wird, **müssen** die zugehörigen Unit-Tests im selben Commit enthalten sein.

