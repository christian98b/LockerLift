# Bidirectional Workout History Synchronization (`:core:sync`, `:mobile`, `:wear`)

  - Symmetric workout session streaming: workouts tracked on either phone or watch stream via `ChannelClient` (`/workout_payload_transfer`) to the companion device.
  - Automatic ingestion into Room DB for both devices ensuring identical past performance logs (`getLastCompletedSetsForMachine`), prefilling, and double progression baseline.
