---
name: issue-creation-guide
description: >-
  Standards, templates, and best practices for authoring and filing high-quality GitHub issues in LockerLift.
  Use whenever defining new user stories, reporting bugs, specifying technical debt, or filing tasks via the GitHub CLI.
---

# LockerLift Issue Creation Guide (`issue-creation-guide`)

This skill defines the standards, required structure, label taxonomies, and CLI workflows for authoring and filing issues in the **LockerLift** repository.

---

## 1. Core Principles for Issues in LockerLift

Every issue in LockerLift serves as an actionable specification for human developers and autonomous AI agents. To ensure seamless implementation:

1. **Strictly Actionable:** An issue must define concrete, testable outcomes—never vague desires.
2. **Acceptance Criteria (AK) are Mandatory:** Every feature issue must include a checklist of acceptance criteria (`- [ ] **AK X.Y.Z:** ...`).
3. **Architectural Invariant Compliance:** Proposed features must respect LockerLift's core invariants:
   * Offline-first on Wear OS (no network calls on the watch during workouts).
   * UUIDv4 String primary keys for all entities.
   * Decoupled templates and session instances.
   * Data Layer channel segregation (`DataClient`, `ChannelClient`, `MessageClient`).
   * Health Connect writes performed strictly by the mobile phone.

---

## 2. Standard Issue Templates

### 2.1 Feature / User Story Template

```markdown
### User Story
**As a** [type of athlete/user],
**I want** [capability or interface behavior],
**so that** [specific training benefit or value].

---

### Context & Motivation
[1-2 paragraphs explaining why this feature is needed, how it fits into the Locker Scenario, and relevant user flow]

---

### Acceptance Criteria
- [ ] **AK X.1:** [Clear, testable behavior 1]
- [ ] **AK X.2:** [Clear, testable behavior 2]
- [ ] **AK X.3:** [Edge case handling or validation rule]

---

### Technical Scope & Affected Modules
* **Modules:** `:wear`, `:mobile`, `:core:database`, `:core:sync`, or `:core:healthconnect`
* **Entities / DAOs:** [Affected Room entities or DAO queries]
* **UI Components:** [Screens, dialogs, or rotary interactions]
```

### 2.2 Bug Report Template

```markdown
### Bug Description
[Clear and concise description of the defect]

---

### Reproduction Steps
1. On [Watch / Phone], navigate to [...]
2. Perform action [...]
3. Observe unexpected behavior [...]

---

### Expected vs. Actual Behavior
* **Expected:** [What should happen according to specification]
* **Actual:** [What actually happened, including crashes or logcat errors]

---

### Environment
* **Device:** [e.g., Galaxy Watch 6 / Pixel 8]
* **OS Version:** [e.g., Wear OS 4.0 / Android 14]
* **App Version / Commit:** [e.g., v1.0.0 or commit hash]

---

### Regression Test Requirement
- [ ] Automated unit test covering this failure must be added to [TestClass].
```

---

## 3. Label Taxonomy

Apply the appropriate labels when creating an issue:

| Label | Category | Purpose |
|---|---|---|
| `enhancement` | Type | New feature, algorithm, or UI capability. |
| `bug` | Type | Unexpected behavior, calculation error, or crash. |
| `documentation` | Type | Documentation, user guides, or agent skills. |
| `epic:catalog` | Epic | Global equipment catalog (`:core:database`, `:mobile`). |
| `epic:templates` | Epic | $n$-template management and exercise ordering. |
| `epic:wear-tracking` | Epic | Autonomous on-wrist tracking and rotary bezel input. |
| `epic:progression` | Epic | Double progression and historical performance data. |
| `epic:sync` | Epic | Wearable Data Layer Store-and-Forward sync. |
| `epic:healthconnect` | Epic | Health Connect integration and telemetry records. |

---

## 4. GitHub CLI (`gh`) Workflow

> [!TIP]
> Always use `--body-file` (or pipe via stdin) when creating issues with the GitHub CLI. Passing multi-line Markdown with quotes directly via `--body "..."` frequently fails on Windows PowerShell and Unix shells due to quote escaping.

### Step-by-Step Creation via `gh`:

```bash
# 1. Draft the issue content in a temporary markdown file
cat << 'EOF' > issue_draft.md
### User Story
**As a lifter**, I want to export my workout history to a CSV file, **so that** I can analyze my progress in external spreadsheet tools.

### Acceptance Criteria
- [ ] **AK 1:** Export button available on the HistoryScreen.
- [ ] **AK 2:** Generated CSV includes date, machine name, set number, weight (kg), and reps.
- [ ] **AK 3:** Handled via Android Sharesheet without requiring external storage permissions.
EOF

# 2. File the issue via GitHub CLI
gh issue create \
  --title "US 7.1: CSV Data Export on Mobile" \
  --label "enhancement" \
  --body-file issue_draft.md

# 3. Clean up the draft file
rm issue_draft.md
```
