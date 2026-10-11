# Dynamic Weight & Reps Stepper Layout with Machine Increment Adaptation & Anti-Wrapping (`:mobile`, `:core:database`)

  - `WorkoutTrackingLogic` (`:core:database`): Added `calculateWeightSteppers(incrementKg)` and `formatDelta(delta)`, computing machine-adapted negative and positive delta multiples (`-2x, -1x, -0.5x` and `+0.5x, +1x, +2x`) based on `defaultIncrementKg`, with plate-matching rules for `1.25kg`, `1.0kg`, `0.5kg`, and clean number formatting (e.g., `+5` instead of `+5.0`).
  - `MobileTrackingScreen` (`:mobile`):
    - Redesigned weight stepper controls into a balanced 2-row layout (Top Row: Minus steps `[-5, -2.5, -1.25]`, Bottom Row: Plus steps `[+1.25, +2.5, +5]`), providing ~85dp per button with large touch targets.
    - Enforced `maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Clip`, and compact content padding, eliminating text wrapping where the `5` in `-1.25` dropped down.
    - Wrapped dialog contents in `verticalScroll(rememberScrollState())` to prevent layout overflow on smaller viewports.
    - Extracted `RepsStepperControls` with single-line wrap protection.
  - Unit tests added:
    - `WorkoutTrackingLogicTest.kt` (`:core:database`): `testCalculateWeightSteppers()` covering standard 2.5 kg increment, 1.25 kg microplates, 5.0 kg heavy stacks, 1.0 kg dumbbells, 0.5 kg, and fallback; `testFormatDelta()` covering signed integer and decimal formatting.
    - `MobileWorkoutTrackingTest.kt` (`:mobile`): `testWeightStepperInteractionAndClamping()` covering delta application, lower bound clamping at 0 kg, and whitespace/wrap-free string formatting.
