# 39: Machine & Template Deletion (Issue #40, Soft-Delete)

## Feature

Implemented deletion for machines and workout templates (GitHub Issue #40), including the owner's comment request for swipe-to-delete.

### Machines (Soft-Delete)
- Additive `is_deleted` flag on `MachineEntity` + Room migration 3→4; the unique index on `machines.name` was dropped so a soft-deleted machine no longer blocks its name for new machines.
- All catalog/selection DAO queries filter `is_deleted = 0`; history queries and `@Relation`-based session details remain unchanged, so historical workout sets stay fully readable.
- Swipe-to-delete in `CatalogScreen` (Material 3 `SwipeToDismissBox`, swipe left) with a confirmation dialog that explains the consequences (machine disappears from catalog and templates, history stays).
- Deleting removes the machine from all template cross-refs; it is blocked while the machine is used in the currently active workout (notice via snackbar).
- The confirmation dialog's alternative "only hide instead of delete" is covered by the soft-delete semantics itself: deleting only hides the machine from all lists — the dialog text states this explicitly.

### Templates (Soft-Delete)
- Additive `is_deleted` flag on `WorkoutTemplateEntity` (history sessions reference templates by id, so the row is retained as the issue's technical note suggests).
- Swipe-to-delete in `TemplateListScreen` with confirmation dialog; deleting removes the n:m cross-refs (machines and history unaffected, CASCADE rules were checked).
- Deleting is blocked with a notice while a workout based on that template is running (checked via the durable active session snapshot).

### Sync
- `SyncIngestionEngine.ingestEquipmentCatalog` now soft-deletes local machines missing from the incoming catalog (deletion propagates to Wear) and no longer resurrects locally deleted machines/templates from incoming sync payloads.

## Files

- `core/database/src/main/kotlin/.../entity/MachineEntity.kt` — `isDeleted` flag
- `core/database/src/main/kotlin/.../entity/WorkoutTemplateEntity.kt` — `isDeleted` flag
- `core/database/src/main/kotlin/.../dao/MachineDao.kt` — `is_deleted` filters, `softDeleteMachine`, `getMachineByNameIncludingDeleted`
- `core/database/src/main/kotlin/.../dao/WorkoutTemplateDao.kt` — `is_deleted` filters, `softDeleteTemplate`, `softDeleteTemplateWithCrossRefs`, `deleteCrossRefsByMachineId`, `getTemplateEntityById`
- `core/database/src/main/kotlin/.../dao/WorkoutSessionDao.kt` — `getActiveMachineInstanceByMachineId`
- `core/database/src/main/kotlin/.../LockerLiftDatabase.kt` — version 4, `MIGRATION_3_4`
- `core/database/src/main/kotlin/.../state/ActiveSessionStateRepository.kt` — `loadActiveTemplateId`
- `core/sync/src/main/kotlin/.../SyncIngestionEngine.kt` — deletion propagation + resurrection protection
- `mobile/src/main/kotlin/.../catalog/CatalogViewModel.kt` — `deleteMachine`, `MachineDeleteResult`
- `mobile/src/main/kotlin/.../catalog/CatalogScreen.kt` — swipe-to-delete, confirmation dialog, snackbars
- `mobile/src/main/kotlin/.../templates/TemplateListScreen.kt` — swipe-to-delete, confirmation dialog, active-workout block
- `mobile/src/main/res/values/strings.xml`, `values-de/strings.xml` — new strings (EN/DE)

## Unit Tests

- `core/database/src/test/.../dao/MachineDaoSoftDeleteTest.kt` — hides from catalog, keeps row, name reuse, sync protection query
- `core/database/src/test/.../dao/WorkoutTemplateDaoSoftDeleteTest.kt` — hides from lists, removes cross-refs, keeps machine row
- `mobile/src/test/.../catalog/CatalogViewModelDeleteTest.kt` — soft-delete keeps row, blocked during active workout
