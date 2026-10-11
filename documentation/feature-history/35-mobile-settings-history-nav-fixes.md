# 35 – Mobile Navigation Fixes: Settings Trap & History Sub-Tab Bar

Fixes two navigation regressions introduced with the four-tab bottom navigation refactor (PR #28) that shipped in v1.9.0.

## Problem

1. **Settings trap:** once the settings screen was opened, it could not be left. `showSettings` was evaluated first in the tab `when` block, but no bottom navigation item ever reset the flag — tapping Catalog/Templates/History/Tracking kept the settings screen on top, and the gear button hid itself. `SettingsScreen` has no back arrow, so there was no exit at all.
2. **History sub-tab bar unusable:** the Sessions/Insights `TabRow` rendered edge-to-edge *under the status bar* (tab labels at y=36–89 behind the status bar overlay on the emulator). Users only saw the half-width indicator line — looking like a stray progress bar at the top of the screen — and could not reach the Insights dashboard.

## Solution

* Every bottom navigation item now sets `showSettings = false` before switching tabs, so any tab tap leaves the settings screen.
* Added a `BackHandler` so the system back gesture/button also exits settings.
* The History content column now applies `windowInsetsPadding(WindowInsets.statusBars)` followed by `consumeWindowInsets(WindowInsets.statusBars)`: the sub-tab bar renders below the status bar, and the nested screen Scaffolds do not add a second status inset (no double spacing).

## Files Modified

* `mobile/src/main/kotlin/com/lockerlift/mobile/MainActivity.kt`

## Unit Tests

No new logic was added — the change is pure Compose navigation/inset layout. The mobile unit test suite runs green as regression check; both fixes were verified manually on the phone emulator (settings exit via bottom nav and system back; Insights dashboard reachable with the sub-tab bar fully visible below the status bar).

## Follow-Up

* The fixes are not part of the v1.9.0 release APKs; a patch release v1.9.1 should ship them once this PR is merged.
