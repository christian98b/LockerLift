# 41: Assistierte Maschinen – inverse Gewichtsprogression (Issue #35)

## Feature

Unterstützung für assistierte Maschinen (z. B. Assist-Dip, Assist-Chin/Pull-Up), bei denen
**weniger ausgewähltes Gewicht = mehr Unterstützung = leichtere Übung** bedeutet
(GitHub Issue #35). Der Fortschritt wird auf solchen Maschinen durch **abnehmendes**
Gewicht gemessen.

Pro Maschine ist die Progressionsrichtung konfigurierbar:
- `NORMAL`: klassische Maschinen – mehr Gewicht = mehr Fortschritt (Default).
- `INVERSE`: assistierte Maschinen – die Doppelte-Progression-Logik schlägt bei
  erreichtem Overload-Ziel nun **niedrigere** Gewichte vor (z. B. 40 kg → 20 kg → 0 kg).

Historische Daten bleiben unangetastet (additive Room-Migration mit Default `NORMAL`).
Das Flag wird beim Master-Data-Sync (Phone → Wear) mit übertragen, sodass die Uhr die
invertierte Vorschlagslogik ebenfalls verwendet.

## Umsetzung

- `:core:model`: Neues `WeightDirection`-Enum (NORMAL/INVERSE), serialisierbar;
  `Machine` erhält `weightDirection` (Default NORMAL). `WorkoutLogic.calculateNextWeight`
  erhält den Parameter `weightDirection` und subtrahiert für INVERSE die Schrittweite
  (geclampt bei 0 kg).
- `:core:database`: `MachineEntity.weight_direction` + Room-Migration 5→6
  (`ALTER TABLE ... DEFAULT 'NORMAL'`), `Converters` für `WeightDirection`.
- `:core:sync`: `SyncIngestionEngine` übernimmt `weightDirection` beim
  Master-Data-Sync.
- `:mobile`: Katalog-Dialoge (Anlegen/Bearbeiten) mit „Assisted machine“-Chip;
  `MobileTrackingScreen` übergibt die Richtung an die Progressionsvorschläge.
- `:wear`: `RepsWeightInputScreen` übergibt die Richtung an den
  Doppelte-Progression-Vorschlag; Maschinen-Schnellanlage auf der Uhr mit
  Assist-Chip.

## Dateien

- `core/model/src/main/kotlin/.../WeightDirection.kt` (neu)
- `core/model/src/main/kotlin/.../Machine.kt`
- `core/model/src/main/kotlin/.../WorkoutLogic.kt`
- `core/database/src/main/kotlin/.../entity/MachineEntity.kt`
- `core/database/src/main/kotlin/.../Converters.kt`
- `core/database/src/main/kotlin/.../LockerLiftDatabase.kt` (v6, `MIGRATION_5_6`)
- `core/sync/src/main/kotlin/.../SyncIngestionEngine.kt`
- `mobile/src/main/kotlin/.../catalog/CatalogViewModel.kt`, `CatalogScreen.kt`
- `mobile/src/main/kotlin/.../tracking/MobileTrackingScreen.kt`
- `wear/src/main/kotlin/.../logic/WearWorkoutLogic.kt`
- `wear/src/main/kotlin/.../presentation/RepsWeightInputScreen.kt`, `ActiveWorkoutScreen.kt`
- `wear/src/main/kotlin/.../tracking/ActiveWorkoutViewModel.kt`
- `mobile/src/main/res/values{,-de}/strings.xml`, `wear/src/main/res/values{,-de}/strings.xml`

## Unit-Tests

- `core/model/src/test/.../WorkoutLogicTest.kt`: `testInverseProgressionForAssistedMachines`
  (INVERSE: 80 kg → 77,5 kg; kein Fortschritt unverändert; NORMAL weiter +;
  Clamp bei 0 kg).
- `core/database/src/test/.../EntityMappingTest.kt`: `weightDirection`-Mapping
  Domain ↔ Entity.
- `wear/src/test/.../WearWorkoutLogicTest.kt`: INVERSE-Progression über den
  Wear-Wrapper.
