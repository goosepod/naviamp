#!/usr/bin/env bash
set -euo pipefail

# Emit no classification if history cannot be read: callers must fail closed.
if [[ "${GITHUB_EVENT_NAME:-}" != pull_request ]]; then
    echo 'run_full=true'
    exit 0
fi
changed_paths="$(mktemp)"
trap 'rm -f "$changed_paths"' EXIT
git diff --name-only -z "${BASE_SHA:?}" "${HEAD_SHA:?}" > "$changed_paths"
run_full=false
while IFS= read -r -d '' path; do
    case "$path" in
        *.md|docs/*|readme-assets/*) ;;
        *) run_full=true; break ;;
    esac
done < "$changed_paths"
echo "run_full=$run_full"
