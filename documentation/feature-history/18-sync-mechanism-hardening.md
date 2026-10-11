# Sync Mechanism Hardening & Defect Remediation (`:core:sync`, `:core:database`, `:mobile`, `:wear`)

  - Stale `IN_TRANSIT` Queue Recovery: Added `resetStaleInTransitItems` in `SyncQueueDao` and automated 60s stale item reset in `flushPendingQueue`, preventing items from becoming permanently stalled when ACKs are lost.
  - WorkManager Retries & Error Result Mapping: Updated `flushPendingQueue` to return `SyncResult.Error` when transfers fail, allowing `SyncQueueWorker` to retry with exponential backoff rather than silently succeeding.
  - Mutual Exclusion & Concurrency Control: Added `globalFlushMutex` in `WearableDataLayerManager` and `enqueueUniqueWork(KEEP)` in `SyncQueueWorker` to prevent parallel execution collisions across workers, reconnect events, and UI triggers.
  - Safe Ad-Hoc Machine Reconciliation: Implemented case-insensitive machine name matching in `MobileDataLayerListenerService` and `WearDataLayerListenerService`, remapping instance references to existing IDs to prevent SQLite `FOREIGN KEY ... RESTRICT` constraint crashes.
  - Master Data Sequential Processing & Template Pruning: Ordered `onDataChanged` in `WearDataLayerListenerService` to process catalog updates before workout templates, avoiding foreign key constraint crashes on template cross-references, and archiving templates no longer active on phone.
  - Pull-to-Refresh Ingestion Synchronization: Added `activeIngestionMutex` in `MobileDataLayerListenerService` ensuring `SyncEventBus.notifyFlushCompleted` is emitted only after full database transactions have committed.
  - Wear OS Session Sync Status Fix: Added `database.workoutSessionDao().updateSyncStatus(sessionId, SyncStatus.SYNCED)` upon receiving `PATH_WORKOUT_ACK` on watch.
  - Phone Peer Reconnect Handler: Overrode `onPeerConnected` in `MobileDataLayerListenerService` to immediately flush phone-side sync queues when the watch reconnects.
  - Zombie Workout Prevention: Guarded incoming workout ingestion on phone against sessions marked with `ACTION_DELETE` in `sync_queue`, re-dispatching delete messages instead of re-inserting deleted workouts.
  - Platform-Aware Capability Targeting: Configured `SyncQueueWorker` to automatically target `CAPABILITY_MOBILE` on watches and `CAPABILITY_WEAR` on phones based on `FEATURE_WATCH`.
  - Unit Tests: Added `SyncMechanismTest.kt` in `:core:sync` (testing node capability resolution, result mappings, byte calculations, stale item recovery, and machine deduplication) and added `testSyncQueueResetStaleInTransitQueryContract` in `EntityMappingTest.kt`.
