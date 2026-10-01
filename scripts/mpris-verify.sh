#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for tool in dbus-run-session playerctl; do
    command -v "$tool" >/dev/null || { echo "Required MPRIS verification tool is missing: $tool" >&2; exit 1; }
done
cd "$ROOT_DIR"
# A private real bus avoids colliding with an already-running user's player.
# Integration mode always reruns the test task, while keeping compilation caches.
NAVIAMP_MPRIS_INTEGRATION=true NAVIAMP_PLAYERCTL="$(command -v playerctl)" \
    dbus-run-session -- ./gradlew -Pkotlin.native.enableKlibsCrossCompilation=false \
    -Pnaviamp.bass.platform=linux-x64 :platforms:desktop:desktopTest \
    --tests 'app.naviamp.desktop.platform.DesktopMprisServiceTest' \
    --console=plain
