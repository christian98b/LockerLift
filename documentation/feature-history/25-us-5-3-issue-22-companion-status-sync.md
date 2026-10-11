# US 5.3 / Issue #22: Companion Connection Status & Manual Sync Configuration (Mobile & Wear OS)

  - `CompanionDeviceStatus`, `CompanionNodeInfo`, `SyncResult`, `CompanionStatusResolver`, `SyncUtils` (`:core:sync`).
  - `WearableDataLayerManager`: companion status resolution (`getCompanionStatus`, `getWearCompanionStatus`, `getMobileCompanionStatus`), reachability inspection, `flushPendingQueue`, and `last_sync_timestamp` persistence in `SharedPreferences`.
  - `SyncQueueDao`: added `getPendingQueueCountFlow()` and `getPendingQueueCount()`.
  - `SyncQueueWorker`: updated to delegate queue flushing to `WearableDataLayerManager.flushPendingQueue` with `enqueue` helper.
  - `MobileDataLayerListenerService` & `WearDataLayerListenerService`: persistent `last_sync_timestamp` tracking on incoming data payloads, catalog updates, and ACK packets.
  - Mobile `SettingsScreen`: dedicated 'Wear OS Companion & Sync' section with live connection badge, last sync timestamp, pending queue counter, 'Sync Master Data Now' button with progress indicator, and 'Sync Workouts Now' button with feedback.
  - Wear OS `WearSettingsScreen`: dedicated configuration screen for round displays using `ScalingLazyColumn`, rotary scroll input, live smartphone connection badge, offline queue counter, 'Sync Now' button, visual and haptic feedback (`WearSettingsLogic`), last sync timestamp, app version display, and back button.
  - Wear OS `MainActivity`: navigation state and entry point button in `TemplateSelectionScreen`.
  - Localization: full English (`values/strings.xml`) and German (`values-de/strings.xml`) strings across `:mobile` and `:wear`.
  - Unit Tests: `CompanionStatusResolverTest.kt` (node state resolution, fallback behavior, timestamp formatting, `SyncResult` contracts), `WearSettingsLogicTest.kt` (formatting for connection and queue status, fallbacks), and `EntityMappingTest.kt` (`SyncQueueDao` pending count query contract).
