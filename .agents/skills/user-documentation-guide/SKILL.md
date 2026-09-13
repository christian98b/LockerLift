---
name: user-documentation-guide
description: >-
  Guidelines, formatting standards, and visual design principles for writing beautiful, structured, and user-centric
  documentation in the LockerLift project. Use whenever authoring user guides, feature manuals, FAQs, onboarding docs,
  or README enhancements.
---

# LockerLift User Documentation Guide Skill

This skill defines the standards, style conventions, and architectural templates for creating beautiful, accessible, and highly structured **user documentation** across the **LockerLift** ecosystem.

---

## 1. Documentation Philosophy & Target Audience

LockerLift user documentation is written for **athletes, strength trainees, and gym-goers** who need clear, distraction-free instructions. 

### Core Tenets:
1. **Gym-First Clarity:** Trainees are often viewing documentation on mobile devices while at the gym. Information must be concise, skimmable, and immediately actionable.
2. **Emphasize the Locker Scenario:** Always clarify which action occurs on the **smartwatch** (in the gym, 100% offline) versus the **smartphone** (in the locker or at home).
3. **Show, Don't Just Tell:** Use structured tables, Mermaid sequence/flow diagrams, and clear step-by-step checklists over dense paragraphs.
4. **Consistency & Professional Polish:** Adhere strictly to GitHub Flavored Markdown (GFM) formatting standards, clear typographies, and standardized alert callouts.

---

## 2. Visual Design & Formatting Guidelines

### 2.1 Visual Hierarchy & Headings
* Use a single `#` H1 for the page title, followed by a concise sub-headline or tagline.
* Use `##` H2 for major structural sections with an emoji prefix for quick visual parsing (e.g., `## 🚀 Quick Start Guide`, `## ⚙️ Device Setup`).
* Use `###` H3 for specific feature steps or tasks.
* Include a navigation anchor bar beneath the title for documents longer than 150 lines:
  ```markdown
  [🚀 Quick Start](#-quick-start) • [⌚ Watch Setup](#-watch-setup) • [📱 Phone Features](#-phone-features) • [❓ FAQ](#-faq)
  ```

### 2.2 Strategic GitHub Alerts
Use GitHub-style alert callouts with restraint to highlight critical user information without creating visual clutter:

```markdown
> [!NOTE]
> Explanatory background details or context.

> [!TIP]
> Helpful shortcuts, battery-saving advice, or performance tips (e.g., using the rotary dial).

> [!IMPORTANT]
> Critical prerequisites, such as required permissions or Health Connect setup.

> [!WARNING]
> Potential traps, such as leaving a workout uncompleted before returning to the locker.
```

### 2.3 Diagrams & Data Visualizations
Whenever explaining a multi-step user journey, device handover, or data flow, provide a clean Mermaid diagram.

* **User Journey Flowcharts:** Use `flowchart TD` or `flowchart LR` with styled nodes.
* **Sync & Handshake Flows:** Use `sequenceDiagram` to illustrate watch-to-phone data transfer.
* **Keep Mermaid Labels Plain:** Avoid unescaped parentheses, special symbols, or HTML in labels.

---

## 3. Standard User Guide Template

When authoring a new user-facing guide (e.g., a guide for template management, rotary dial usage, or Health Connect pairing), follow this standard template:

```markdown
# 🏋️‍♂️ [Feature / Guide Name]

> **One-sentence summary of the value and practical outcome for the athlete.**

---

[📖 Overview](#-overview) • [🛠️ Step-by-Step](#️-step-by-step) • [💡 Pro Tips](#-pro-tips) • [❓ FAQ](#-faq)

---

## 📖 Overview
Brief context on what this feature does, where it runs (Phone vs. Smartwatch), and how it works in the locker scenario.

## 🛠️ Step-by-Step Guide

### Step 1: [Action Verb + Task]
1. Open the LockerLift app on your [Watch / Phone].
2. Tap **[Button Name]** or rotate the bezel.
3. Observe [Result / State change].

> [!TIP]
> [Relevant efficiency tip]

### Step 2: [Next Task]
...

## 💡 Pro Tips & Best Practices
* **Tip 1:** Bulleted practical advice for gym training.
* **Tip 2:** Advice on progression, cadence, or equipment adjustment.

## ❓ Frequently Asked Questions (FAQ)

<details>
<summary><b>Question text goes here?</b></summary>

Clear, reassuring, and direct answer explaining the solution.
</details>
```

---

## 4. Tone & Vocabulary Guidelines

| Concept | Preferred Term | Avoid | Rationale |
|---|---|---|---|
| Offline state | *Autonomous / Local-first* | *Disconnected / Broken* | Emphasizes that offline is the intended state, not an error. |
| The Locker Scenario | *Locker isolation / Locker mode* | *Out of range bug* | Frames the lack of connection as a core design feature. |
| Weight adjustments | *Increment / Stepping* | *Delta / Diff* | User-friendly gym terminology. |
| Double progression | *Double progression / Weight increase* | *Algorithm heuristic* | Common strength training vocabulary. |
| Physical bezel | *Rotary dial / Bezel* | *RotaryScrollable modifier* | User-facing hardware terminology, not code. |

---

## 5. Multi-Language Documentation Parity

LockerLift supports multiple languages (starting with English as default and German):
1. **Repository & Source Docs:** All official documentation stored in the root repository (`README.md`, `ARCHITECTURE.md`, `AGENTS.md`) is maintained in **English**.
2. **Localized User Documentation:**
   * When dedicated user manuals or release notes are published for end users, provide localized editions alongside English (e.g., `docs/user-guide.md` and `docs/de/user-guide.md`).
   * Follow the [internationalization-guide](../internationalization-guide/SKILL.md) for naming consistency and glossary terms.

---

## 6. Authoring & Verification Checklist

Before publishing or committing user documentation:
- [ ] **Accurate Terminology:** Follows the table in Section 4.
- [ ] **Distraction-Free:** Unnecessary fluff removed; clear, actionable instructions.
- [ ] **Correct Device Context:** Clearly states whether an action takes place on the watch or phone.
- [ ] **Valid Links:** All markdown file links, GitHub anchors, and issue links resolve correctly.
- [ ] **Mermaid Syntax Valid:** Fenced code blocks with `mermaid` render cleanly without errors.
- [ ] **Alert Syntax:** Uses standard GitHub alert syntax (`> [!TIP]`, `> [!NOTE]`).
- [ ] **Commit Convention:** Follow [git-commit-guidelines](../git-commit-guidelines/SKILL.md):
  ```bash
  git commit -m "docs: add comprehensive user guide for rotary input and rest timer"
  ```
