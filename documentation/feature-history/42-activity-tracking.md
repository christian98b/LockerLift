# 42 - Activity Tracking: Log Other Sports (Issue #50)

## Feature

Beyond gym workouts, users can now log other sports activities (running, bouldering, etc.) so they are recorded as "done" and appear in the journal next to gym workouts.

Per the owner's comment on Issue #50, activity types are **user-customizable**: a user can create a new sport entry with an arbitrary number of self-defined fields (e.g. running with "km" and "time"). Running and Bouldering are seeded as built-in types (Running with `km` (number) and `time` (text) fields); built-ins cannot be deleted.

Minimal scope by design: only date + activity type (+ custom field values + notes). No route tracking, grades, or splits (follow-ups).

## Implementation

### `:core:model`
- New `core/model/src/main/kotlin/com/lockerlift/core/model/Activity.kt`:
  - `ActivityFieldType` (TEXT, NUMBER), `ActivityFieldDefinition` (id, name, type)
  - `ActivityType` (id, name, fields, isBuiltin, createdAt) + stable builtin IDs and JSON (de)serialization helpers for field definitions
  - `ActivityEntry` (id, activityTypeId, date, fieldValues map, notes, createdAt) + JSON (de)serialization helpers for values

### `:core:database`
- New tables via `MIGRATION_6_7` (DB version 6 → 7):
  - `activity_types` (UUID PK per architectural invariant 1)
  - `activity_entries` (FK → `activity_types.id`, `ON DELETE CASCADE`, indexed)
- New `ActivityTypeEntity` / `ActivityEntryEntity` with domain mappers (`entity/ActivityEntity.kt`)
- New DAOs: `ActivityTypeDao` (flow + name lookup, builtin-protected delete), `ActivityEntryDao` (flow with relation, entry CRUD, by-type query)
- New relation model `ActivityEntryWithType` (`model/ActivityRelations.kt`)
- `ActivityTypeSeeder` seeds the built-in types on app start (fresh installs never run the migration); the migration seeds them for existing installs

### `:core:healthconnect`
- New `ActivityRecordBuilder`: maps logged activities to `ExerciseSessionRecord` (RUNNING, ROCK_CLIMBING, HIKING, SWIMMING, BIKING, YOGA, OTHER_WORKOUT fallback) with the entry id as `clientRecordId` for synchronized deletion
- `HealthConnectManager.exportActivityEntry` / `deleteActivityEntry` follow the existing export/deletion pattern

### `:mobile`
- New `ui/activities/ActivitiesScreen.kt`: activity journal with log/edit/delete, Health Connect export & synchronized deletion
- New `ui/activities/ActivityDialogs.kt`: log dialog (type picker, custom fields, notes), edit dialog, and manage-types dialog (create custom types with user-defined fields; delete custom types)
- `MainActivity`: new ACTIVITIES sub-tab between Sessions and Insights in the History tab
- Strings added to `values/strings.xml` and `values-de/strings.xml` (EN/DE)
- `LocalBackupManager`: backup schema version 2 now includes activity types & entries (optional fields with defaults; schema 1 backups still restore)

## Files

- Created: `core/model/src/main/kotlin/com/lockerlift/core/model/Activity.kt`, `core/database/src/main/kotlin/com/lockerlift/core/database/entity/ActivityEntity.kt`, `core/database/src/main/kotlin/com/lockerlift/core/database/dao/ActivityTypeDao.kt`, `core/database/src/main/kotlin/com/lockerlift/core/database/dao/ActivityEntryDao.kt`, `core/database/src/main/kotlin/com/lockerlift/core/database/model/ActivityRelations.kt`, `core/database/src/main/kotlin/com/lockerlift/core/database/logic/ActivityTypeSeeder.kt`, `core/healthconnect/src/main/kotlin/com/lockerlift/core/healthconnect/ActivityRecordBuilder.kt`, `mobile/src/main/kotlin/com/lockerlift/mobile/ui/activities/ActivitiesScreen.kt`, `mobile/src/main/kotlin/com/lockerlift/mobile/ui/activities/ActivityDialogs.kt`
- Modified: `LockerLiftDatabase.kt` (v7 + migration), `HealthConnectManager.kt`, `LockerLiftMobileApp.kt`, `MainActivity.kt`, `values/strings.xml`, `values-de/strings.xml`

## Unit Tests

- `core/model/src/test/.../ActivityModelTest.kt`: field/value JSON round trips, invalid-JSON fallbacks, builtin ID stability
- `core/database/src/test/.../dao/ActivityDaoTest.kt`: type CRUD + name lookup + builtin delete protection, entry round trip + relation, date-desc ordering, cascade delete
- `core/database/src/test/.../dao/ActivityTypeSeederTest.kt`: seeds both built-ins with correct fields, idempotent
- `core/healthconnect/src/test/.../ActivityRecordBuilderTest.kt`: exercise type resolution (running, bouldering, fallback), record construction (times, type, title, notes, clientRecordId)

## Deferred (follow-ups)

- Route tracking, grades, splits
- Referencing activity types from the workout plan
- Wear OS-side activity logging & sync
