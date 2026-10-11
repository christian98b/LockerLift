# Epic 5: Wearable Data Layer Synchronization (`:core:sync`)

  - `SyncConstants` (Paths for DataClient, ChannelClient, MessageClient).
  - DTOs: `WorkoutSessionPayload`, `SessionMachineInstancePayload`, `WorkoutTemplatePayload`.
  - `SyncPayloadSerializer` (Kotlinx Serialization JSON with type safety, session, template list, and machine catalog serializers).
  - `WearableDataLayerManager` (Streaming via `ChannelClient`, Master Data via `DataClient`, ACK via `MessageClient`).
  - Master data synchronization of templates and catalog from mobile to watch via `DataClient` (US 5.2, AK 5.2.1).
  - `SyncQueueWorker` (WorkManager task for background transfer upon reconnect).
