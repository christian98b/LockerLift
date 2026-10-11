# Issue #21: Wear OS Tracking UX Enhancements (:wear)

  - Workout Pause & Resume in ActiveWorkoutScreen with foreground service persistence (Invariant 5), pause state management, and active session duration adjustment.
  - Intuitive Set Logging -> Rest Timer -> Next Set sequence with labeled step (Set X logged), Next Set continuation preselecting the next set, and Next Exercise navigation.
  - Configurable Rest Timer with persistent default duration (WearPreferences), real-time +/- 15s adjustment buttons without timer reset, and Skip Rest / Next Set actions.
  - Clear 'Skip Exercise' / 'Übung überspringen' labeling and informative status text to distinguish station skipping from set deletion.
  - In-workout set editing and set deletion directly from exercise cards, with clamped weight/reps and sequential renumbering (1..N).
  - All new user-facing strings localized in English (alues/strings.xml) and German (alues-de/strings.xml).
  - Pure unit tests in WearWorkoutLogicTest.kt (expanded to 42 tests) covering rest timer calculation/bounds, set update & deletion with renumbering, pause/resume transitions, and station navigation.
