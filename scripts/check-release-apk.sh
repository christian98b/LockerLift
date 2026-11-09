#!/usr/bin/env bash
# Smoke-check a minified release APK still contains the pieces R8 must not strip:
# the SQLCipher native library, the Room database implementation, and a
# kotlinx.serialization serializer. Does not sign anything.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: scripts/check-release-apk.sh <apk>" >&2
  exit 1
fi

APK="$1"
if [[ ! -f "${APK}" ]]; then
  echo "error: APK not found: ${APK}" >&2
  exit 1
fi

listing="$(unzip -l "${APK}")"

require_zip_entry() {
  local pattern="$1"
  local label="$2"
  # Here-string, not a pipe: grep -q under pipefail reports SIGPIPE as failure
  # even when the pattern matched.
  if ! grep -E -q "${pattern}" <<< "${listing}"; then
    echo "error: ${APK} is missing ${label} (${pattern})" >&2
    exit 1
  fi
}

require_zip_entry 'lib/.*/libsqlcipher\.so' "SQLCipher native library"
require_zip_entry 'classes.*\.dex' "dex"

# Class descriptors survive as strings in the dex when the keep rules hold.
dex_strings="$(unzip -p "${APK}" 'classes*.dex' | strings || true)"
require_string() {
  local needle="$1"
  local label="$2"
  if ! grep -a -F -q -- "${needle}" <<< "${dex_strings}"; then
    echo "error: ${APK} dex is missing ${label} (${needle}); R8 likely stripped it" >&2
    exit 1
  fi
}

require_string 'LockerLiftDatabase_Impl' "Room database implementation"
require_string '$$serializer' "kotlinx.serialization serializer"

echo "Release APK OK: ${APK}"
