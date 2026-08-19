#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="$ROOT_DIR/build/linux-test"
APP_PATH="$ROOT_DIR/build/local-test/Naviamp/bin/Naviamp"
SMOKE_SECONDS="${LINUX_SMOKE_SECONDS:-15}"

if [[ "$(uname -s)" != "Linux" ]]; then
    echo "linux-verify must run on Linux." >&2
    exit 1
fi

for tool in java timeout xvfb-run; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "Required Linux verification tool is missing: $tool" >&2
        exit 1
    fi
done

java_major="$(java -version 2>&1 | awk -F'[\".]' 'NR == 1 { print $2 }')"
if [[ ! "$java_major" =~ ^[0-9]+$ ]] || (( java_major < 21 )); then
    echo "Linux packaging requires JDK 21 or newer; found: $(java -version 2>&1 | head -n 1)" >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"
printf '%s\n' 'pcm.!default { type null }' > "$OUTPUT_DIR/asound.conf"

(
    cd "$ROOT_DIR"
    ALSA_CONFIG_PATH="$OUTPUT_DIR/asound.conf" ./gradlew \
        -Pkotlin.native.enableKlibsCrossCompilation=false \
        -Pnaviamp.bass.platform=linux-x64 \
        desktopTest \
        :apps:desktop:stageLocalTestApp
)

if [[ ! -x "$APP_PATH" ]]; then
    echo "Staged Linux launcher is missing or not executable: $APP_PATH" >&2
    exit 1
fi

set +e
ALSA_CONFIG_PATH="$OUTPUT_DIR/asound.conf" \
    timeout --signal=TERM --kill-after=5s "${SMOKE_SECONDS}s" \
    xvfb-run -a -s '-screen 0 1280x800x24' \
    "$APP_PATH" > "$OUTPUT_DIR/app-smoke.log" 2>&1
smoke_status=$?
set -e

if [[ "$smoke_status" -ne 124 ]]; then
    echo "Linux app exited before the ${SMOKE_SECONDS}-second smoke window (status $smoke_status)." >&2
    echo "Startup log: $OUTPUT_DIR/app-smoke.log" >&2
    exit 1
fi

printf 'Linux verification passed: native tests, staged app, and %s-second launch smoke test.\n' "$SMOKE_SECONDS"
printf 'Startup log: %s\n' "$OUTPUT_DIR/app-smoke.log"
