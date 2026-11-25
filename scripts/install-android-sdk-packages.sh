#!/usr/bin/env bash
# Installs Android SDK packages, retrying corrupted or interrupted downloads.
#
# sdkmanager occasionally receives a truncated package zip ("Error reading Zip content from a
# SeekableByteChannel") and fails the whole job before any test runs. A fresh download succeeds,
# so this retries the installation only; it never retries the tests that use the packages.
#
# Usage: install-android-sdk-packages.sh [--log FILE] PACKAGE...
set -euo pipefail

log=/dev/null
if [ "${1:-}" = "--log" ]; then
  log="$2"
  shift 2
fi
if [ "$#" -eq 0 ]; then
  echo "usage: $0 [--log FILE] PACKAGE..." >&2
  exit 2
fi

attempts="${NAVIAMP_SDK_INSTALL_ATTEMPTS:-3}"
delay="${NAVIAMP_SDK_INSTALL_RETRY_DELAY:-15}"
attempt_log="$(mktemp)"
trap 'rm -f "$attempt_log"' EXIT

for attempt in $(seq 1 "$attempts"); do
  set +e
  sdkmanager "$@" 2>&1 | tee "$attempt_log" | tee -a "$log"
  status="${PIPESTATUS[0]}"
  set -e
  # sdkmanager can report a package preparation error while still exiting successfully.
  if [ "$status" -eq 0 ] && ! grep -q "An error occurred while preparing SDK package" "$attempt_log"; then
    exit 0
  fi
  if [ "$attempt" -lt "$attempts" ]; then
    echo "::warning::Android SDK package installation failed (attempt $attempt of $attempts, exit $status); retrying in ${delay}s." | tee -a "$log"
    sleep "$delay"
    delay=$((delay * 2))
  fi
done

echo "::error::Android SDK package installation failed after $attempts attempts." | tee -a "$log"
exit 1
