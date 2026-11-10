#!/usr/bin/env bash
# Fail when the app version, the CHANGELOG top entry, and the git release tag disagree.
# versionName may be newer than the latest tag while the next release is being prepared
# (the tag is created by scripts/bump-version.sh --tag). A tag newer than the app
# version, or a changelog entry that does not match versionName, is a failure.
# On a tag push (GITHUB_REF=refs/tags/vX.Y.Z) the tag, versionName and changelog must be equal.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

prop() {
  local key="$1"
  local value
  value="$(grep -E "^${key}=" gradle.properties | head -n 1 | cut -d= -f2- | tr -d '[:space:]')"
  if [[ -z "${value}" ]]; then
    echo "error: missing ${key} in gradle.properties" >&2
    exit 1
  fi
  printf '%s' "${value}"
}

VERSION_NAME="$(prop lockerlift.versionName)"
VERSION_CODE="$(prop lockerlift.versionCode)"
OFFSET="$(prop lockerlift.wearVersionCodeOffset)"

if ! [[ "${VERSION_NAME}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "error: versionName '${VERSION_NAME}' is not MAJOR.MINOR.PATCH" >&2
  exit 1
fi
if ! [[ "${VERSION_CODE}" =~ ^[1-9][0-9]*$ ]]; then
  echo "error: versionCode '${VERSION_CODE}' must be a positive integer" >&2
  exit 1
fi
if ! [[ "${OFFSET}" =~ ^[1-9][0-9]*$ ]]; then
  echo "error: wearVersionCodeOffset '${OFFSET}' must be a positive integer" >&2
  exit 1
fi
if (( VERSION_CODE >= OFFSET )); then
  echo "error: versionCode ${VERSION_CODE} must stay below wear offset ${OFFSET} so phone and watch codes never collide" >&2
  exit 1
fi

CHANGELOG_VERSION="$(grep -E '^## \[[0-9]+\.[0-9]+\.[0-9]+\]' CHANGELOG.md | head -n 1 | sed -E 's/^## \[([0-9]+\.[0-9]+\.[0-9]+)\].*/\1/')"
if [[ "${CHANGELOG_VERSION}" != "${VERSION_NAME}" ]]; then
  echo "error: CHANGELOG top entry [${CHANGELOG_VERSION}] does not match versionName ${VERSION_NAME}" >&2
  exit 1
fi

require_property_ref() {
  local file="$1"
  local key="$2"
  if ! grep -q "lockerlift.${key}" "${file}"; then
    echo "error: ${file} does not read lockerlift.${key}" >&2
    exit 1
  fi
}

forbid_literal() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if grep -E "${pattern}" "${file}" >/dev/null; then
    echo "error: ${file} ${message}" >&2
    grep -nE "${pattern}" "${file}" >&2 || true
    exit 1
  fi
}

for file in \
  mobile/build.gradle.kts \
  wear/build.gradle.kts \
  core/database/build.gradle.kts \
  core/sync/build.gradle.kts \
  core/healthconnect/build.gradle.kts
do
  require_property_ref "${file}" compileSdk
  forbid_literal "${file}" 'compileSdk[[:space:]]*=[[:space:]]*[0-9]+' "hardcodes compileSdk"
done

require_property_ref mobile/build.gradle.kts versionName
require_property_ref mobile/build.gradle.kts versionCode
require_property_ref mobile/build.gradle.kts minSdk
require_property_ref mobile/build.gradle.kts targetSdk
require_property_ref wear/build.gradle.kts versionName
require_property_ref wear/build.gradle.kts versionCode
require_property_ref wear/build.gradle.kts wearVersionCodeOffset
require_property_ref wear/build.gradle.kts wearMinSdk
require_property_ref wear/build.gradle.kts targetSdk

if grep -q 'wearVersionCodeOffset' mobile/build.gradle.kts; then
  echo "error: mobile must use the base versionCode, not the wear offset" >&2
  exit 1
fi

for file in mobile/build.gradle.kts wear/build.gradle.kts; do
  forbid_literal "${file}" 'versionName[[:space:]]*=[[:space:]]*"' "hardcodes versionName"
  forbid_literal "${file}" 'versionCode[[:space:]]*=[[:space:]]*[0-9]+' "hardcodes versionCode"
done

semver_gt() {
  local top
  top="$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n 1)"
  [[ "${top}" == "$1" && "$1" != "$2" ]]
}

LATEST="$(git tag -l 'v[0-9]*.[0-9]*.[0-9]*' | sed 's/^v//' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true)"
if [[ -n "${LATEST}" ]] && semver_gt "${LATEST}" "${VERSION_NAME}"; then
  echo "error: latest git tag v${LATEST} is newer than app version ${VERSION_NAME}" >&2
  exit 1
fi

HEAD_TAGS="$(git tag --points-at HEAD | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' || true)"
if [[ -n "${HEAD_TAGS}" ]]; then
  while IFS= read -r tag; do
    [[ -z "${tag}" ]] && continue
    tag_version="${tag#v}"
    if [[ "${tag_version}" != "${VERSION_NAME}" ]]; then
      echo "error: tag ${tag} on HEAD does not match versionName ${VERSION_NAME}" >&2
      exit 1
    fi
  done <<< "${HEAD_TAGS}"
fi

if [[ "${GITHUB_REF:-}" == refs/tags/v* ]]; then
  ref_tag="${GITHUB_REF#refs/tags/}"
  ref_version="${ref_tag#v}"
  if [[ "${ref_version}" != "${VERSION_NAME}" ]]; then
    echo "error: release tag ${ref_tag} does not match versionName ${VERSION_NAME}" >&2
    exit 1
  fi
fi

WEAR_CODE=$((VERSION_CODE + OFFSET))
echo "Version check OK: ${VERSION_NAME} (phone code ${VERSION_CODE}, wear code ${WEAR_CODE}), changelog [${CHANGELOG_VERSION}], latest tag v${LATEST:-none}"
