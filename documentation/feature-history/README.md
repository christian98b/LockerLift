# Feature Implementation History

This directory is the authoritative, append-only history of everything that has been implemented in LockerLift. Each feature, user story, release milestone, or remediation effort gets its own Markdown file here instead of bloating `AGENTS.md`.

## Rules

1. **One feature, one file.** Whenever a new feature is implemented, adjusted, or extended, create a *new* Markdown file in this directory. Never append implementation logs to `AGENTS.md`.
2. **Naming convention:** `NN-short-kebab-case-title.md`, where `NN` is the next free two-digit sequence number (e.g., `30-my-new-feature.md`). Keep the title descriptive.
3. **Content per file:**
   - Which feature / user story was implemented (reference the GitHub issue if one exists).
   - Which files were created or modified.
   - Which unit tests were added.
4. **Never delete or rewrite existing history files** unless the documented feature was intentionally removed from the project.

## Index

| # | Feature | File |
|---|---|---|
| 01 | Project Setup & Multi-Module Architecture | [01-project-setup-multi-module-architecture.md](01-project-setup-multi-module-architecture.md) |
| 02 | Documentation & Issue Tracking | [02-documentation-issue-tracking.md](02-documentation-issue-tracking.md) |
| 03 | Internationalization (i18n) | [03-internationalization-i18n.md](03-internationalization-i18n.md) |
| 04 | Epic 1 & 2: Data Foundations & Models | [04-epic-1-2-data-foundations-models.md](04-epic-1-2-data-foundations-models.md) |
| 05 | Epic 5: Wearable Data Layer Synchronization | [05-epic-5-wearable-data-layer-sync.md](05-epic-5-wearable-data-layer-sync.md) |
| 06 | Epic 6: Health Connect Integration | [06-epic-6-health-connect-integration.md](06-epic-6-health-connect-integration.md) |
| 07 | Epic 1, 2 & 4: Mobile App | [07-epic-1-2-4-mobile-app.md](07-epic-1-2-4-mobile-app.md) |
| 08 | Epic 3 & 4: Standalone Wear OS Tracking | [08-epic-3-4-standalone-wear-os-tracking.md](08-epic-3-4-standalone-wear-os-tracking.md) |
| 09 | Bidirectional Workout History Synchronization | [09-bidirectional-workout-history-sync.md](09-bidirectional-workout-history-sync.md) |
| 10 | Security & Privacy Hardening (Audit Remediation) | [10-security-privacy-hardening.md](10-security-privacy-hardening.md) |
| 11 | Unit Testing Suite | [11-unit-testing-suite.md](11-unit-testing-suite.md) |
| 12 | Agent Skills | [12-agent-skills.md](12-agent-skills.md) |
| 13 | Official Releases | [13-official-releases.md](13-official-releases.md) |
| 14 | Brand Redesign & Minimalist AI App Icons | [14-brand-redesign-minimalist-ai-icons.md](14-brand-redesign-minimalist-ai-icons.md) |
| 15 | In-Memory E2E Synchronization Test Suite | [15-in-memory-e2e-sync-test-suite.md](15-in-memory-e2e-sync-test-suite.md) |
| 16 | US 5.4: Pull-to-Refresh Sync with Wear OS Wake-Up | [16-us-5-4-pull-to-refresh-sync.md](16-us-5-4-pull-to-refresh-sync.md) |
| 17 | Wear OS "Finish Exercise" UX Enhancement | [17-wear-os-finish-exercise-ux.md](17-wear-os-finish-exercise-ux.md) |
| 18 | Sync Mechanism Hardening & Defect Remediation | [18-sync-mechanism-hardening.md](18-sync-mechanism-hardening.md) |
| 19 | Material 3 Theming, Edge-to-Edge & Appbar Placement Fix | [19-material3-theming-edge-to-edge.md](19-material3-theming-edge-to-edge.md) |
| 20 | Mobile Live Workout Tracking UX Overhaul & Set Table Parity | [20-mobile-live-tracking-ux-overhaul.md](20-mobile-live-tracking-ux-overhaul.md) |
| 21 | Wear OS Always-In-Foreground & Ambient Screen Management | [21-wear-os-always-in-foreground.md](21-wear-os-always-in-foreground.md) |
| 22 | Issue #21: Wear OS Tracking UX Enhancements | [22-issue-21-wear-os-tracking-ux.md](22-issue-21-wear-os-tracking-ux.md) |
| 23 | US 7.2: Local Storage Backup & Restore | [23-us-7-2-local-backup-restore.md](23-us-7-2-local-backup-restore.md) |
| 24 | Issue #20: Edit and Delete Past Workouts in Mobile History | [24-issue-20-edit-delete-past-workouts.md](24-issue-20-edit-delete-past-workouts.md) |
| 25 | US 5.3 / Issue #22: Companion Connection Status & Manual Sync | [25-us-5-3-issue-22-companion-status-sync.md](25-us-5-3-issue-22-companion-status-sync.md) |
| 26 | CI/CD Pipeline & Build Remediation | [26-ci-cd-pipeline-build-remediation.md](26-ci-cd-pipeline-build-remediation.md) |
| 27 | 16 KB Page-Size Compatibility Remediation | [27-16kb-page-size-compatibility.md](27-16kb-page-size-compatibility.md) |
| 28 | Dynamic Weight & Reps Stepper Layout | [28-dynamic-weight-reps-steppers.md](28-dynamic-weight-reps-steppers.md) |
| 29 | Sync Reliability Hardening & Deep Audit Remediation | [29-sync-reliability-hardening-audit.md](29-sync-reliability-hardening-audit.md) |
| 30 | Code Examples: Sync Fixes (Reference Material) | [30-code-examples-sync-fixes.md](30-code-examples-sync-fixes.md) |
| 31 | Sync Reliability Improvements (Reference Material) | [31-sync-reliability-improvements.md](31-sync-reliability-improvements.md) |
| 32 | Insights Dashboard: Analytics, Graphs & Configurable Widgets | [32-insights-dashboard.md](32-insights-dashboard.md) |
| 33 | Wear OS Material 3 Component Migration & Dynamic Color | [33-wear-material3-dynamic-theming.md](33-wear-material3-dynamic-theming.md) |
| 34 | Gradle 9.6 / AGP 9.4.1 Upgrade: CI Alignment & Room KSP2 Remediation | [34-gradle9-agp9-ci-alignment.md](34-gradle9-agp9-ci-alignment.md) |
| 35 | Mobile Navigation Fixes: Settings Trap & History Sub-Tab Bar | [35-mobile-settings-history-nav-fixes.md](35-mobile-settings-history-nav-fixes.md) |
| 36 | Architecture Hardening: Durable Sessions, MVVM & Module Boundary Fix | [36-architecture-hardening-durable-sessions-mvvm.md](36-architecture-hardening-durable-sessions-mvvm.md) |
| 37 | License-Page: Open-Source-Lizenzen (Issue #39) | [37-license-page-aboutlibraries.md](37-license-page-aboutlibraries.md) |
| 38 | Muskelgruppen-Autocomplete beim Maschinen-Anlegen (Issue #36) | [38-muscle-group-autocomplete.md](38-muscle-group-autocomplete.md) |
| 39 | Machine & Template Deletion with Soft-Delete (Issue #40) | [39-machine-template-deletion-soft-delete.md](39-machine-template-deletion-soft-delete.md) |
| 40 | lbs-Gewichtsstapel: Einheit & Schrittweite pro Maschine (Issue #37) | [40-machine-weight-unit-lbs-stacks.md](40-machine-weight-unit-lbs-stacks.md) |
| 41 | Assistierte Maschinen: inverse Gewichtsprogression (Issue #35) | [41-assisted-machines-inverse-progression.md](41-assisted-machines-inverse-progression.md) |
| 42 | Activity Tracking: Log Other Sports (Issue #50) | [42-activity-tracking.md](42-activity-tracking.md) |
| 43 | Weight Stack Scale für lbs-Maschinen (Issue #48) | [43-weight-stack-scale.md](43-weight-stack-scale.md) |
| 44 | Workout Plan: Weekly Training Schedule (Issue #51) | [44-workout-plan.md](44-workout-plan.md) |
| 45 | MobileTrackingScreen Decomposition & Timer Logic in TrackingViewModel (Issue #57) | [45-tracking-screen-decomposition-timers.md](45-tracking-screen-decomposition-timers.md) |
