#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
requested="${1:-patch}"
version_file="$repo_root/VERSION"
version_code_file="$repo_root/VERSION_CODE"

"$repo_root/scripts/validate-version.sh" >/dev/null

version="$(tr -d '[:space:]' < "$version_file")"
version_code="$(tr -d '[:space:]' < "$version_code_file")"

numeric_version="${version#v}"
core="${numeric_version%%[-+]*}"
IFS='.' read -r major minor patch <<< "$core"

case "$requested" in
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
  v*)
    semver_regex='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$'
    if [[ ! "$requested" =~ $semver_regex ]]; then
      echo "Explicit version must be v-prefixed SemVer; got: $requested" >&2
      exit 1
    fi
    if [[ "$requested" == "$version" ]]; then
      echo "Explicit version must differ from the current version: $version" >&2
      exit 1
    fi
    next_version="$requested"
    ;;
  *)
    echo "Usage: $0 [patch|minor|major|vX.Y.Z[-prerelease]]" >&2
    exit 1
    ;;
esac

next_version="${next_version:-v$major.$minor.$patch}"
next_version_code=$((version_code + 1))

printf '%s\n' "$next_version" > "$version_file"
printf '%s\n' "$next_version_code" > "$version_code_file"

echo "Bumped version: $version ($version_code) -> $next_version ($next_version_code)"
