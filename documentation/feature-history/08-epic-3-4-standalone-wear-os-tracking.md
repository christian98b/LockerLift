# Epic 3 & 4: Standalone Wear OS Tracking (`:wear`)

  - `WorkoutForegroundService` with Ongoing Activity notification & WakeLock.
  - `MainActivity` with template selection & free workout (localized strings).
  - `ActiveWorkoutScreen` (Exercise list, machine catalog selection, ad-hoc machine creation with duplicate check [US 1.1, AK 1.1.2, AK 1.1.3], station replacement [US 3.3, AK 3.3.2], settings note editing with catalog persistence [US 1.2, AK 1.2.2], station skipping [AK 3.3.1], template consolidation, queue persistence, localized strings).
  - `RepsWeightInputScreen` (Rotary input support, quick buttons, double progression overload suggestion, historical reference data card & prefill [US 4.1, AK 4.1.1], localized strings).
  - `RestTimerScreen` (Rest timer with haptic vibration, localized strings).
  - `WearWorkoutLogic` (Pure domain logic delegating to shared `WorkoutTrackingLogic`).
  - `WearDataLayerListenerService` (Master data sync for catalog and templates, bidirectional workout session reception from phone via `ChannelClient`, ACK dispatch).
