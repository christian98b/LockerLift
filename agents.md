# LockerLift – AI Agent & Developer Guidelines (`agents.md`)

Dieses Dokument ist identisch zu [AGENT.md](AGENT.md) und dient als Referenz und Fortschrittsprotokoll für Entwickler und Coding-Agents.

---

## 1. Verbindliche Test-Vorgaben (Strict Unit Testing Mandate)

> [!IMPORTANT]
> **Unit Tests sind für jede Implementierung Pflicht:**
> 1. **100% Testabdeckung für Logik:** Jede neue Funktion, Geschäftslogik, Berechnung (z. B. Double Progression, Kadenz), jedes Datenmodell, Mapping, Konvertierung, Serialisierung und jeder DAO-Workflow **MUSS** durch automatisierte Unit-Tests abgedeckt werden.
> 2. **Bestehende Tests müssen immer funktionieren:** Alle bestehenden Unit-Tests müssen bei zukünftigen Änderungen und Erweiterungen fehlerfrei durchlaufen (Regressionstest-Garantie).
> 3. **Ausnahme für Test-Änderungen:** Ein Unit-Test darf **ausschließlich dann** modifiziert oder entfernt werden, wenn das konkret getestete Feature bewusst gelöscht oder durch eine neue Spezifikation abgelöst wurde.
> 4. **Zukunftssicherheit:** Zukünftige Implementierungen müssen die vorhandenen Test-Suites aktiv nutzen und erweitern. Vor dem Abschluss einer Implementierung ist sicherzustellen, dass die Tests für das Modul grün sind.

---

## 2. Nachverfolgung von Implementierungen (Progress & Tracking)

> [!NOTE]
> **Pflicht für jeden Agenten:** Wenn neue Features implementiert, angepasst oder erweitert werden, **MUSS** dieser Abschnitt in `agents.md` und `AGENT.md` sofort aktualisiert werden. Dokumentiere:
> * Welches Feature / welche User Story umgesetzt wurde.
> * Welche Dateien erstellt oder modifiziert wurden.
> * Welche Unit-Tests hinzugefügt wurden.

### Aktueller Implementierungsstand

- [x] **Projekt-Setup & Multi-Module-Architektur** (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`)
- [x] **Dokumentation & Guidelines** (`README.md`, `ARCHITECTURE.md`, `AGENT.md`, `agents.md`, GitHub Issues #1–#15)
- [x] **Domain Models & Room Datenbank** (`:core:model`, `:core:database`)
- [x] **Wearable Data Layer Sync & Serialization** (`:core:sync`)
- [x] **Health Connect Client & Record Builder** (`:core:healthconnect`)
- [x] **Mobile App UI & Listener Service** (`:mobile`)
- [x] **Wear OS Standalone Tracking & Ongoing Activity Service** (`:wear`)
- [x] **Agent Skills** (`git-commit-guidelines`, `unit-testing-guidelines`, `feature-implementation-workflow`, `release-versioning-rules`)
- [x] **GitHub Actions CI/CD Pipeline** (`.github/workflows/build-and-test.yml` mit Test- & APK-Build-Stage)

---

## 3. Git- & Commit-Richtlinien (Commit Message Guidelines)

Alle Commits müssen dem **Conventional Commits**-Standard (v1.0.0) folgen:

### Format
```text
<type>(<scope>): <kurze Zusammenfassung im Imperativ/Präsens>

[Optionale detailliertere Beschreibung: Kontext, Problem, Lösung]

[Optional: Referenz auf Akzeptanzkriterien/User Stories, z. B. 'Closes US-3.1']
```

### 3.1 Erlaubte Typen (`type`)
* `feat`: Neues Feature für den Anwender (z. B. neuer Screen, Progressionslogik).
* `fix`: Bugfix oder Korrektur eines unerwünschten Verhaltens.
* `test`: Hinzufügen, Ergänzen oder Anpassen von Unit-Tests.
* `docs`: Reine Dokumentationsänderungen (`README.md`, `USER_STORIES.md`, `AGENT.md`, etc.).
* `refactor`: Code-Umbauten, die weder Funktionalität hinzufügen noch Fehler beheben.
* `chore`: Build-Konfiguration, Gradle-Updates, Versionsanpassungen, `.gitignore`.
* `perf`: Performance-Optimierungen.

### 3.2 Gültige Scopes (`scope`)
* `model`: Änderungen in `:core:model`
* `database`: Änderungen in `:core:database`
* `sync`: Änderungen in `:core:sync`
* `healthconnect`: Änderungen in `:core:healthconnect`
* `mobile`: Änderungen in `:mobile`
* `wear`: Änderungen in `:wear`
* `project`: Modulübergreifende Änderungen, Root-Setup oder Dokumentation

### 3.3 Verbindliche Commit-Regeln
1. **Imperativ / Präsens:** Schreibe `feat(wear): add rotary input support` statt `added rotary input`.
2. **Kein Punkt am Zeilenende:** Die erste Betreffzeile endet **nie** mit einem Punkt.
3. **Längenbegrenzung:** Die Betreffzeile sollte maximal 72 Zeichen lang sein.
4. **Atomic Commits:** Ein Commit behandelt genau ein in sich geschlossenes logisches Thema.
5. **Tests mit Feature committen:** Wenn ein neues Feature (`feat(...)`) hinzugefügt wird, **müssen** die zugehörigen Unit-Tests im selben Commit enthalten sein.

---

Für vollständige Details zu Modulstrukturen, Coding-Standards und Invarianten siehe [AGENT.md](AGENT.md).
