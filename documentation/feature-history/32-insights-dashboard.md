# 32 – Insights Dashboard: Analytics, Graphs & Configurable Widgets

Implements the user request for a configurable training analytics dashboard in the mobile app ("Auswertung / Dashboard").

## Feature / User Story

* New **Insights** tab (`MobileTab.INSIGHTS`) in the mobile bottom navigation.
* **Training verdict widget**: three-class traffic-light rating (TOO_LITTLE / ON_TRACK / DO_MORE) based on average sessions per week over the trailing 28 days.
* **Regularity widget**: 8-week bar strip showing the number of sessions per week.
* **Training calendar widget**: month grid (Monday-first) marking all days with completed workouts; free month navigation.
* **Volume progression widget**: line chart of total volume (kg) across the last 12 completed sessions (Canvas-drawn, no external chart library).
* **Exercise progression widget**: per-machine volume progression with machine filter chips, line chart, and trend indicator (improved / regressed, % change vs. first session, least-squares slope so single outliers cannot dominate).
* **Totals widget**: total sessions, total volume, total sets.
* **Configurable dashboard**: edit mode to reorder widgets (up/down) and show/hide them; order and visibility are persisted via SharedPreferences. The dashboard comes preconfigured with all widgets in a sensible default order.

## Architecture

* Analytics core lives in `:core:model` (`InsightsEngine`) as pure Kotlin with zero Android dependencies, fully unit-testable. Warm-up sets and skipped machine instances are excluded from all volume calculations; only completed sessions count.
* UI layer in `:mobile` maps Room relations (`WorkoutSessionWithDetails`) into engine snapshots via `InsightsMapper`.

## Files Created / Modified

* `core/model/src/main/kotlin/com/lockerlift/core/model/InsightsEngine.kt` (new)
* `core/model/src/test/kotlin/com/lockerlift/core/model/InsightsEngineTest.kt` (new)
* `mobile/src/main/kotlin/com/lockerlift/mobile/ui/insights/DashboardWidgetType.kt` (new)
* `mobile/src/main/kotlin/com/lockerlift/mobile/ui/insights/DashboardConfigStore.kt` (new)
* `mobile/src/main/kotlin/com/lockerlift/mobile/ui/insights/InsightsMapper.kt` (new)
* `mobile/src/main/kotlin/com/lockerlift/mobile/ui/insights/InsightsScreen.kt` (new)
* `mobile/src/test/kotlin/com/lockerlift/mobile/InsightsDashboardTest.kt` (new)
* `mobile/src/main/kotlin/com/lockerlift/mobile/MainActivity.kt` (modified: new Insights tab)
* `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-de/strings.xml` (modified: full EN/DE localization)

## Unit Tests Added

* `InsightsEngineTest` (16 tests): training-day grouping, calendar month building, regularity verdicts for all three classes, average sessions per week, exercise progression improvement/regression detection, skipped-instance and unknown-machine handling, least-squares trend behavior (null for <2 points, outlier resistance), overall volume progression, personal records, machine deduplication.
* `InsightsDashboardTest` (6 tests): default widget order, serialization round-trip, fallback to defaults on incomplete/garbage persisted data, hidden-widget parsing with unknown entries and empty string.
