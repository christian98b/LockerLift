# 47 – Build Config Hygiene: Version, Wrapper, SDK, Room Schema, R8

Aligns the app version with the tagged 1.15.0 release and removes the build-configuration drift that let `versionName`, the changelog, the Gradle wrapper, `compileSdk`, Room schema export, and release minification fall out of date.

## What changed

- **Single version source.** `lockerlift.versionName`, `lockerlift.versionCode`, and the SDK levels live in `gradle.properties`. `:mobile` and `:wear` read them. Phone `versionCode` for 1.15.0 is 23 (1.12.0 shipped as 20; tags 1.13.0–1.15.0 never incremented it). The watch `versionCode` is that value plus `lockerlift.wearVersionCodeOffset` (100000) because Play requires a unique version code per form factor for package `com.lockerlift`.
- **`scripts/bump-version.sh`** bumps the property, prepends the changelog section, and with `--tag` commits those files and creates `vX.Y.Z`.
- **`scripts/check-version.sh`** fails when `versionName` and the top changelog heading disagree, when the latest git tag is newer than the app version, or when a tag-triggered release tag does not match. Wired into the CI workflows.
- **Gradle wrapper** (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) for Gradle 9.6.0. CI calls `./gradlew`.
- **`compileSdk` 36** on every Android module, installed in CI from `lockerlift.compileSdk` via `scripts/ci-install-android-sdk.sh`. `targetSdk` stays 35.
- **Room `exportSchema = true`** with the Room Gradle plugin writing `core/database/schemas/`. CI fails if that export drifts.
- **Release minify and resource shrinking** on both apps, with keep rules for Room, SQLCipher, kotlinx.serialization, the Wearable Data Layer, and Health Connect. `scripts/check-release-apk.sh` checks the minified APK still contains SQLCipher, `LockerLiftDatabase_Impl`, and a kotlinx serializer.
- **Health Connect** `connect-client` moved from `1.1.0-alpha10` to stable `1.1.0`. `Metadata` is now built with `Metadata.manualEntry(...)` because the constructor is internal as of 1.1.0.

## Files

- `gradle.properties`, `gradle/libs.versions.toml`, `build.gradle.kts`
- `mobile/build.gradle.kts`, `wear/build.gradle.kts`
- `core/database/build.gradle.kts`, `core/sync/build.gradle.kts`, `core/healthconnect/build.gradle.kts`
- `core/database/src/main/kotlin/com/lockerlift/core/database/LockerLiftDatabase.kt`
- `core/healthconnect/.../ExerciseRecordBuilder.kt`, `ActivityRecordBuilder.kt`
- `core/database/schemas/` (generated, committed)
- `proguard/lockerlift-release.pro`, module `proguard-rules.pro`, `consumer-rules.pro`
- `scripts/bump-version.sh`, `scripts/check-version.sh`, `scripts/ci-install-android-sdk.sh`, `scripts/check-release-apk.sh`
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`
- `.github/workflows/build-and-test.yml`, `build-pr.yml`, `build-debug.yml`, `release.yml`
- `README.md`, `CHANGELOG.md` (versioning notes only; no new release section)

## Tests

- `LockerLiftMigrationTest` — migration chain 2→current, fresh database matches the exported schema, and a reconstructed v2 database (tag v1.9.1) migrates forward preserving a machine row and the built-in activity types.
- Existing Health Connect builder tests cover `Metadata.manualEntry`.

## Not done

- Release signing (issue #16) was not touched. `release.yml` still publishes unsigned debug APKs.
- `targetSdk` was not raised to 36.
- Historical Room JSON schemas 2–8 were not invented; v2 is recreated in the migration test from the entities at that tag, and only the current schema is exported.
- Health Connect 1.2.x was left alone because it is still alpha.
