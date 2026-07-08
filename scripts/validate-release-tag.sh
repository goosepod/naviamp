#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tag="${1:-${GITHUB_REF_NAME:-}}"

if [[ -z "$tag" ]]; then
  echo "Usage: $0 vX.Y.Z" >&2
  exit 1
fi

"$repo_root/scripts/validate-version.sh" >/dev/null

version="$(tr -d '[:space:]' < "$repo_root/VERSION")"
if [[ "$tag" != "$version" ]]; then
  echo "Release tag must match VERSION exactly: tag=$tag VERSION=$version" >&2
  exit 1
fi

echo "Release tag matches VERSION: $tag"
