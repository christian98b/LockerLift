---
name: internationalization-guide
description: >-
  Step-by-step instructions, conventions, and architectural standards for adding new languages and managing
  localized string resources across LockerLift's mobile and wear modules. Use whenever introducing a new language,
  translating strings, or extracting UI text.
---

# LockerLift Internationalization (i18n) Guide Skill

This skill outlines the standard operating procedure for adding new languages and managing localized resources in the **LockerLift** project.

---

## 1. Core Principles & Invariants

1. **English as Base / Fallback Language:**
   * Default `res/values/strings.xml` in both `:mobile` and `:wear` **MUST** contain English strings.
   * Never put German or any other non-English language into the default `values/` directory.
2. **Standard Android Locale Directory Qualifiers:**
   * Translations reside in `res/values-<language_code>/strings.xml` using ISO 639-1 two-letter language codes (e.g., `values-de/`, `values-es/`, `values-fr/`, `values-it/`).
3. **1:1 Key Parity (Symmetry):**
   * Every key defined in `values/strings.xml` must exist in all localized `values-<lang>/strings.xml` files.
   * Removing or renaming a string key must be reflected in all language folders simultaneously.
4. **Zero Hardcoded User-Facing Strings:**
   * Compose UI components, ViewModels, Notifications, and dialogs must reference string resources via `stringResource(R.string.<key>)` or `context.getString(R.string.<key>)`.
   * Enums with user-facing labels must store a `@StringRes val labelRes: Int` instead of a hardcoded `String`.

---

## 2. Directory & Module Structure

Localization in LockerLift spans both application modules:

```text
LockerLift/
├── mobile/src/main/res/
│   ├── values/
│   │   └── strings.xml          # Base (English)
│   ├── values-de/
│   │   └── strings.xml          # German translation
│   └── values-<lang>/
│       └── strings.xml          # New language translation
└── wear/src/main/res/
    ├── values/
    │   └── strings.xml          # Base (English)
    ├── values-de/
    │   └── strings.xml          # German translation
    └── values-<lang>/
        └── strings.xml          # New language translation
```

---

## 3. Step-by-Step Procedure to Add a New Language

### Step 1: Determine the Target ISO 639-1 Language Code
Common examples:
* Spanish: `es` ➔ `values-es`
* French: `fr` ➔ `values-fr`
* Italian: `it` ➔ `values-it`
* Portuguese: `pt` ➔ `values-pt`
* Japanese: `ja` ➔ `values-ja`

### Step 2: Add Mobile Translations (`:mobile`)
1. Create directory `mobile/src/main/res/values-<lang>/`.
2. Create `mobile/src/main/res/values-<lang>/strings.xml`.
3. Copy all keys from `mobile/src/main/res/values/strings.xml` and translate values:
   ```xml
   <resources>
       <string name="app_name">LockerLift</string>

       <!-- Navigation Tabs -->
       <string name="tab_catalog">Catálogo</string>
       <string name="tab_templates">Plantillas</string>
       <string name="tab_history">Historial</string>
       <string name="tab_tracking">Seguimiento</string>
       ...
   </resources>
   ```

### Step 3: Add Wear OS Translations (`:wear`)
1. Create directory `wear/src/main/res/values-<lang>/`.
2. Create `wear/src/main/res/values-<lang>/strings.xml`.
3. Copy all keys from `wear/src/main/res/values/strings.xml` and translate values:
   ```xml
   <resources>
       <string name="app_name">LockerLift</string>

       <!-- Foreground Notification -->
       <string name="notification_channel_name">LockerLift Seguimiento de Entrenamiento</string>
       <string name="notification_title">Entrenamiento Activo</string>
       <string name="notification_text">Tu entrenamiento se está registrando en segundo plano.</string>
       ...
   </resources>
   ```

---

## 4. Formatting & Placeholder Conventions

### 4.1 Positional String Formats
Always use positional argument specifiers (`%1$s`, `%2$d`, `%1$f`) rather than bare `%s` or `%d`. Different grammatical structures require different word orders:

* **English:** `%1$s • Set %2$d` ➔ `Lat Pulldown • Set 1`
* **Other languages:** Allows reordering if necessary without changing code.

### 4.2 Special Character Escaping
* Escape single quotes / apostrophes with backslash: `Don\'t` or wrap in double quotes.
* Escape `@` and `?` if they appear at the start of a string: `\?` or `\@`.
* Use XML entities for ampersands (`&amp;`) and angle brackets (`&lt;`, `&gt;`).

---

## 5. UI Code Consumption Patterns

### 5.1 In Jetpack Compose / Wear OS Compose
```kotlin
import androidx.compose.ui.res.stringResource
import com.lockerlift.mobile.R

// Simple string
Text(text = stringResource(R.string.catalog_title))

// Formatted string with parameters
Text(text = stringResource(R.string.muscle_group_format, machine.targetMuscleGroup))
Text(text = stringResource(R.string.set_header_format, machine.name, setNumber))
```

### 5.2 In Android Services & Notifications
```kotlin
import com.lockerlift.wear.R

val title = context.getString(R.string.notification_title)
val formatted = context.getString(R.string.station_format, index + 1)
```

### 5.3 In Navigation Tabs & Enum Definitions
```kotlin
import androidx.annotation.StringRes
import com.lockerlift.mobile.R

enum class MobileTab(@StringRes val labelRes: Int) {
    CATALOG(R.string.tab_catalog),
    TEMPLATES(R.string.tab_templates),
    HISTORY(R.string.tab_history),
    TRACKING(R.string.tab_tracking)
}

// In Composable:
Text(text = stringResource(selectedTab.labelRes))
```

---

## 6. Verification & Quality Checklist

Before committing new translations:
1. **Key Parity Check:** Verify all keys in `values/strings.xml` are present in `values-<lang>/strings.xml`.
2. **Run Tests:** Ensure `./gradlew test` passes without regression.
3. **Build APKs:** Run `./gradlew :mobile:assembleDebug :wear:assembleDebug` to verify resource compilation.
4. **Update Documentation:**
   * Update [README.md](../../../README.md) under Key Features / Multi-Language Support.
   * Update [AGENTS.md](../../../AGENTS.md) tracking section.
5. **Git Commit:**
   * Adhere to [git-commit-guidelines](../git-commit-guidelines/SKILL.md):
     ```bash
     git commit -m "feat(i18n): add <Language> language support for mobile and wear"
     ```
