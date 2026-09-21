#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="$ROOT_DIR/build/linux-animation-probe"
TRIALS="${NAVIAMP_PROBE_TRIALS:-3}"

if [[ "$(uname -s)" != "Linux" ]]; then
    echo "measure-linux.sh requires Linux." >&2
    exit 1
fi
if [[ -z "${DISPLAY:-}" ]]; then
    echo "A visible X11 display is required; DISPLAY is unset." >&2
    exit 1
fi
if ! [[ "$TRIALS" =~ ^[1-9][0-9]*$ ]]; then
    echo "NAVIAMP_PROBE_TRIALS must be a positive integer." >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"
{
    date --iso-8601=seconds
    uname -a
    java -version 2>&1
    printf 'DISPLAY=%s\n' "$DISPLAY"
    if command -v glxinfo >/dev/null 2>&1; then glxinfo -B; fi
} > "$OUTPUT_DIR/environment.txt"

cd "$ROOT_DIR"
./gradlew \
    -Pkotlin.native.enableKlibsCrossCompilation=false \
    -Pnaviamp.bass.platform=linux-x64 \
    :platforms:desktop:copyDesktopRasterX11Resources \
    :core:ui:jvmTestClasses \
    --console=plain

for trial in $(seq 1 "$TRIALS"); do
    NAVIAMP_PROBE_INTEGRATED=true \
    NAVIAMP_PROBE_VERIFY=true \
    ./gradlew :core:ui:playerAnimationProbe --console=plain \
        | tee "$OUTPUT_DIR/trial-$trial.txt"
done

printf 'Linux animation probe completed: %s trial(s).\n' "$TRIALS"
printf 'Results: %s\n' "$OUTPUT_DIR"
