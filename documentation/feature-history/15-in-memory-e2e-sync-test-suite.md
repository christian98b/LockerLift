# In-Memory End-to-End Synchronization Test Suite with On-Demand CI Trigger (`:core:sync`, `:core:database`, `:mobile`, `:wear`, `.github`)

  - `SyncIngestionEngine` (`:core:sync`): Extracted shared, single-source-of-truth ingestion engine for master equipment catalog (`ingestEquipmentCatalog`), master workout templates (`ingestWorkoutTemplates`), bidirectional workout session ingestion (`ingestWorkoutPayload`), ACK handling (`handleWorkoutAck`), and deletion propagation (`handleWorkoutDelete`). Eliminates code duplication between `MobileDataLayerListenerService` and `WearDataLayerListenerService`.
  - `LockerLiftDatabase` (`:core:database`): Added `createInMemory(context)` companion helper for fast, headless in-memory Room SQLite database creation without SQLCipher native library requirements.
  - `MobileDataLayerListenerService` (`:mobile`) & `WearDataLayerListenerService` (`:wear`): Refactored message handling, master data ingestion, and session processing to delegate cleanly to `SyncIngestionEngine`.
  - `InMemoryE2eSyncTest` (`:core:sync`): 7 comprehensive Robolectric-based in-memory end-to-end integration tests:
    1. `testWorkoutSessionSync_fromWatchToPhone_persistsFullGraphAndAcknowledges`: Full session graph transfer, machine creation, set persistence, and ACK loopback.
    2. `testWorkoutSessionSync_fromPhoneToWatch_persistsFullGraphAndAcknowledges`: Multi-machine session sync from phone to watch with ACK return and sync queue purging.
    3. `testMasterDataSync_catalogAndTemplatesReplicatedToWatchPreservingOrder`: Catalog & template replication preserving exact exercise order.
    4. `testMachineReconciliation_whenWatchAndPhoneHaveSameMachineNameWithDifferentIds_preventsConstraintCrashAndRemaps`: Ad-hoc machine collision reconciliation remapping instances to local IDs, preventing SQLite `FOREIGN KEY` / unique constraint crashes.
    5. `testHistoricalDataParity_forDoubleProgressionAfterSync`: Verifies `getLastCompletedSetsForMachine` returns identical historical sets on both devices after sync.
    6. `testZombieWorkoutRejection_onMobileDoesNotResurrectDeletedSession`: Rejects resurrecting locally deleted sessions pending delete sync.
    7. `testWorkoutDeletionSync_removesSessionAndQueueAcrossDevices`: Deletion propagation purging session and queue items across devices.
  - On-Demand CI Execution & Selective Gradle Filtering: E2E sync suite is excluded by default from standard local builds and standard push/PR CI runs via Gradle `tasks.withType<Test>` filtering and JUnit `Assume.assumeTrue`.
  - GitHub Actions Workflow (`build-and-test.yml`): Added `workflow_dispatch` boolean input `run_e2e_sync` ("In-Memory E2E Synchronisations-Testsuite explizit ausführen") enabling manual on-demand execution via `-PrunE2eSync=true`.
