# 33 – Wear OS Material 3 Component Migration & Dynamic Color

Remediates the mismatched button styling on the watch: buttons rendered as phone-style rectangles, mixed component libraries ignoring the app theme, and a fixed brand palette that diverged from the phone's Material You colors.

## Problem

* All wear screens used phone-style `androidx.compose.material3.Button`/`Card` stretched `fillMaxWidth(0.9f)` — full-width rectangles with phone corner radii that look misplaced on round displays.
* `androidx.wear.compose.material.CompactButton` (Wear Material *1*) was mixed in; Material 1 components read their own `MaterialTheme.colors`, which was never provided, so these buttons fell back to library defaults and ignored the LockerLift palette entirely.
* The watch used a static dark brand palette while the phone renders Material You dynamic color (`dynamicColor = true`, Android 12+), so the two apps never visually matched.
* The theme's `#121212` background was never painted (pure black window showed instead).

## Solution

* Migrated every wear screen to **Wear Compose Material 3** (`androidx.wear.compose:compose-material3:1.5.0`): `Button`, `CompactButton`, `Card`, `CardDefaults`, `ButtonDefaults`, `Text`, `MaterialTheme` are now the round-screen-optimized wear variants that consume the wear M3 `ColorScheme`.
* **Dynamic color on both devices** (product decision): on API 31+ the watch uses the wallpaper-derived dark dynamic scheme via `dynamicDarkColorScheme()`, matching the phone's dynamic color behavior. API < 31 falls back to the static LockerLift brand dark palette.
* Wear M3 1.5.0's `ColorScheme` (token set: `surfaceContainer`/`Low`/`High`, no plain `surface`) is produced from the phone M3 scheme by a dedicated `toWearColorScheme()` mapper.
* Root `Box` in `MainActivity` now paints `colorScheme.background`, so the declared background color actually renders.
* Dropped the unused Wear Material *1* (`compose-material`) dependency.

## Files Created / Modified

* `gradle/libs.versions.toml` (modified: added `composeWearMaterial3 = 1.5.0`, removed `androidx-wear-compose-material`)
* `wear/build.gradle.kts` (modified: `compose-material3` replaces `compose-material`)
* `wear/src/main/kotlin/com/lockerlift/wear/theme/WearTheme.kt` (modified: dynamic color support, wear M3 theme, `toWearColorScheme()` mapper, `surfaceContainerLow` brand token)
* `wear/src/main/kotlin/com/lockerlift/wear/MainActivity.kt` (modified: wear M3 imports, painted background)
* `wear/src/main/kotlin/com/lockerlift/wear/presentation/ActiveWorkoutScreen.kt` (modified: wear M3 components)
* `wear/src/main/kotlin/com/lockerlift/wear/presentation/RepsWeightInputScreen.kt` (modified: wear M3 components)
* `wear/src/main/kotlin/com/lockerlift/wear/presentation/RestTimerScreen.kt` (modified: wear M3 components)
* `wear/src/main/kotlin/com/lockerlift/wear/presentation/WearSettingsScreen.kt` (modified: wear M3 components)

## Unit Tests Added

* `wear/src/test/kotlin/com/lockerlift/wear/theme/WearColorSchemeMappingTest.kt` (new): verifies the brand fallback scheme and custom/dynamic schemes map losslessly into the wear M3 `ColorScheme` (primary, containers, surface container hierarchy, error slots).
* `wear/src/test/kotlin/com/lockerlift/wear/theme/WearColorSchemeTest.kt` (existing, untracked until this commit): guards the brand dark palette (near-black AMOLED background, brand colors, WCAG AA contrast).

## Verification

* `gradle test` green across all modules (`:core:model`, `:core:database`, `:core:sync`, `:core:healthconnect`, `:mobile`, `:wear`).
* Wear debug APK installed on the Wear OS emulator (API 36, 454x454): home screen, active workout overview and add-machine picker render with wear pill buttons, correct edge fading and dynamic palette; `AndroidRuntime` clean, process alive.
