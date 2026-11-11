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

if [[ -f "$repo_root/scripts/send-release-announcement.py" ]]; then
  "${PYTHON:-python3}" "$repo_root/scripts/send-release-announcement.py" \
    --notes "$repo_root/.github/releases/${tag}-announcement.md" \
    --release-url "https://github.com/goosepod/naviamp/releases/tag/${tag}" \
    --dry-run >/dev/null
fi

echo "Release tag matches VERSION and announcement: $tag"
