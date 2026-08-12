#!/usr/bin/env bash
set -euo pipefail

if (( $# == 0 )); then
  echo "Usage: $0 TEST_TASK [TEST_TASK ...]" >&2
  exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
total_tests=0
total_failures=0
total_errors=0
total_skipped=0

for test_task in "$@"; do
  report_files=()
  while IFS= read -r -d '' report_file; do
    report_files+=("$report_file")
  done < <(find "$repo_root" -path "*/build/test-results/$test_task/TEST-*.xml" -type f -print0)

  if (( ${#report_files[@]} == 0 )); then
    echo "No XML test results found for $test_task" >&2
    exit 1
  fi

  read -r tests failures errors skipped < <(
    awk '
      BEGIN { tests = failures = errors = skipped = 0 }
      /<testsuite / {
        for (field = 1; field <= NF; field++) {
          value = $field
          gsub(/[^0-9]/, "", value)
          if ($field ~ /^tests=/) tests += value
          else if ($field ~ /^failures=/) failures += value
          else if ($field ~ /^errors=/) errors += value
          else if ($field ~ /^skipped=/) skipped += value
        }
      }
      END { print tests, failures, errors, skipped }
    ' "${report_files[@]}"
  )

  printf '%s: %d tests, %d failures, %d errors, %d skipped\n' \
    "$test_task" "$tests" "$failures" "$errors" "$skipped"

  total_tests=$((total_tests + tests))
  total_failures=$((total_failures + failures))
  total_errors=$((total_errors + errors))
  total_skipped=$((total_skipped + skipped))
done

printf 'Combined: %d tests, %d failures, %d errors, %d skipped\n' \
  "$total_tests" "$total_failures" "$total_errors" "$total_skipped"
