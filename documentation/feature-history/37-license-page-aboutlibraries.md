# 37 – License-Page: Open-Source-Lizenzen aller verwendeten Packages

## Feature / User Story

Implementation von GitHub Issue #39: Eine License-Page listet alle verwendeten Open-Source-Packages (inklusive `:core:*`-Transitiv-Abhängigkeiten) mit Name, Version und vollständiger Lizenz. Die Daten werden automatisch aus den Gradle-Abhängigkeiten generiert (nicht manuell gepflegt) und sind damit Teil jedes Builds — CI-APKs enthalten die Attributionen automatisch.

## Ansatz

Einsatz von [AboutLibraries](https://github.com/mikepenz/AboutLibraries) (mikepenz) in Version **14.1.0** (AGP-9-kompatibel, kein Play-Services-Zwang, passt zum Local-First-Ansatz):

- **Gradle-Plugin** `com.mikepenz.aboutlibraries.plugin.android` in `:mobile` und `:wear`: generiert `res/raw/aboutlibraries.json` automatisch als Teil des Android-Builds (inkl. `:core:*`-Abhängigkeiten und Transitiv-Lizenzen).
- **Runtime-Dependency** `aboutlibraries-core`: parst die generierte JSON-Datei lokal (kein Netzwerkzugriff zur Laufzeit).

## Dateien erstellt / geändert

### erstellt
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/licenses/LicenseUtils.kt` — Mapping-/Formatierungslogik (Sortierung, Lizenznamen, eingebettete Lizenztexte).
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/licenses/OpenSourceLicensesScreen.kt` — Mobile License-Page (Material 3, LazyColumn, aufklappbare Cards mit vollem Lizenztext).
- `wear/src/main/kotlin/com/lockerlift/wear/presentation/licenses/LicenseUtils.kt` — Wear-Pendant der Logik.
- `wear/src/main/kotlin/com/lockerlift/wear/presentation/licenses/WearLicensesScreen.kt` — Wear OS License-Page (ScalingLazyColumn, Rotary-Input, aufklappbare Cards).
- `mobile/src/test/kotlin/com/lockerlift/mobile/ui/licenses/LicenseUtilsTest.kt` — Unit-Tests.
- `wear/src/test/kotlin/com/lockerlift/wear/presentation/licenses/LicenseUtilsTest.kt` — Unit-Tests.

### geändert
- `build.gradle.kts`, `gradle/libs.versions.toml` — Plugin + Library im Version-Catalog registriert.
- `mobile/build.gradle.kts`, `wear/build.gradle.kts` — Plugin angewendet, `aboutlibraries-core` als Dependency.
- `mobile/src/main/kotlin/com/lockerlift/mobile/MainActivity.kt` — Navigation: License-Screen als Full-Screen-Zustand mit Back-Handler.
- `mobile/src/main/kotlin/com/lockerlift/mobile/ui/settings/SettingsScreen.kt` — Einstieg „Open-Source-Lizenzen" in den Einstellungen.
- `wear/src/main/kotlin/com/lockerlift/wear/MainActivity.kt` — Navigation zum License-Screen.
- `wear/src/main/kotlin/com/lockerlift/wear/presentation/WearSettingsScreen.kt` — Einstieg „Open-Source-Lizenzen" in den Wear-Einstellungen.
- `mobile/src/main/res/values{,-de}/strings.xml`, `wear/src/main/res/values{,-de}/strings.xml` — i18n-Strings.

## Unit-Tests

`LicenseUtilsTest` (jeweils in `:mobile` und `:wear`): alphabetische Sortierung (case-insensitive), Versions-/Lizenz-Mapping, Formatierung der Lizenznamen, Konkatenation eingebetteter Lizenztexte, Blank-Fallback ohne eingebettete Texte.

## Hinweise

- SQLCipher (Dual-Lizenz) und weitere direkte Abhängigkeiten werden vom Plugin aus den POM-Metadaten erfasst; die NOTICE-Dateien der direkten Abhängigkeiten wurden gesichtet.
- Die `licenses_license_format`-Zeile zeigt die SPDX-Namen; der vollständige Copyright-/Lizenzheader ist im expandierten Card-Zustand enthalten, sofern vom Plugin eingebettet.
