#!/usr/bin/env bash
set -euo pipefail

# Emit no classification if history cannot be read: callers must fail closed.
if [[ "${GITHUB_EVENT_NAME:-}" != pull_request ]]; then
    echo 'run_full=true'
    echo 'run_translations=false'
    exit 0
fi
changed_paths="$(mktemp)"
trap 'rm -f "$changed_paths"' EXIT
# Disable rename detection: both removal and addition must require full verification.
git diff --no-renames --name-status -z "${BASE_SHA:?}...${HEAD_SHA:?}" > "$changed_paths"
run_full=false
run_translations=false
while IFS= read -r -d '' status && IFS= read -r -d '' path; do
    if [[ "$status" == M && "$path" =~ ^core/ui/src/commonMain/composeResources/values-[A-Za-z0-9-]+/strings\.xml$ ]]; then
        run_translations=true
        continue
    fi
    case "$path" in
        *.md|docs/*|readme-assets/*) ;;
        *) run_full=true ;;
    esac
done < "$changed_paths"
echo "run_full=$run_full"
echo "run_translations=$run_translations"
