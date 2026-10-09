---
name: Local Build & Test
description: Build and test LockerLift locally on the Ubuntu server (JDK 21, Gradle 8.10.2, Android SDK 35) with memory throttling for low-RAM machines
---

# Local Build & Test on the Ubuntu Server

The server has only **3.3 GB RAM** and no root access. The toolchain therefore lives entirely in the user home and must run with throttling.

## Toolchain locations

| Component | Version | Path |
|---|---|---|
| JDK | Temurin 21 (`JAVA_HOME`) | `~/opt/jdk-21` |
| Gradle | 8.10.2 (must match `gradle-wrapper.properties`) | `~/opt/gradle-8.10.2` |
| cmdline-tools | latest | `~/Android/Sdk/cmdline-tools/latest` |
| platform-tools | r37 (ADB) | `~/Android/Sdk/platform-tools` |
| Android platform | android-35 | `~/Android/Sdk/platforms` |
| build-tools | 35.0.0 | `~/Android/Sdk/build-tools` |

Environment is pre-configured in `~/.bashrc` (`JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `PATH`). There is **no** `gradlew` wrapper in the repo — use the `gradle` binary from `~/opt/gradle-8.10.2/bin`.

`local.properties` in the repo root points to the SDK: `sdk.dir=/home/christian/Android/Sdk`. Do not commit it.

## Memory-throttled Gradle invocation (mandatory)

The project's `gradle.properties` requests 2048 MB heap, which crashes the box (swap thrash → `Gradle Test Executor` connection timeout). Always run:

```sh
gradle <task> \
  -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" \
  -Dorg.gradle.workers.max=1 \
  -Dkotlin.compiler.execution.strategy=in-process
```

Notes:
- The Kotlin daemon may still start alongside; keep it in mind when watching `free -h`.
- Use `--no-daemon` only when running once; the daemon actually speeds up repeated builds but takes ~1 GB resident heap — on CI-like one-off runs prefer `--no-daemon`.

## Common commands

```sh
# Full unit-test suite across all modules (E2E sync suite is skipped by default, same as CI)
gradle test <throttle-flags>

# Run the in-memory E2E sync test suite explicitly
gradle test -PrunE2eSync=true <throttle-flags>

# Compile a single module fast (first sanity check after edits)
gradle :mobile:compileDebugKotlin <throttle-flags>

# Build debug APKs (output in mobile/build/outputs/apk/debug/ and wear/build/outputs/apk/debug/)
gradle :mobile:assembleDebug :wear:assembleDebug <throttle-flags>

# Clean (when gradle state seems stuck)
gradle clean --no-daemon <throttle-flags>
```

## Troubleshooting

- ` Unable to connect to the child process 'Gradle Test Executor 1'` → memory exhaustion. Use the throttle flags and close other heavy processes before retrying.
- `sdk.dir is missing`/`local.properties` complains → verify `ANDROID_HOME` and `local.properties`.
- `error: unresolved reference 'bodyExtraSmall'` (or similar Wear-printer type slots) → these slots exist only in Wear Compose Material 3; this project uses standard `androidx.compose.material3`, so migrate to `typography.bodySmall`.
- Check installed SDK packages with `sdkmanager --list_installed` (set `JAVA_HOME` first).
- RAM/swap situation: `free -h && swapon --show`. Expect the build to need ~1.5 GB combined heap max.
