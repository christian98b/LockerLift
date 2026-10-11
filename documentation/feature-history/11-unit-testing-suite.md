# Unit Testing Suite (`:core:model`, `:core:sync`, `:core:database`, `:core:healthconnect`, `:mobile`, `:wear`)

  - `DomainModelTest.kt`: Tests for instantiation, UUIDs, defaults, and JSON serialization.
  - `SyncPayloadSerializerTest.kt`: Tests for lossless encoding/decoding of complex workout payloads, `WorkoutTemplatePayload`, and machine catalogs.
  - `EntityMappingTest.kt`: Tests for bidirectional mappings, type converters, `getLastCompletedSetsForMachine` query contract verification, and sync status updates.
  - `WorkoutTrackingLogicTest.kt`: Unit tests for double progression calculations, clamping, validation, and station operations.
  - `AesGcmHelperTest.kt`: Tests for AES-256-GCM encryption, decryption roundtrip, invalid key rejection, IV/ciphertext tampering detection, and key reconstruction.
  - `AesGcmHelperTest.kt`: Verifies that encryption retains the provider-generated 12-byte GCM IV for successful decryption.
  - `ExerciseRecordBuilderTest.kt`: Tests for `ExerciseSessionRecord`, `TotalCaloriesBurnedRecord`, and `HeartRateRecord` builders, boundary safeguards, dynamic system zone offset resolution, and custom zone offset propagation.
  - `MobileWorkoutTrackingTest.kt`: Unit tests for mobile session instances, set volume calculations, double progression triggers, template variation detection, and payload serialization.
  - `WearWorkoutLogicTest.kt`: Tests for double progression calculation, historical reference data extraction & formatting, machine replacement, station skip toggle, name validation, weight/reps prefilling, input boundary clamping (`clampWeight`, `clampReps`), and session instances initialization for template and free workouts.
