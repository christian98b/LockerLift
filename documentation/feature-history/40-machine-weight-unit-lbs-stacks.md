# 40: lbs-Gewichtsstapel: Einheit & Schrittweite pro Maschine (Issue #37)

## Feature

Unterst\u00fctzung f\u00fcr in Pfund (lbs) beschriftete Gewichtsstacks (GitHub Issue #37).
Pro Maschine ist die Skaleneinheit (kg/lbs) und eine individuelle Schrittweite
konfigurierbar. Eingaben erfolgen direkt in der Maschineneinheit; intern wird
ausnahmslos in kg gespeichert (1 lbs = 0.45359237 kg, exakt gerechnet, nur bei
der Anzeige gerundet). Historische Daten bleiben unangetastet (additive
Migration mit Default kg). Bewusst keine globale App-Einheiten-Umschaltung,
da das Problem per Maschine unterschiedlich ist (Mischstudios).

## Umsetzung

- `:core:model`: Neues `WeightUnit`-Enum (KG/LBS) und `WeightConversion` mit
  exaktem Umrechnungsfaktor; `Machine` erh\u00e4lt `weightUnit` (Default KG).
- `:core:database`: `MachineEntity.weight_unit` + Room-Migration 4\u21925
  (`ALTER TABLE ... DEFAULT 'KG'`), `Converters` f\u00fcr `WeightUnit`.
- `:core:sync`: `SyncIngestionEngine` \u00fcbernimmt `weightUnit` beim
  Master-Data-Sync (Wear erbt die Einheit vom Telefon).
- `:mobile`: Katalog-Dialoge (Anlegen/Bearbeiten) mit kg/lbs-Auswahl; die
  Schrittweite wird in der gew\u00e4hlten Einheit eingegeben und intern in kg
  gespeichert. Tracking- und Verlaufs-Screens zeigen Gewichte in der
  Maschineneinheit, Eingaben (Log/Set-Dialoge, Stepper) erfolgen direkt in
  Skalenwerten.
- `:wear`: `RepsWeightInputScreen` arbeitet in der Maschineneinheit (Skalenwerte
  direkt, +/- in kg-Schritten intern), Maschinenanlage auf der Uhr mit
  kg/lbs-Auswahl.

## Dateien

- `core/model/src/main/kotlin/.../WeightUnit.kt` (neu)
- `core/model/src/main/kotlin/.../Machine.kt`
- `core/database/src/main/kotlin/.../entity/MachineEntity.kt`
- `core/database/src/main/kotlin/.../Converters.kt`
- `core/database/src/main/kotlin/.../LockerLiftDatabase.kt` (v5, `MIGRATION_4_5`)
- `core/sync/src/main/kotlin/.../SyncIngestionEngine.kt`
- `mobile/src/main/kotlin/.../catalog/CatalogViewModel.kt`, `CatalogScreen.kt`
- `mobile/src/main/kotlin/.../tracking/MobileTrackingScreen.kt`
- `mobile/src/main/kotlin/.../history/HistoryScreen.kt`
- `wear/src/main/kotlin/.../presentation/RepsWeightInputScreen.kt`, `ActiveWorkoutScreen.kt`
- `wear/src/main/kotlin/.../tracking/ActiveWorkoutViewModel.kt`
- `mobile/src/main/res/values{,-de}/strings.xml`, `wear/src/main/res/values{,-de}/strings.xml`

## Unit-Tests

- `core/model/src/test/.../WeightConversionTest.kt` (neu): Faktor-Exaktheit,
  Round-Trip, Skalenwert-Formatierung (40 lbs \u2192 "40"), KG-Default der Machine.
- `core/database/src/test/.../EntityMappingTest.kt`: `weightUnit`-Mapping +
  `WeightUnit`-Converter (inkl. Fallback auf KG bei unbekannten Werten).
- `mobile/src/test/.../CatalogViewModelTest.kt`: lbs-Schrittweite wird in kg
  konvertiert (7 lbs \u2248 3.175 kg), kg bleibt unver\u00e4ndert.
