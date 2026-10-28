#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build
umask 077
./gradlew :core:testkit:prepareMobileMutualTlsFixture -Pkotlin.native.enableKlibsCrossCompilation=false --console=plain
"$JAVA_HOME/bin/java" @build/mobile-mtls-fixture.args >build/mobile-mtls-fixture.log 2>&1 &
fixture_launcher=$!
for _ in $(seq 1 300); do
  if curl --max-time 2 --silent --fail --output build/mobile-mtls-fixture.bootstrap http://127.0.0.1:59974/fixture; then
    echo 'Disposable mobile mTLS fixture is ready.'
    exit 0
  fi
  if ! kill -0 "$fixture_launcher" 2>/dev/null; then
    cat build/mobile-mtls-fixture.log
    exit 1
  fi
  sleep 2
done
echo 'Disposable mobile mTLS fixture did not become ready.'
cat build/mobile-mtls-fixture.log
exit 1
