# 📱 Mobile Smartphone App & Catalog Guide

> **Master equipment setup notes, custom weight increments, multi-template programming, workout history, and Health Connect integration on your smartphone.**

---

[📋 Machine Catalog](#-machine-catalog-management) • [📑 Template Management](#-workout-template-management) • [📈 Workout History & Editing](#-workout-history--editing) • [⚙️ Companion Settings & Backup](#️-companion-settings-sync--backup) • [🌉 Health Connect Integration](#-health-connect-bridge) • [🌐 Language Settings](#-language-settings)

---

## 📋 Machine Catalog Management

The **Catalog** tab (`CatalogScreen`) acts as your personal equipment registry. Every gym has slightly different machines, plate configurations, and adjustments. LockerLift allows you to record exact settings so you never forget your seat height or pin position again.

```mermaid
flowchart LR
    A["Catalog Tab"] --> B["Tap '+' Button"]
    B --> C["Fill Equipment Form<br/>(Name, Muscle, Setup, Increment)"]
    C --> D["Validation Check<br/>(No Duplicate Names)"]
    D --> E["Saved to Room DB & Synced to Watch"]
```

### Adding a New Machine
1. Open the LockerLift app on your phone and tap the **Catalog** tab on the bottom navigation bar.
2. Tap the floating **+** button.
3. Complete the equipment fields:
   * **Name:** Unique identifier for the equipment (e.g., *Incline Chest Press*, *Seated Cable Row*).
   * **Target Muscle Group:** Primary muscle targeted (e.g., *Chest*, *Back*, *Hamstrings*, *Shoulders*).
   * **Device Settings Note (Optional):** Crucial setup details so you can immediately configure the machine at the gym (e.g., *"Seat level 5, backrest angle 2, handle 3"*).
   * **Increment (kg):** The smallest weight step available on this specific weight stack or barbell (e.g., `2.5` kg for cable stacks, `1.25` kg for plate-loaded barbells).
4. Tap **Save**.

> [!IMPORTANT]
> LockerLift enforces strict uniqueness on machine names to prevent duplicate entries from cluttering your watch interface. If a machine with that name already exists, you will be prompted to choose a distinctive name.

---

### Editing Equipment Details & Auto-Sync
* Tap any machine in your catalog to edit its name, muscle group, custom settings note, or weight increment.
* Whenever you save changes to a machine, LockerLift automatically synchronizes the updated catalog to your smartwatch via Google Play Services `DataClient` (`/equipment_catalog`).

---

## 📑 Workout Template Management

The **Templates** tab (`TemplateListScreen`) provides flexible, $n$-template programming capabilities. You can create as many training splits or single-day routines as your program requires.

### Managing Exercises in a Template (US 2.1 & US 2.2)
Tap on any template card to open the **Template Detail Editor**:

```mermaid
flowchart TD
    Card["Tap Template Card"] --> Editor["Template Detail Editor"]
    Editor --> A["Move Up / Move Down (Reorder Exercises)"]
    Editor --> B["Add Exercise (Catalog Picker)"]
    Editor --> C["Remove Exercise (Leaves Catalog Intact)"]
    Editor --> Save["Save Template"]
    Save --> Sync["Automatic DataClient Sync to Watch"]
```

1. **Reordering Exercises (US 2.1, AK 2.1.3):**
   * Use the **▲ (Move Up)** and **▼ (Move Down)** buttons on any exercise to change its execution sequence.
   * Exercises will load on your watch in this exact order.
2. **Assigning Exercises (US 2.2, AK 2.2.1):**
   * Tap **+ Add Exercise** to open the catalog picker and assign any machine to the template.
   * A single machine can be assigned to multiple templates (e.g. barbell bench in both *Push* and *Full Body*).
3. **Removing Exercises (US 2.2, AK 2.2.2):**
   * Tap **Remove** next to an exercise to detach it from the template.
   * **Catalog & History Safety:** Removing an exercise from a template leaves the machine intact in your global catalog and never alters or deletes your past workout history.
4. **Automatic Smartwatch Sync (US 5.2):**
   * When you tap **Save**, the template and ordered machine associations are automatically transmitted to your Wear OS watch via `DataClient` (`/workout_templates`).

### Cold-Start Templates (Zero-Prep Plans)
Don't have time to configure all exercises before heading to the gym?
* Create an empty template with just a name (e.g., *Exploratory Leg Day*).
* At the gym, launch this template on your watch. Use the **+ Add Exercise** button to build the plan on the fly.
* At the end of the session, the watch will offer to consolidate your selections back into the template on your phone!

---

## 📈 Workout History & Editing (US 4.1 & Issue #20)

The **History** tab (`HistoryScreen`) provides a clear, chronological archive of all completed workout sessions, with full post-workout editing and deletion capabilities:

```text
┌────────────────────────────────────────────────────────┐
│ Upper Body A                           WEAR_OS (Watch) │
│ 13.09.2026 18:30                         [Edit] [Delete]│
│ ────────────────────────────────────────────────────── │
│ • Lat Pulldown: 60.0kg × 12, 62.5kg × 10, 62.5kg × 8   │
│ • Incline Dumbbell Press: 32.0kg × 10, 32.0kg × 9      │
│ • Cable Lateral Raise: 7.5kg × 15, 7.5kg × 14          │
└────────────────────────────────────────────────────────┘
```

### 1. Editing Past Sessions (Issue #20)
Tap the **Edit** icon on any past session card to open the **Session Detail Editor**:
* **Adjusting Weights & Reps:** Edit any logged set using stepper buttons or manual entry. Identical domain boundary clamping is enforced (`0.0–1000.0 kg`, `1–999 reps`).
* **Adding Sets:** Tap **+ Add Set** under any exercise to record an additional completed set.
* **Deleting Sets:** Remove erroneous sets with the delete icon. Remaining sets are automatically renumbered with contiguous indexing (`1..N`).
* **Removing Machine Instances:** Remove an entire exercise from the session. The global machine catalog remains untouched (Invariant 2).
* **Automatic Convergence:** Saving changes re-streams the updated session payload to your Wear OS watch via `ChannelClient`, ensuring past performance reference data is identical on your wrist.

### 2. Deleting Workouts & Health Connect Cleanup
* Tap the **Delete** icon on any session card to permanently remove it.
* A confirmation dialog protects against accidental deletions.
* **Database Cascade:** Removing a session automatically purges child machine instances and sets via Room `CASCADE`.
* **Health Connect Purge:** The corresponding Health Connect session (and telemetry) is automatically deleted via `HealthConnectManager.deleteWorkoutSession(sessionId)`.
* **Sync Tombstone:** A deletion notification (`/workout_delete`) is dispatched to your Wear OS watch to purge the session from the watch history.

---

## ⚙️ Companion Settings, Sync & Backup (US 5.3 & US 7.2)

The **Settings** tab provides control over companion smartwatch synchronization and local data protection:

### 1. Wear OS Companion & Sync Hub (US 5.3)
* **Live Connection Status:** Shows whether your smartwatch is connected via Bluetooth/Wi-Fi (`"Connected: Galaxy Watch 6"`) or disconnected (`"No watch connected / Disconnected"`).
* **Last Sync Timestamp:** Displays the exact date and time of the last successful synchronization.
* **Pending Sync Queue:** Shows how many items are waiting to be sent to the watch.
* **Sync Master Data Now:** Immediately pushes the entire machine catalog and all templates to your smartwatch via Google Play Services `DataClient`.
* **Sync Workouts Now:** Immediately triggers the background sync worker to process and flush pending queue items.

### 2. Local Storage Backup & Restore (US 7.2)
* **Privacy-First Storage:** Export your entire database to any folder on your device or SD card via the Android Storage Access Framework (SAF).
* **GZip & SHA-256 Integrity:** Backups are compressed with GZip and validated against cryptographic SHA-256 checksums to guarantee zero corruption upon restore.
* **Automated Periodic Backups:** Configure automated daily or weekly backups with customizable retention policies (keep 3, 5, or 10 backups).
* **Android Sharesheet:** Directly export and share backup archives via Google Drive, Nextcloud, email, or messaging apps.

---

## 🌉 Health Connect Bridge

LockerLift connects directly with Android's unified health database:

### How Export Works:
1. When your Wear OS watch finishes syncing a workout to your phone via `ChannelClient`, the phone's `MobileDataLayerListenerService` automatically checks Health Connect availability.
2. If permissions are granted, an `ExerciseSessionRecord` is created with:
   * **Exercise Type:** `EXERCISE_TYPE_STRENGTH_TRAINING`
   * **Start & End Timestamps:** Exact workout duration
   * **Workout Title:** Template name or *"Free Workout (LockerLift)"*
3. **Telemetry Integration (US 6.1, AK 6.1.3):**
   * **Calories Burned (`TotalCaloriesBurnedRecord`):** Total active calories recorded during the session are linked to the exercise record.
   * **Heart Rate (`HeartRateRecord`):** Heart rate samples logged during the workout are attached as time-series metrics.
4. **Granular Privacy & Silent Fallback (US 6.2):**
   * LockerLift checks granted permissions (`HealthConnectClient.permissionController.getGrantedPermissions()`) before each write.
   * If telemetry permissions are denied, the exercise session is still exported without heart rate or calories.
   * If all Health Connect permissions are denied, the export gracefully falls back without any errors or interruptions to normal app use.
5. Compatible apps (such as **Google Fit**, **Samsung Health**, **Peloton**, or **MyFitnessPal**) immediately read the session from Health Connect without any manual export/import steps!

---

## 🌐 Language Settings

LockerLift features full internationalization support:
* **Default Language:** English
* **German Translation:** Supported natively (`values-de/`)

### Changing App Language:
* **Android 13+ (Per-App Language):**  
  Go to **Phone Settings ➔ Apps ➔ LockerLift ➔ Language** and select your preferred language (English or German).
* **Android 9–12:**  
  LockerLift automatically adapts to your smartphone's primary system display language.

---

[Next: ❓ FAQ & Troubleshooting ➔](faq-troubleshooting.md)
