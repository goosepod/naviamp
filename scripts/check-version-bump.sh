#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
base_ref="${1:-origin/main}"

"$repo_root/scripts/validate-version.sh"

current_version="$(tr -d '[:space:]' < "$repo_root/VERSION")"
current_version_code="$(tr -d '[:space:]' < "$repo_root/VERSION_CODE")"

if ! base_version="$(git -C "$repo_root" show "$base_ref:VERSION" 2>/dev/null)"; then
  echo "Could not read VERSION from $base_ref; skipping bump comparison."
  exit 0
fi

if ! base_version_code="$(git -C "$repo_root" show "$base_ref:VERSION_CODE" 2>/dev/null)"; then
  echo "Could not read VERSION_CODE from $base_ref; skipping bump comparison."
  exit 0
fi

base_version="$(tr -d '[:space:]' <<< "$base_version")"
base_version_code="$(tr -d '[:space:]' <<< "$base_version_code")"

if [[ "$current_version" == "$base_version" ]]; then
  echo "VERSION must be bumped from $base_version before merging to main." >&2
  exit 1
fi

if (( current_version_code <= base_version_code )); then
  echo "VERSION_CODE must increase above $base_version_code before merging to main; got $current_version_code." >&2
  exit 1
fi

echo "Version bump is valid: $base_version ($base_version_code) -> $current_version ($current_version_code)"
