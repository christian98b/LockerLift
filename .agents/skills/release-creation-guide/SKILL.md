---
name: release-creation-guide
description: >-
  End-to-end operational guide and standard operating procedure for creating official
  releases in LockerLift. The release itself is fully automated: pushing a `v*` git tag
  triggers `.github/workflows/release.yml`, which builds signed-or-unsigned release APKs
  and publishes the GitHub Release with both APKs attached. Covers pre-release quality
  gates, Gradle version bumps, CHANGELOG maintenance, tagging, and post-release
  verification. No local PC, Android SDK, or manual `gh release create` upload is needed.
---

# LockerLift Release Creation Guide (`release-creation-guide`)

This skill defines the authoritative, step-by-step operational procedure for versioning,
tagging, and publishing official releases of **LockerLift** for Android and Wear OS.

> **IMPORTANT — Automation since v1.7.4:** The release is built and published entirely by
> the CI pipeline (`release.yml`, triggered by pushing a `v*` tag). Never download CI
> artifacts and attach them manually via `gh release create` — the workflow does this
> itself and publishes the release non-draft. The manual artifact-download procedure that
> older versions of this skill described is **obsolete**.

---

## 1. Release Lifecycle & Architecture

Every LockerLift release proceeds through this sequence:

```mermaid
flowchart TD
    A["1. Quality Gate Check<br/>(CI green on main, issues closed)"] --> B["2. Version Determination<br/>(SemVer rules)"]
    B --> C["3. Gradle Version Bump<br/>(versionCode & versionName)"]
    C --> D["4. CHANGELOG Entry<br/>(section '## [X.Y.Z]')"]
    D --> E["5. Push git tag v*<br/>(triggers release.yml)"]
    E --> F["6. CI builds release APKs<br/>and publishes the GitHub Release"]
    F --> G["7. Post-Release Verification<br/>(assets, changelog, feature-history log)"]
```

---

## 2. Pre-Release Quality Gates

Before tagging a release, verify all mandatory gates pass:

1. **Zero Open Milestone Issues:** All intended features, epics, or bugfixes must be
   closed and commented (refer to `issue-closing-and-commenting-guide`).
2. **100% Green CI Pipeline:** The latest commit on `main` must have a passing
   `Test & Build APKs` workflow (`build-and-test.yml`).
3. **Automated Unit Tests Passing:** Zero test failures across all modules
   (`:core:*`, `:mobile`, `:wear`).
4. **Version bump committed:** `lockerlift.versionName` and `lockerlift.versionCode`
   updated in `gradle.properties` (the only copy). Phone and watch share
   `versionName`. The watch `versionCode` is the phone code plus
   `lockerlift.wearVersionCodeOffset` so Play's per-form-factor uniqueness
   rule holds. The top CHANGELOG heading must match `versionName`.

---

## 3. Step-by-Step Release Workflow

### Step 1: Determine Version Bump

Consult [`.agents/skills/release-versioning-rules/SKILL.md`](../release-versioning-rules/SKILL.md):

* **MAJOR (`X.0.0`):** Breaking sync protocol changes, breaking Room database schema, dropping minSdk.
* **MINOR (`x.Y.0`):** New user-facing features (Epics, User Stories, new screens, backward-compatible additions).
* **PATCH (`x.y.Z`):** Bugfixes, performance optimizations, documentation updates, dependency bumps.

### Step 2: Bump Version

`gradle.properties` is the only place `versionName` and the phone `versionCode` are
defined. Bump them with the script (it also prepends `## [X.Y.Z]` to `CHANGELOG.md`):

```bash
./scripts/bump-version.sh minor          # or major | patch | X.Y.Z
# Edit the generated CHANGELOG section, then:
./scripts/bump-version.sh --tag          # commits the two files and tags vX.Y.Z
```

Do not write `versionCode` or `versionName` into the app `build.gradle.kts` files.
The watch version code is derived (`versionCode + wearVersionCodeOffset`) so the
two Play artifacts of package `com.lockerlift` never share a code.

### Step 3: Add a CHANGELOG Entry

Add a section to `CHANGELOG.md` in Keep-a-Changelog style. The release workflow extracts
the section matching the tag (`## [X.Y.Z]`) and uses it as the release body. If no
matching section exists, the workflow falls back to the entire CHANGELOG — so keep the
section heading exactly `## [X.Y.Z]`.

