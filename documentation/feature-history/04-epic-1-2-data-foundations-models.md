# Epic 1 & 2: Data Foundations & Models (`:core:model`, `:core:database`)

  - Domain classes: `Machine`, `WorkoutTemplate`, `TemplateMachineCrossRef`, `WorkoutSession`, `SessionMachineInstance`, `WorkoutSet`, `SyncQueueItem`, `SetType`, `SyncStatus`, `QueueStatus`.
  - Room Entities with Cascade Delete, indices, and mappers (`MachineEntity`, `WorkoutTemplateEntity`, `TemplateMachineCrossRefEntity`, `WorkoutSessionEntity`, `SessionMachineInstanceEntity`, `WorkoutSetEntity`, `SyncQueueEntity`).
  - DAOs: `MachineDao`, `WorkoutTemplateDao`, `WorkoutSessionDao`, `SyncQueueDao`.
  - Relations: `WorkoutTemplateWithMachines`, `WorkoutSessionWithDetails`.
  - `WorkoutSessionDao.getLastCompletedSetsForMachine`: historical sets query for double progression and past performance (US 4.1).
  - `WorkoutTemplateDao.getCrossRefsForTemplate` & `getMachinesForTemplateOrdered` for ordered template exercises (US 2.1).
  - `Converters` for Room enums and `LockerLiftDatabase` builder.
