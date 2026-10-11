# LockerLift

<p align="center">
  <img src="documentation/assets/lockerlift_app_icon.jpg" alt="LockerLift App Icon" width="128" />
</p>

**LockerLift** is a privacy-first, open-source strength training tracker for Android and Wear OS, built for the *locker scenario*: your smartphone stays in the locker while your smartwatch tracks the entire workout completely offline.

## Key Features

**Smartphone app (`:mobile`)**
- Live workout tracking with a set table, past-performance comparison, and in-workout set editing
- Machine & exercise catalog with muscle groups, weight increments, and setup notes
- Unlimited workout templates with reordering and on-the-fly modification
- Editable workout history with cascade deletion
- Wear OS companion status, manual sync, and local backup & restore (SAF, GZip/JSON, SHA-256)
- Health Connect export and synchronized deletion
- English and German localization

**Wear OS app (`:wear`)**
- 100% autonomous tracking with zero phone connectivity required
- Rotary input for fast weight and rep adjustments
- Pause/resume, configurable rest timer with haptics, and set correction during the workout
- Store-and-forward sync queue that flushes automatically once the phone reconnects
- Progressive overload guidance based on historical performance

## The Locker Scenario

The watch operates fully autonomously during the workout — no Bluetooth, no Wi-Fi. All CRUD operations complete locally on the watch; workout records are queued and streamed to the phone asynchronously once you return to your locker (Store-and-Forward). The phone then persists the session and exports it to Health Connect. See the [Locker Scenario Guide](documentation/locker-scenario-guide.md) for details.

## Architecture

Highly modular monorepo with strict separation of concerns:

| Module | Purpose |
|---|---|
| `:core:model` | Pure Kotlin domain models, zero Android dependencies |
| `:core:database` | Room database, DAOs, SQLCipher AES-256 encryption |
| `:core:sync` | Wearable Data Layer manager, JSON serialization, sync queue worker |
| `:core:healthconnect` | Health Connect client and record builder |
| `:mobile` | Smartphone app (Jetpack Compose, Material 3) |
| `:wear` | Wear OS app (Horologist, rotary input, foreground service) |

Full system design, ER diagrams, and sync protocol: [ARCHITECTURE.md](ARCHITECTURE.md)

## Getting Started

**Prerequisites:** JDK 21, Android SDK 35 (min SDK 28 mobile / 30 Wear OS), Gradle 8.10.2 (no `gradlew` wrapper; use the `gradle` binary)

```bash
# Run all unit tests
gradle test

# Build the smartphone APK
gradle :mobile:assembleDebug

# Build the Wear OS APK
gradle :wear:assembleDebug
```

Setup and installation instructions: [Getting Started](documentation/getting-started.md)

## Documentation

- [Documentation Portal](documentation/README.md) — user guides for mobile, Wear OS, sync, and troubleshooting
- [ARCHITECTURE.md](ARCHITECTURE.md) — technical system architecture
- [AGENTS.md](AGENTS.md) — developer and AI agent guidelines, invariants, and conventions
- [Feature Implementation History](documentation/feature-history/README.md) — chronological log of all implemented features
- [CHANGELOG.md](CHANGELOG.md) — release changelog

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
