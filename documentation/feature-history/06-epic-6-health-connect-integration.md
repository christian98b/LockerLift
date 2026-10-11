# Epic 6: Health Connect Integration (`:core:healthconnect`)

  - `HealthConnectManager` with SDK status, granular permission checking (`WRITE_EXERCISE`, `WRITE_TOTAL_CALORIES_BURNED`, `WRITE_HEART_RATE`), and silent fallback on denied telemetry.
  - `ExerciseRecordBuilder` (`EXERCISE_TYPE_STRENGTH_TRAINING`, `TotalCaloriesBurnedRecord`, `HeartRateRecord` with sample bounds safeguards).
