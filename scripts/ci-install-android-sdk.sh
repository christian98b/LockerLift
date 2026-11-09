#!/usr/bin/env bash
# Install the Android SDK packages this repo compiles against.
# The platform is lockerlift.compileSdk in gradle.properties so CI cannot
# drift behind the modules. Build-tools 35 stays installed because AGP still
# resolves it for some library transforms; 36.0.0 matches compileSdk 36.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"

if [[ ! -d "${SDK_ROOT}" ]]; then
  echo "error: Android SDK not found at ${SDK_ROOT}" >&2
  exit 1
fi

SDKMANAGER="$(find "${SDK_ROOT}/cmdline-tools" -name sdkmanager -type f | head -n 1)"
if [[ -z "${SDKMANAGER}" ]]; then
  echo "error: sdkmanager not found under ${SDK_ROOT}/cmdline-tools" >&2
  exit 1
fi

SDK_BIN="$(dirname "${SDKMANAGER}")"
if [[ -n "${GITHUB_PATH:-}" ]]; then
  echo "${SDK_BIN}" >> "${GITHUB_PATH}"
  echo "${SDK_ROOT}/platform-tools" >> "${GITHUB_PATH}"
fi
if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "ANDROID_HOME=${SDK_ROOT}" >> "${GITHUB_ENV}"
  echo "ANDROID_SDK_ROOT=${SDK_ROOT}" >> "${GITHUB_ENV}"
fi
export ANDROID_HOME="${SDK_ROOT}"
export ANDROID_SDK_ROOT="${SDK_ROOT}"
export PATH="${SDK_BIN}:${SDK_ROOT}/platform-tools:${PATH}"

COMPILE_SDK="$(grep -E '^lockerlift\.compileSdk=' "${ROOT}/gradle.properties" | head -n 1 | cut -d= -f2 | tr -d '[:space:]')"
if [[ -z "${COMPILE_SDK}" ]]; then
  echo "error: lockerlift.compileSdk missing from gradle.properties" >&2
  exit 1
fi

yes | sdkmanager --licenses >/dev/null || true
sdkmanager \
  "platforms;android-${COMPILE_SDK}" \
  "build-tools;${COMPILE_SDK}.0.0" \
  "build-tools;35.0.0" \
  "platform-tools"

echo "Installed platforms;android-${COMPILE_SDK}, build-tools;${COMPILE_SDK}.0.0, build-tools;35.0.0"
