# 34 – Gradle 9.6 / AGP 9.4.1 Upgrade: CI Alignment & Room KSP2 Remediation

The toolchain upgrade to Gradle 9.6.0 / AGP 9.4.1 / Kotlin 2.2.10 / KSP 2.3.6 (KSP2) required two follow-up remediations to make CI green again.

## Problem

1. **Pipeline Gradle pin**: all workflows (`build-and-test.yml`, `build-pr.yml`, `build-debug.yml`, `release.yml`) still installed Gradle 8.10.2. AGP 8.13.2 already required Gradle >= 8.13 (breaking CI on `main`), and the upgrade to AGP 9.4.1 raised the minimum to Gradle 9.6.0.
2. **Room 2.6.1 ↔ KSP2 incompatibility**: Room 2.6.1 predates KSP2 and fails during `:core:database:kspDebugKotlin` with `java.lang.IllegalStateException: unexpected jvm signature V` once KSP moved to the 2.3.x version line.

## Solution

* Bumped `gradle-version` in all four workflows to 9.6.0, matching the upgraded `gradle-wrapper.properties` distribution (release workflow pins must always match the wrapper).
* Upgraded Room to 2.8.5 (KSP2-compatible stable line).

## Files Modified

* `.github/workflows/build-and-test.yml`
* `.github/workflows/build-pr.yml`
* `.github/workflows/build-debug.yml`
* `.github/workflows/release.yml`
* `gradle/libs.versions.toml` (room 2.6.1 → 2.8.5)

## Verification

* Full `gradle test` suite green locally on Gradle 9.6.0 / JDK 21 (all modules).
* No unit test changes were required; Room 2.8.5 is drop-in for this codebase.

## Follow-Up

* The Ubuntu build server is retired; local builds now happen on the Mac in Android Studio (JDK 21 / Gradle 9.6.0 / Android SDK 35, two emulators for APK verification). The `local-build` skill was rewritten for this environment.
* The Android Studio generated `gradle/gradle-daemon-jvm.properties` (JetBrains JDK 21 daemon criteria) stays untracked: CI runners use Temurin 21 and cannot download a JetBrains JDK, so the file must not be committed.
