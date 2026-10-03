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
# Hold a session-scoped inhibitor; do not change the desktop's saved power/blanking settings.
INHIBITOR_PID=""
trap '[[ -z "$INHIBITOR_PID" ]] || kill "$INHIBITOR_PID" 2>/dev/null || true' EXIT
if command -v xfce4-screensaver-command >/dev/null 2>&1 && pgrep -f '^(/usr/bin/)?xfce4-screensaver([[:space:]]|$)' >/dev/null 2>&1; then
    xfce4-screensaver-command --inhibit --application-name "Naviamp animation probe" \
        --reason "Measure visible raster pixels and compositor cost" > "$OUTPUT_DIR/screensaver.txt" 2>&1 &
    INHIBITOR_PID=$!
    xfce4-screensaver-command --deactivate
    xfce4-screensaver-command --poke
fi
{
    date --iso-8601=seconds
    uname -a
    java -version 2>&1
    printf 'DISPLAY=%s\n' "$DISPLAY"
    if command -v xrandr >/dev/null 2>&1; then xrandr --current; fi
    if command -v xfconf-query >/dev/null 2>&1; then
        xfconf-query -c xfwm4 -p /general/use_compositing || true
        xfconf-query -c xsettings -p /Xft/DPI || true
    fi
    for condition in /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor /sys/class/power_supply/*/online; do
        [[ ! -r "$condition" ]] || { printf '%s=' "$condition"; cat "$condition"; }
    done
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
    NAVIAMP_PROBE_LIFECYCLE=true \
    NAVIAMP_PROBE_STACKING=true \
    NAVIAMP_RASTER_DIAGNOSTICS=true \
    ./gradlew :core:ui:playerAnimationProbe --console=plain \
        2>&1 | tee "$OUTPUT_DIR/trial-$trial.txt"
done

printf 'Linux animation probe completed: %s trial(s).\n' "$TRIALS"
printf 'Results: %s\n' "$OUTPUT_DIR"
