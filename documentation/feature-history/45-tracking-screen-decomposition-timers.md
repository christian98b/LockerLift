# 45 – MobileTrackingScreen Decomposition & Timer Logic in TrackingViewModel (Issue #57)

**Issue:** [Refactor: decompose MobileTrackingScreen and move timer logic into TrackingViewModel](https://github.com/christian98b/LockerLift/issues/57)

## Summary

The 1,344-line `MobileTrackingScreen.kt` was split into focused files under the tracking package, and the elapsed/rest timer state machines moved from `LaunchedEffect` composables into `TrackingViewModel`. Timer state now lives in `TrackingUiState`, survives configuration changes (AK 2.2), and is unit-testable (AK 2.6). `UpNextPlanCard` no longer reads the database inside a composable; it receives pre-collected plan/activity-type flows from the ViewModel (AK 2.4). No behavioral regression in the tracking flow (AK 2.7).

## Files Created

- `mobile/src/main/kotlin/com/lockerlift/mobile/tracking/components/StationCard.kt` – station card (header menu, progression banner, set table, add-set, next-station suggestion)
- `mobile/src/main/kotlin/com/lockerlift/mobile/tracking/components/StepperControls.kt` – `WeightStepperControls` and `RepsStepperControls`
- `mobile/src/main/kotlin/com/lockerlift/mobile/tracking/components/UpNextPlanCard.kt` – "Up next today" card, flow-based parameters
- `mobile/src/main/kotlin/com/lockerlift/mobile/tracking/dialogs/LogSetDialog.kt` – log-set dialog with prefill and progression proposal
- `mobile/src/main/kotlin/com/lockerlift/mobile/tracking/dialogs/EditSetDialog.kt` – edit/delete set dialog
- `mobile/src/main/kotlin/com/lockerlift/mobile/tracking/dialogs/SwapAndAddExerciseDialogs.kt` – swap exercise and add exercise dialogs
- `mobile/src/main/kotlin/com/lockerlift/mobile/tracking/dialogs/FinishAndDiscardDialogs.kt` – finish and discard confirmation dialogs

## Files Modified

- `mobile/src/main/kotlin/com/lockerlift/mobile/tracking/TrackingViewModel.kt` – timer logic (`startTimers`, `startRestTimer`, `addRestTimerSeconds`, `skipRestTimer`), timer fields in `TrackingUiState` with `deriveElapsedSeconds()`/`tickRestTimer()`, new `workoutPlans`/`activityTypes` state flows
- `mobile/src/main/kotlin/com/lockerlift/mobile/tracking/MobileTrackingScreen.kt` – reduced to the screen skeleton rendering ViewModel state and the extracted components (457 lines, AK 2.5)
- `mobile/src/test/kotlin/com/lockerlift/mobile/tracking/TrackingViewModelTest.kt` – added ViewModel-level timer tests
- `mobile/src/test/kotlin/com/lockerlift/mobile/tracking/TrackingTimerStateTest.kt` – new pure state-transition tests

## Unit Tests Added

- `TrackingTimerStateTest`: elapsed seconds derived from `startTimeMillis` (positive, zero when inactive, never negative), rest countdown tick/decrement, stop at zero, no-op when not running, combined elapsed+rest tick
- `TrackingViewModelTest` additions: `startRestTimer` while active, `addRestTimerSeconds` extends running countdown, `skipRestTimer` clears countdown, rest timer ignored when no workout is active

## Notes

- The wear `ActiveWorkoutScreen.kt` and `HistoryScreen.kt` splits are explicitly out of scope (issue marks them lower-priority follow-ups).
- All existing `TrackingViewModelTest` tests remain unmodified and pass alongside the new suites (CI verified).
