#!/usr/bin/env bash
set -euo pipefail

required_vars=(
  NAVIAMP_ANDROID_KEYSTORE
  NAVIAMP_ANDROID_KEYSTORE_PASSWORD
  NAVIAMP_ANDROID_KEY_ALIAS
  NAVIAMP_ANDROID_KEY_PASSWORD
)

for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "Missing required signing environment variable: $var" >&2
    exit 1
  fi
done

if [[ ! -f "$NAVIAMP_ANDROID_KEYSTORE" ]]; then
  echo "NAVIAMP_ANDROID_KEYSTORE does not point to a file: $NAVIAMP_ANDROID_KEYSTORE" >&2
  exit 1
fi

echo "Android release signing environment is configured."
