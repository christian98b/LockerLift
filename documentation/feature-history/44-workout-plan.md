# 44 - Workout Plan: Weekly Training Schedule (Issue #51)

## Feature

Users can store a recurring training plan instead of picking workouts ad-hoc: a weekly rhythm defines which program is planned per weekday (gym template, activity type, or free-text label), over a configurable multi-week rotation that repeats in a cycle. The start screen surfaces the planned workout for the current day ("Up next").

## Implementation

### `:core:model`
- `WorkoutPlan.kt`: `WorkoutPlan` (cycle length, anchor start date), `PlanEntry` (week index + ISO day of week → templateId | activityTypeId | label), `WorkoutPlanLogic` (ISO day-of-week from epoch, week index within rotation incl. backward wrap, `entryFor` resolution)

### `:core:database`
- Room v9 (`MIGRATION_8_9`): `workout_plans` + `plan_entries` (FK plan CASCADE, unique index per plan/week/day)
- `WorkoutPlanDao`: flows with entries, CRUD, atomic `replacePlanEntries`
- `WorkoutPlanWithEntries` relation
- Template/activity references are intentionally soft (no FK) so plans survive later template/activity deletions

### `:mobile`
- New **Plan** tab (5th bottom tab, calendar icon): `WorkoutPlanScreen` with plan list, delete, and editor dialog
  - Editor: name, rotation length (1–8 weeks, ±stepper), per week 7 weekday rows to assign a program
  - Assignment picker: gym template, activity type, or custom label; clear option
- Start screen (tracking): new "Up next today" card resolving the first plan's entry for the current day
- Strings EN/DE

## Files
- Created: `core/model/.../WorkoutPlan.kt`, `core/database/.../entity/WorkoutPlanEntity.kt`, `core/database/.../dao/WorkoutPlanDao.kt`, `core/database/.../model/PlanRelations.kt`, `mobile/.../ui/plan/WorkoutPlanScreen.kt`
- Modified: `LockerLiftDatabase.kt` (v9 + migration), `MainActivity.kt` (Plan tab), `MobileTrackingScreen.kt` (Up next card), `strings.xml` (EN/DE)

## Unit Tests
- `core/model/.../WorkoutPlanTest.kt`: ISO day-of-week (epoch anchor), single/four-week cycles with repeat, backward wrap before start date, `entryFor` hit/miss
- `core/database/.../dao/WorkoutPlanDaoTest.kt`: relation loading, flow, atomic entry replacement, unique (plan/week/day) REPLACE semantics, cascade delete, entry delete, count

## Notes
- Local-first: everything in Room, no cloud (per issue)
- Model kept simple: week index + day of week → program (per issue)
- Wear-side plan view can be a follow-up
