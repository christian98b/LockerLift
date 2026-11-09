#!/usr/bin/env bash
# Bump LockerLift's single version source (gradle.properties), prepend a
# CHANGELOG section, and optionally commit and tag.
#
#   scripts/bump-version.sh <major|minor|patch|X.Y.Z> [--dry-run] [--tag]
#   scripts/bump-version.sh --tag
#
# --dry-run prints the next versionName and versionCode and does not write.
# --tag creates annotated tag vX.Y.Z. When a bump is also requested, the
# version files are committed first so the tag points at that commit.
# The script never pushes. Pushing the tag triggers .github/workflows/release.yml.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DRY=0
DO_TAG=0
BUMP=""

usage() {
  echo "Usage: scripts/bump-version.sh <major|minor|patch|X.Y.Z> [--dry-run] [--tag]" >&2
  echo "       scripts/bump-version.sh --tag" >&2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY=1 ;;
    --tag) DO_TAG=1 ;;
    -h|--help) usage; exit 0 ;;
    major|minor|patch) BUMP="$1" ;;
    [0-9]*) BUMP="$1" ;;
    *) echo "error: unknown argument '$1'" >&2; usage; exit 1 ;;
  esac
  shift
done

if [[ -z "${BUMP}" && "${DO_TAG}" -eq 0 ]]; then
  usage
  exit 1
fi

current_name="$(grep -E '^lockerlift\.versionName=' gradle.properties | head -n 1 | cut -d= -f2 | tr -d '[:space:]')"
current_code="$(grep -E '^lockerlift\.versionCode=' gradle.properties | head -n 1 | cut -d= -f2 | tr -d '[:space:]')"

if [[ -n "${BUMP}" ]]; then
  case "${BUMP}" in
    major|minor|patch)
      IFS=. read -r major minor patch <<< "${current_name}"
      case "${BUMP}" in
        major) major=$((major + 1)); minor=0; patch=0 ;;
        minor) minor=$((minor + 1)); patch=0 ;;
        patch) patch=$((patch + 1)) ;;
      esac
      NEW_NAME="${major}.${minor}.${patch}"
      ;;
    *)
      if ! [[ "${BUMP}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "error: '${BUMP}' is not MAJOR.MINOR.PATCH" >&2
        exit 1
      fi
      NEW_NAME="${BUMP}"
      ;;
  esac
  NEW_CODE=$((current_code + 1))
else
  NEW_NAME="${current_name}"
  NEW_CODE="${current_code}"
fi

if [[ "${DRY}" -eq 1 ]]; then
  echo "dry-run: ${current_name} (code ${current_code}) -> ${NEW_NAME} (code ${NEW_CODE})"
  exit 0
fi

if [[ -n "${BUMP}" ]]; then
  if grep -qE "^## \\[${NEW_NAME}\\]" CHANGELOG.md; then
    echo "error: CHANGELOG already contains ## [${NEW_NAME}]" >&2
    exit 1
  fi
  TODAY="$(date +%Y-%m-%d)"
  python3 - "${NEW_NAME}" "${NEW_CODE}" "${TODAY}" <<'PY'
import pathlib, re, sys
name, code, today = sys.argv[1], sys.argv[2], sys.argv[3]
props = pathlib.Path("gradle.properties")
text = props.read_text()
text2, n = re.subn(r"(?m)^lockerlift\.versionName=.*$", f"lockerlift.versionName={name}", text, count=1)
text2, n2 = re.subn(r"(?m)^lockerlift\.versionCode=.*$", f"lockerlift.versionCode={code}", text2, count=1)
if n != 1 or n2 != 1:
    raise SystemExit("failed to update gradle.properties")
props.write_text(text2)

changelog = pathlib.Path("CHANGELOG.md")
body = changelog.read_text()
section = (
    f"## [{name}] - {today}\n\n"
    "### 🚀 Neue Features\n\n"
    "- _Release-Notizen ergänzen._\n\n"
    "---\n\n"
)
match = re.search(r"(?m)^## \[\d+\.\d+\.\d+\]", body)
if not match:
    raise SystemExit("CHANGELOG has no version section to insert before")
changelog.write_text(body[:match.start()] + section + body[match.start():])
PY
  echo "Updated gradle.properties and CHANGELOG.md to ${NEW_NAME} (code ${NEW_CODE})"
  echo "Edit the new CHANGELOG section before tagging."
fi

if [[ "${DO_TAG}" -eq 0 ]]; then
  exit 0
fi

# Re-read in case this invocation just wrote the files.
VERSION_NAME="$(grep -E '^lockerlift\.versionName=' gradle.properties | head -n 1 | cut -d= -f2 | tr -d '[:space:]')"
VERSION_CODE="$(grep -E '^lockerlift\.versionCode=' gradle.properties | head -n 1 | cut -d= -f2 | tr -d '[:space:]')"
CHANGELOG_VERSION="$(grep -E '^## \[[0-9]+\.[0-9]+\.[0-9]+\]' CHANGELOG.md | head -n 1 | sed -E 's/^## \[([0-9]+\.[0-9]+\.[0-9]+)\].*/\1/')"
if [[ "${CHANGELOG_VERSION}" != "${VERSION_NAME}" ]]; then
  echo "error: refusing to tag; CHANGELOG [${CHANGELOG_VERSION}] != versionName ${VERSION_NAME}" >&2
  exit 1
fi

TAG="v${VERSION_NAME}"
if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
  echo "error: tag ${TAG} already exists" >&2
  exit 1
fi

if [[ -n "$(git status --porcelain -- gradle.properties CHANGELOG.md)" ]]; then
  other="$(git status --porcelain | grep -v -E '^(.. )?gradle.properties$|^(.. )?CHANGELOG.md$' || true)"
  if [[ -n "${other}" ]]; then
    echo "error: working tree has changes besides gradle.properties and CHANGELOG.md; commit or stash them first" >&2
    echo "${other}" >&2
    exit 1
  fi
  git add gradle.properties CHANGELOG.md
  git commit -m "chore(project): bump version to ${TAG} (code ${VERSION_CODE})"
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "error: working tree is not clean; tag was not created" >&2
  git status --short >&2
  exit 1
fi

git tag -a "${TAG}" -m "Release ${TAG}"
echo "Created tag ${TAG} on $(git rev-parse --short HEAD). Push it with: git push origin ${TAG}"
