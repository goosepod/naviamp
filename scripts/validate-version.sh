#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
version_file="$repo_root/VERSION"
version_code_file="$repo_root/VERSION_CODE"

if [[ ! -f "$version_file" ]]; then
  echo "Missing VERSION file" >&2
  exit 1
fi

if [[ ! -f "$version_code_file" ]]; then
  echo "Missing VERSION_CODE file" >&2
  exit 1
fi

version="$(tr -d '[:space:]' < "$version_file")"
version_code="$(tr -d '[:space:]' < "$version_code_file")"

semver_regex='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$'
if [[ ! "$version" =~ $semver_regex ]]; then
  echo "VERSION must be v-prefixed SemVer, for example v0.15.0; got: $version" >&2
  exit 1
fi

if [[ ! "$version_code" =~ ^[1-9][0-9]*$ ]]; then
  echo "VERSION_CODE must be a positive integer, got: $version_code" >&2
  exit 1
fi

if (( version_code > 2100000000 )); then
  echo "VERSION_CODE must be <= 2100000000 for Google Play, got: $version_code" >&2
  exit 1
fi

echo "Version is valid: $version ($version_code)"
