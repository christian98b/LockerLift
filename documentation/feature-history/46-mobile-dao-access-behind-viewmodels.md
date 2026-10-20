# 46 – Mobile UI Architecture: DAO Access behind ViewModels (Issue #56)

## Feature / User Story

**Issue #56 (Refactor):** All mobile screen data access flows through
screen-level ViewModels / repositories instead of direct DAO calls inside
`@Composable` functions, so that UI logic is testable, error handling is
centralized, and the app follows a consistent architecture.

Previously only `TrackingViewModel`, `CatalogViewModel` and
`WearSettingsViewModel` followed the state-holder pattern; six other screens
grabbed DAOs directly inside composables (~36 direct DAO touchpoints).

## Acceptance Criteria fulfilled

- **AK 1.1:** No `app.database.*Dao()` call remains inside any `@Composable`
  function in `:mobile`. The only remaining `app.database` usages in the UI
  layer are `database = app.database` ViewModel factory arguments and the
  master-data push in the pull-to-refresh coordinator bridge.
- **AK 1.2:** `HistoryScreen` obtains sessions, machines and sync queue state
  through `HistoryViewModel`; the cascade-deletion flow (tombstone → Room
  delete → sync queue cleanup → delete action queue → Wear flush → Health
  Connect cleanup) and the full-graph session edit flow live in the state
  holder.
- **AK 1.3:** `TemplateListScreen`, `WorkoutPlanScreen`, `SettingsScreen`,
  `InsightsScreen` and `ActivitiesScreen` each obtain data via a new
  screen-level ViewModel.
- **AK 1.4:** Behavior and localization are unchanged; only the persistence
  side effects were relocated.
- **AK 1.5:** Unit tests cover the deletion/reordering flows previously
  inlined in composables.

## Files created

- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/history/HistoryViewModel.kt`
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/templates/TemplateListViewModel.kt`
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/plan/WorkoutPlanViewModel.kt`
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/settings/SettingsViewModel.kt`
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/insights/InsightsViewModel.kt`
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/activities/ActivitiesViewModel.kt`

## Files modified

- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/history/HistoryScreen.kt`
  – sessions/machines via `HistoryViewModel`, delete/save delegated.
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/templates/TemplateListScreen.kt`
  – templates, catalog machines and the assigned-machine edit draft via
  `TemplateListViewModel`; create/save/delete delegated (delete reports
  `TemplateDeleteResult` incl. active-workout guard).
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/plan/WorkoutPlanScreen.kt`
  – plans/templates/activity types via `WorkoutPlanViewModel`; save/delete
  delegated. Signature changed to receive `LockerLiftMobileApp`.
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/settings/SettingsScreen.kt`
  – pending queue count, companion status, last sync timestamp, sync flags,
  manual master-data/workout sync and backup/restore via
  `SettingsViewModel` (result mapping via `WorkoutSyncUiResult`).
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/insights/InsightsScreen.kt`
  – pre-mapped session snapshots via `InsightsViewModel`.
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/activities/ActivitiesScreen.kt`
  – types/entries via `ActivitiesViewModel`; entry/type CRUD delegated with
  Health Connect callbacks.
- `mobile/src/main/kotlin/com/lockerlift/mobile/MainActivity.kt` – updated
  `WorkoutPlanScreen` / `ActivitiesScreen` call sites.

## Unit tests added

- `mobile/src/test/kotlin/com/lockerlift/mobile/ui/history/HistoryViewModelTest.kt`
  – deletion cascade (tombstone, session delete, queue cleanup + delete
    action) and session edit (queue item replacement, persistence).
- `mobile/src/test/kotlin/com/lockerlift/mobile/ui/templates/TemplateListViewModelTest.kt`
  – template creation, reordered save, delete blocked during active workout,
    soft delete with cross-ref cleanup, edit-order draft handling.
- `mobile/src/test/kotlin/com/lockerlift/mobile/ui/plan/WorkoutPlanViewModelTest.kt`
  – plan save with entry replacement and plan deletion.
- `mobile/src/test/kotlin/com/lockerlift/mobile/ui/settings/SettingsViewModelTest.kt`
  – pending queue exposure, workout sync result mapping, sync flag reset.
- `mobile/src/test/kotlin/com/lockerlift/mobile/ui/insights/InsightsViewModelTest.kt`
  – snapshot mapping from persisted sessions.
- `mobile/src/test/kotlin/com/lockerlift/mobile/ui/activities/ActivitiesViewModelTest.kt`
  – entry log/update/delete, type creation and type deletion with Health
    Connect delegation for every removed entry.

No schema changes: DAOs were only relocated behind state holders.