```markdown
## [1.7.5] - YYYY-MM-DD

### 🚀 New Features
* **<Feature>:** <Description>

### 🔧 Fixes
* **<Fix>:** <Description>
```

### Step 4: Commit, then Push the Tag

Commit the version bump and changelog, then create and push the tag. **The tag push is
the sole release trigger** — `release.yml` runs only on `on.push.tags: ['v*']`:

```bash
# Prefer scripts/bump-version.sh --tag, which commits gradle.properties + CHANGELOG.md
# and creates the annotated tag on that commit. Then:
git push origin main
git push origin vX.Y.Z
```

> [!IMPORTANT]
> Tag a commit that is already on `main` and contains the matching version bump.
> If the tag has to be moved (e.g., release fix), delete and re-push it:
> `git push origin :refs/tags/vX.Y.Z && git tag -f vX.Y.Z <sha> && git push origin vX.Y.Z`.
> A tag re-push triggers a new release run.

### Step 5: Monitor the Release Workflow

The workflow (`release.yml`) automatically:

1. Checks out the tag.
2. Sets up JDK 21 and the Android SDK platform from `lockerlift.compileSdk`
   (`scripts/ci-install-android-sdk.sh`). Manual `sdkmanager` setup stays —
   `android-actions/setup-android@v3` is broken and must not be reintroduced.
   Builds use `./gradlew` (Gradle 9.6.0, declared in `gradle/wrapper/gradle-wrapper.properties`).
3. Runs `./gradlew :mobile:assembleDebug :wear:assembleDebug`. The published
   artifacts stay unsigned debug APKs until release signing (issue #16) is
   configured. CI's `Test & Build APKs` workflow additionally runs
   `:mobile:assemble` and `:wear:assemble`, which includes the minified
   release variant.
4. Renames the APKs to `mobile-release.apk` / `wear-release.apk`.
5. Publishes a **non-draft** GitHub Release named "Release vX.Y.Z" with both APKs
   attached and the extracted CHANGELOG section as body.

Monitor it:

```bash
gh run list --workflow release.yml --limit 3
gh run watch <RUN_ID> --exit-status
```

Typical duration: ~7 minutes. If the run fails, inspect
`gh run view <RUN_ID> --log`, fix on `main`, then move the tag (see Step 4 note) and
re-trigger.

### Step 6: Post-Release Verification & Housekeeping

1. **Verify the online release:**

   ```bash
   gh release view vX.Y.Z
   ```

   Confirm `mobile-release.apk` and `wear-release.apk` are attached and the changelog
   body matches the `## [X.Y.Z]` section.

2. **Verify on main:** Ensure the `Test & Build APKs` workflow for the pushed `main`
   commit is green as well.

3. **Update tracking:** Add the release milestone, version code, and tag in a new file in `documentation/feature-history/` (see its README for conventions)
   to the progress log (as done for v1.7.3 and v1.7.4).

No local build, no artifact download, no manual APK upload is part of this procedure.

---

## 4. Signing Notes

* Without the `ANDROID_KEYSTORE_*` secrets configured in the repository, the pipeline
  builds **unsigned** release APKs. That is the current state.
* To produce signed APKs, configure the four repository secrets
  (`ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
  `ANDROID_KEY_PASSWORD`) as described in [`.github/README.md`](../../.github/README.md).
  No workflow change is needed — the build steps already forward these env vars.

---

## 5. Emergency Patch & Rollback Procedures

### Hotfix Protocol

1. If a critical regression is discovered in production, branch from tag `vX.Y.Z`:

   ```bash
   git checkout -b hotfix/vX.Y.(Z+1) vX.Y.Z
   ```

2. Apply the minimal bugfix with a regression test.
3. Merge into `main`, bump patch version, add changelog entry, verify green CI, and
   push tag `vX.Y.(Z+1)` to trigger the automated release.

### Deleting an Erroneous Release / Tag

```bash
# Delete release and remote tag
gh release delete vX.Y.Z --yes --cleanup-tag

# Delete local tag
git tag -d vX.Y.Z
```

> Re-pushing a tag with the same name after cleanup triggers a fresh automated release.
