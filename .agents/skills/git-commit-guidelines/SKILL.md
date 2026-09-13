---
name: git-commit-guidelines
description: >-
  Guidelines, format specifications, and procedures for creating git commit messages in LockerLift
  following the Conventional Commits standard (v1.0.0). Use whenever staging files, drafting commit messages,
  or committing code and tests to the LockerLift repository.
---

# LockerLift Git Commit Guidelines Skill

This skill defines the standard procedure and format for creating git commits in the **LockerLift** repository.

---

## 1. Commit Message Structure

Every commit message MUST adhere to the **Conventional Commits** standard (v1.0.0):

```text
<type>(<scope>): <short imperative summary>

[Optional body: context, motivation, architectural decisions, what changed and why]

[Optional footer: references to user stories or issue tracking, e.g. Closes US-3.1]
```

---

## 2. Allowed Types (`type`)

| Type | When to use | Example |
|---|---|---|
| `feat` | Adding a new feature or user-facing capability | `feat(wear): add rotary input for reps and weight` |
| `fix` | Fixing a bug or unexpected behavior | `fix(sync): resolve channel timeout during session transfer` |
| `test` | Adding, updating, or correcting unit tests | `test(database): add unit tests for WorkoutSessionDao` |
| `docs` | Modifying documentation only (`README.md`, `AGENT.md`, etc.) | `docs: update sync protocol diagram in ARCHITECTURE.md` |
| `refactor`| Code restructuring without changing external behavior | `refactor(mobile): extract catalog form into sub-composable` |
| `chore` | Build configurations, Gradle dependencies, `.gitignore` | `chore(deps): update AndroidX Health Connect to alpha10` |
| `perf` | Performance optimizations | `perf(database): index session_machine_instances foreign keys` |

---

## 3. Allowed Scopes (`scope`)

Every commit (except pure root documentation/chore commits) should specify one of the project's modular scopes:

* **`model`**: Code in `:core:model`
* **`database`**: Code in `:core:database`
* **`sync`**: Code in `:core:sync`
* **`healthconnect`**: Code in `:core:healthconnect`
* **`mobile`**: Code in `:mobile`
* **`wear`**: Code in `:wear`
* **`project`**: Cross-cutting changes affecting multiple modules or root setup

---

## 4. Mandatory Rules & Invariants

1. **Imperative Mood / Present Tense:** Write `add`, `fix`, `update`, `refactor` (never `added`, `fixed`, `updated`).
2. **No Trailing Period:** Do NOT put a period at the end of the subject line.
3. **Subject Length:** Keep the first line strictly under 72 characters.
4. **Atomic Commits:** Each commit must encapsulate a single logical change.
5. **Strict Test Mandate (`feat` must include tests):** Whenever a new feature or logic is committed (`feat(...)`), the associated unit tests **MUST** be included in the exact same commit.
6. **Progress Tracking in `AGENT.md`:** When completing user stories or features, ensure the implementation tracking checklist in `AGENT.md` and `agents.md` is updated.

---

## 5. Standard Workflow for Committing

1. **Verify Unit Tests:** Ensure all unit tests in the affected modules are green.
2. **Review Diff:** Run `git status --short` and `git diff` to inspect changes.
3. **Stage Changes:** Stage relevant files with `git add <files>`.
4. **Draft Commit Message:** Follow the `<type>(<scope>): <summary>` format.
5. **Commit:** Execute `git commit -m "..."`.
6. **Verify Clean Tree:** Verify with `git status`.
