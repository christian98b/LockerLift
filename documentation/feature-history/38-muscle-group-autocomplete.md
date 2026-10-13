# 38 – Muskelgruppen-Vorschläge im Dropdown beim Anlegen einer neuen Maschine (Autocomplete)

## Feature / User Story

Implementierung von GitHub Issue #36: Beim Anlegen bzw. Bearbeiten einer Maschine werden die im
lokalen Katalog bereits genutzten Muskelgruppen als Vorschläge angezeigt, damit der Nutzer
auswählen kann, statt jede Muskelgruppe immer wieder neu freitextuell einzutippen.

- **Mobile (`:mobile`)**: Das Freitextfeld „Zielmuskelgruppe" im Add- und Edit-Maschinen-Dialog
  zeigt bei Fokus ein Autocomplete-Dropdown. Die Vorschläge filtern live anhand des aktuellen
  Eingabetexts (case-insensitiver Substring-Match). Freitext-Eingabe einer neuen, unbekannten
  Muskelgruppe bleibt uneingeschränkt möglich; mit Auswahl einer bestehenden Bezeichnung wird
  exakt diese übernommen, wodurch Duplikate ("Brust" vs. "Brustmuskel") verhindert werden.
  Bei keinem Treffer erscheint ein Hinweis, dass eine neue Muskelgruppe durch Weitertippen
  angelegt wird.
- **Wear OS (`:wear`)**: Der Create-Machine-Subscreen zeigt die bekannten Muskelgruppen als
  einfach tippbare Chips (vereinfachte Auswahl-UI statt Textfeld-Dropdown). Eine Auswahl ist
  optional; ohne Auswahl gilt weiterhin der bisherige Default „Full Body".

## Ansatz

- Vorschlagsquelle ist eine neue Room-DAO-Query: `SELECT DISTINCT target_muscle_group FROM machines`
  als Flow (`MachineDao.getAllTargetMuscleGroupsFlow`) — kein Raw-SQL, kein Schema-Update nötig.
- Das `CatalogViewModel` stellt die distinct Muskelgruppen als `StateFlow<List<String>>`
  bereit; die reine Filterfunktion `filterMuscleGroupSuggestions` ist static/companion-object
  und damit direkt unit-testbar.
- Auf der Watch wird die Liste aus dem bereits geladenen Maschinen-Katalog abgeleitet
  (`catalogMachines.map { it.targetMuscleGroup }.distinct()`), keine zusätzliche Query nötig.

## Dateien erstellt / geändert

### erstellt

- `core/database/src/test/kotlin/com/lockerlift/core/database/dao/MachineDaoMuscleGroupTest.kt` —
  Unit-Tests für die distinct Muskelgruppen-Query (distinct, Sortierung, leerer Katalog).
- `documentation/feature-history/38-muscle-group-autocomplete.md` — diese Dokumentation.

### geändert

- `core/database/src/main/kotlin/com/lockerlift/core/database/dao/MachineDao.kt` —
  neue Query `getAllTargetMuscleGroupsFlow(): Flow<List<String>>`.
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/catalog/CatalogViewModel.kt` —
  `knownMuscleGroups` StateFlow + `filterMuscleGroupSuggestions` Filterlogik.
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/catalog/CatalogScreen.kt` —
  neue Composable `MuscleGroupAutocompleteField` (OutlinedTextField + Dropdown-Overlay),
  eingesetzt im Add- und Edit-Machine-Dialog.
- `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-de/strings.xml` —
  neuer String `muscle_group_no_suggestions` (en/de).
- `mobile/src/test/kotlin/com/lockerlift/mobile/ui/catalog/CatalogViewModelTest.kt` —
  Unit-Tests für den `knownMuscleGroups`-Flow und die Filterfunktion.
- `wear/src/main/kotlin/com/lockerlift/wear/presentation/ActiveWorkoutScreen.kt` —
  Muskelgruppen-Chips im Create-Machine-Subscreen; ausgewählte Gruppe wird statt des
  „Full Body"-Defaults gespeichert.
- `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-de/strings.xml` —
  neuer String `hint_muscle_group` (en/de).

## Unit-Tests

- `MachineDaoMuscleGroupTest` (neu): distinct + Sortierung der Muskelgruppen-Query,
  Verhalten bei leerem Katalog.
- `CatalogViewModelTest` (erweitert): `knownMuscleGroups`-Flow liefert distinct,
  bereits genutzte Gruppen; `filterMuscleGroupSuggestions` testet case-insensitives
  Substring-Matching, leere Query (alle Vorschläge) und Nicht-Treffer.
