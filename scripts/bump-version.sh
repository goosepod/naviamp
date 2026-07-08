#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
part="${1:-patch}"
version_file="$repo_root/VERSION"
version_code_file="$repo_root/VERSION_CODE"

"$repo_root/scripts/validate-version.sh" >/dev/null

version="$(tr -d '[:space:]' < "$version_file")"
version_code="$(tr -d '[:space:]' < "$version_code_file")"

numeric_version="${version#v}"
core="${numeric_version%%[-+]*}"
IFS='.' read -r major minor patch <<< "$core"

case "$part" in
  major)
    major=$((major + 1))
    minor=0
    patch=0
    ;;
  minor)
    minor=$((minor + 1))
    patch=0
    ;;
  patch)
    patch=$((patch + 1))
    ;;
  *)
    echo "Usage: $0 [patch|minor|major]" >&2
    exit 1
    ;;
esac

next_version="v$major.$minor.$patch"
next_version_code=$((version_code + 1))

printf '%s\n' "$next_version" > "$version_file"
printf '%s\n' "$next_version_code" > "$version_code_file"

echo "Bumped version: $version ($version_code) -> $next_version ($next_version_code)"
