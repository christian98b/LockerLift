# 43 - Weight Stack Scale: korrekte Anzeige für lbs-Maschinen (Issue #48)

## Feature

Die Platten-Aufdrucke von lbs-Maschinen (z. B. Hammer Strength: 5 / 12 / 20 / 28 / 36) sind kumulative Pin-Positionen, keine Deltas. Delta-basierte Inkrement-Stepper bildeten das nie korrekt ab (Issue #48: „lbs-Raster mit kg-Beschriftung").

**Gewählter Ansatz (mit Owner abgestimmt, siehe Issue-Kommentar):** frei editierbare Gewichtsskala (Werteliste) pro Maschine. Der Nutzer trägt die aufgedruckten Werte ein (in der Maschineneinheit, z. B. lbs); die App übernimmt diese exakt für Steppers und Progressionsvorschlag. Ohne Skala bleibt das alte Inkrement-Verhalten unverändert. Bestehende Maschinen werden nicht migriert (Schema-only, Room v8).

## Implementation

### `:core:model`
- Neu `WeightStack.kt`: Parse/Format der Werteliste, Einheiten-Konvertierung (kg ↔ lbs), `nextScaleValue` (Position ±n), `snapToScale`, `isOnScale`, `nextProgressionWeight` (INVERSE = abwärts), JSON-Serializer für Room
- `Machine` um `weightStackValuesKg: List<Float>` erweitert (Default leer; serialisiert automatisch im Sync-Payload mit)

### `:core:database`
- `machines.weight_stack_values_kg TEXT` via `MIGRATION_7_8` (v7 → v8), NULL = keine Skala
- Entity-Mapper: Liste ↔ JSON, leer ↔ NULL

### `:mobile`
- Katalog: neues Feld „Gewichtsstapel-Skala" im Anlegen- und Bearbeiten-Dialog (mit Hinweistext, EN/DE); Werte werden beim Speichern in kg konvertiert
- Tracking: `WeightStepperControls` springt bei konfigurierter Skala auf die nächsten Pin-Positionen (±1/±2/±3, Anzeige = aufgedruckter Wert in Maschineneinheit); Progressionsvorschlag (Banner + Log-Set-Dialog) geht auf die nächste Skalenposition statt kg-glattend

### `:wear`
- `RepsWeightInputScreen`: ±-Buttons springen auf Nachbar-Positionen der Skala, Progressions-Card auf die nächste Position; ohne Skala unverändert
- Wear-seitiges Anlegen von Maschinen erhält keine Skala-Eingabe (mobile-only konfigurierbar; Feld hat Default)

## Files

- Created: `core/model/src/main/kotlin/com/lockerlift/core/model/WeightStack.kt`
- Modified: `Machine.kt`, `LockerLiftDatabase.kt` (v8 + MIGRATION_7_8), `MachineEntity.kt`, `CatalogScreen.kt`, `CatalogViewModel.kt`, `MobileTrackingScreen.kt`, `RepsWeightInputScreen.kt`, `values/strings.xml`, `values-de/strings.xml`

## Unit Tests

- `core/model/src/test/.../WeightStackTest.kt` (13 Tests): Parsing (Separatoren, invalide Tokens, Duplikate, Sortierung), lbs-Konvertierung, next up/down mit Steps, snap, isOnScale, Progression NORMAL/INVERSE, JSON-Roundtrip, Format
- `EntityMappingTest.kt`: neuer Test `testMachineEntityMapping_weightStackScale` (Roundtrip Liste ↔ JSON ↔ NULL)

## Hinweise

- Sync: `Machine` wird im Wear-Payload serialisiert; das neue Feld propagiert automatisch (abwärtskompatibel, da Default leer).
- Backup: serialisiert `Machine`-Domainobjekte; ebenfalls automatisch enthalten.
