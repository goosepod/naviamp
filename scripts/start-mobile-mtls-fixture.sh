#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build
umask 077
./gradlew :core:testkit:prepareMobileMutualTlsFixture -Pkotlin.native.enableKlibsCrossCompilation=false --console=plain
fixture_directory="$(mktemp -d "$PWD/build/mobile-mtls-run.XXXXXX")"
fixture_bootstrap="$fixture_directory/fixture.bootstrap"
"$JAVA_HOME/bin/java" @build/mobile-mtls-fixture.args "$fixture_bootstrap" >"$fixture_directory/fixture.log" 2>&1 &
fixture_launcher=$!
fixture_ready=false
cleanup_failed_start() {
  if [ "$fixture_ready" != true ]; then
    kill "$fixture_launcher" 2>/dev/null || true
    wait "$fixture_launcher" 2>/dev/null || true
  fi
}
trap cleanup_failed_start EXIT
for _ in $(seq 1 300); do
  if [ -f "$fixture_bootstrap" ] && python3 scripts/mobile-mtls-fixture.py ready "$fixture_bootstrap"; then
    if [ -n "${GITHUB_ENV:-}" ]; then
      echo "NAVIAMP_MTLS_FIXTURE_BOOTSTRAP_FILE=$fixture_bootstrap" >> "$GITHUB_ENV"
    fi
    fixture_ready=true
    echo "Disposable mobile mTLS fixture is ready. Bootstrap: $fixture_bootstrap"
    exit 0
  fi
  if ! kill -0 "$fixture_launcher" 2>/dev/null; then
    cat "$fixture_directory/fixture.log"
    exit 1
  fi
  sleep 2
done
echo 'Disposable mobile mTLS fixture did not become ready.'
cat "$fixture_directory/fixture.log"
exit 1
