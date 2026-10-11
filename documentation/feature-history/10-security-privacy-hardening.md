# Security & Privacy Hardening (Audit Remediation)

  - `AesGcmHelper`: Cryptographic utility for hardware-grade AES-256-GCM encryption, decryption, and secure random byte generation.
  - `DatabaseKeyManager`: AndroidKeyStore-backed master key lifecycle manager encrypting SQLCipher 256-bit passphrase at rest.
  - `LockerLiftDatabase`: Active SQLCipher encryption integration with `SupportOpenHelperFactory`, automatic migration of existing plaintext SQLite databases, and removal of destructive migration fallback (`fallbackToDestructiveMigration`).
  - OS Backup Hardening: Disabled unencrypted automatic backups (`android:allowBackup="false"`) with explicit XML data extraction and backup rules (`data_extraction_rules.xml`, `backup_rules.xml`) across `:mobile` and `:wear`.
  - Channel Stream Bounding: Enforced 5 MB maximum stream threshold in `MobileDataLayerListenerService` and `WearDataLayerListenerService` to prevent OOM/DoS attacks.
  - Node Capability Verification: Added capability declarations (`wear.xml`) for `lockerlift_mobile_app` and `lockerlift_wear_app`, and sender authorization checks in listener services.
  - Health Connect Standards: Added `ACTION_SHOW_PERMISSIONS_RATIONALE` and `VIEW_PERMISSION_USAGE` alias in mobile manifest, and dynamic local timezone offset calculation in `ExerciseRecordBuilder`.
  - Domain Input Bounds: Added weight clamping (`0.0f..1000.0f` kg) and rep clamping (`1..999`) in `WorkoutTrackingLogic`.
  - Release Optimization & Privacy: Added `proguard-rules.pro` stripping debug logging in release builds and retaining Room, SQLCipher (`net.zetetic`), and serialization classes.
  - Fixed AndroidKeyStore AES-GCM startup crash: encryption now uses the provider-generated IV required by hardware-backed keys; the IV is retained for decryption.
  - Fixed SQLCipher 4.17.0 `UnsatisfiedLinkError` startup crash: `LockerLiftDatabase.buildDatabase` now calls `System.loadLibrary("sqlcipher")` before any database operation, per the `sqlcipher-android` migration requirement (the 4.17 native binary registers JNI methods via `JNI_OnLoad`/`RegisterNatives`, so without explicit loading the first DB access throws `nativeOpen` `UnsatisfiedLinkError`). Affects both `:mobile` and `:wear` via shared `:core:database`.
  - Fixed `WorkoutForegroundService` `SecurityException` crash on Wear OS / Android 14+ (Issue #19): Added required `HIGH_SAMPLING_RATE_SENSORS`, `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`, and `BODY_SENSORS` permissions for `FOREGROUND_SERVICE_HEALTH` compatibility under `targetSdk = 35`; updated `onStartCommand` to specify `ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH` on API 34+; guarded service start/stop operations with `runCatching`; refactored session initialization to `WearWorkoutLogic.initializeSessionInstances`.
