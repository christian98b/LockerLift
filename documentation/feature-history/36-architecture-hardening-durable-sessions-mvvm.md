# 36 – Architecture Hardening: Durable Sessions, MVVM & Module Boundary Fix

## Feature / User Story

Architecture remediation following a full architecture review of the codebase. No GitHub issue existed; the review identified three structural problems that this effort fixes:

1. **Critical: volatile workout state.** The entire active workout (session ID, start time, stations, every logged set) lived in `remember` composables on both the phone and the watch. The only DB write happened at completion. An OS-initiated process kill in ambient mode — exactly the situation AGENTS.md invariant 5 (foreground service) exists to prevent — silently destroyed the whole workout. This directly violated the Locker Scenario mandate that every CRUD action must complete locally and durably on the watch.
2. **No presentation layer.** AGENTS.md section 7.1 mandates ViewModels exposing `StateFlow`, but no ViewModel existed anywhere; 88 DAO references were made directly from composables, making the 1,365-line `MobileTrackingScreen` and 1,172-line `ActiveWorkoutScreen` untestable by the project's own unit testing mandate.
3. **Business logic in `:core:database`.** `WorkoutTrackingLogic` (357 lines of progression, clamping and session-mutation rules) lived in the Room module, contradicting the AGENTS.md section 6 module table.

## What Was Implemented

### Phase 1 – Durable active sessions (data-loss fix)
- New `ActiveSessionSnapshot` + `InstanceSets` domain models in `:core:model` with a codec and kotlinx.serialization round-trip.
- New `active_session_state` singleton Room table (database v3, proper `Migration(2,3)`) with `ActiveSessionStateDao` and `ActiveSessionStateRepository`. The snapshot is written **after every mutation** (set logged/edited/deleted, station added/replaced/skipped/noted, pause/resume) and loaded on startup for crash recovery.
- Both tracking ViewModels restore an interrupted session automatically; the wear `MainActivity` deep-links into the interrupted workout on launch instead of the template picker.
- The snapshot JSON codec encodes default fields (`encodeDefaults = true`) so logged-set timestamps survive the round-trip.

### Phase 2 – MVVM presentation layer
- `ActiveWorkoutViewModel` (wear): owns the whole active session; `finishWorkout`/`consolidateTemplateAndFinish` moved out of the composable (full session write, sync queue insert, worker enqueue, immediate flush, service stop).
- `TrackingViewModel` (mobile): start/recover, all set/station mutations, template consolidation, Health Connect export, Store-and-Forward sync and discard.
- `CatalogViewModel` (mobile): machines flow, add/update validation with callback-reported results, catalog push to the watch.
- `WearSettingsViewModel` (wear): queue count, companion status, last sync as `StateFlow`; manual sync reports a sealed `SyncOutcome`; strings and haptics remain in the composable.
- `ActiveWorkoutScreen` (1172 → 949 lines) and `MobileTrackingScreen` are now renderers over ViewModel state; no DAO, `WorkManager`, or persistence coroutine remains in either composable.

### Phase 3 – Module boundary fix
- Pure domain logic moved to `WorkoutLogic` in `:core:model` (progression, clamping, set bookkeeping, instance management, `CreateMachineMode`, `ValidationResult`).
- `WorkoutTrackingLogic` in `:core:database` reduced to a thin entity→domain adapter for DAO query results.
- `WearWorkoutLogic` delegates to `WorkoutLogic`; all call sites and tests updated.

## Files Created / Modified

**Created:** `WorkoutLogic.kt`, `ActiveSessionSnapshot.kt` (core:model); `ActiveSessionStateEntity.kt`, `ActiveSessionStateDao.kt`, `ActiveSessionStateRepository.kt` (core:database); `ActiveWorkoutViewModel.kt`, `WearSettingsViewModel.kt` (wear); `TrackingViewModel.kt`, `CatalogViewModel.kt` (mobile).

**Modified:** `LockerLiftDatabase.kt` (v3 + migration), `WorkoutTrackingLogic.kt`, `WearWorkoutLogic.kt`, `ActiveWorkoutScreen.kt`, `WearSettingsScreen.kt`, wear `MainActivity.kt`, `MobileTrackingScreen.kt`, `CatalogScreen.kt`, `HistoryScreen.kt`, both app `build.gradle.kts` (+ Robolectric/test/coroutines-test deps), `gradle/libs.versions.toml`, `ARCHITECTURE.md` (data-flow diagram corrected).

## Unit Tests Added

- `ActiveSessionSnapshotTest` (core:model): serialization round-trip, defaults, codec mapping.
- `ActiveSessionStateRepositoryTest` (core:database): save/load/overwrite/clear, corrupted-payload fail-soft.
- `WorkoutLogicTest` (core:model, moved from core:database) + new adapter seam tests in `WorkoutTrackingLogicTest` (core:database).
- `ActiveWorkoutViewModelTest` (wear, Robolectric + in-memory Room): start from template, free workout, restore-with-sets, durability of set/delete/skip/pause mutations, append persistence, resumed-session identity.
- `TrackingViewModelTest` (mobile): start with settings notes, restore, set mutation durability, reorder/swap persistence, discard clearing, finish writing the full session graph plus sync queue entry.
- `CatalogViewModelTest` (mobile): validation, persistence, case-insensitive duplicate detection, self-rename, flow emission.

## Known Follow-ups (explicitly out of scope here)

- `HistoryScreen`, `InsightsScreen`, `TemplateListScreen` and `SettingsScreen` (mobile) still access DAOs directly from composables; they are read-mostly or have their own dedicated flows and should receive the same ViewModel treatment in a follow-up pass.
- Mobile navigation still uses boolean flags + `when` in `MainActivity` instead of a typed destination model.
