# Wear OS Always-In-Foreground & Ambient Screen Management (`:wear`)

  - `AndroidManifest.xml` (`:wear`): Removed empty `taskAffinity=""`, configured `android:launchMode="singleTask"`, `android:showWhenLocked="true"`, and `android:turnScreenOn="true"` on `MainActivity`.
  - `MainActivity.kt` (`:wear`): Added `setShowWhenLocked(true)` and `setTurnScreenOn(true)` on API 27+, with `FLAG_KEEP_SCREEN_ON` managed during active workout sessions so raising the wrist or tapping wakes directly back to the active workout without dropping back to the watch face.
  - `WorkoutForegroundService.kt` (`:wear`): Updated notification builder with `singleTop` intent flags, `NotificationCompat.CATEGORY_WORKOUT`, `NotificationCompat.VISIBILITY_PUBLIC`, and `NotificationCompat.PRIORITY_HIGH`.
