#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-/Users/jbmcmichael/Library/Android/sdk}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
DHU="${DHU:-$ANDROID_HOME/extras/google/auto/desktop-head-unit}"
DEVICE="${DEVICE:-}"
APP_ID="${APP_ID:-app.naviamp.android}"
GEARHEAD_ID="${GEARHEAD_ID:-com.google.android.projection.gearhead}"
GEARHEAD_SERVICE="${GEARHEAD_SERVICE:-com.google.android.projection.gearhead/.companion.DeveloperHeadUnitNetworkService}"
GEARHEAD_SERVICE_CLASS="${GEARHEAD_SERVICE#*/}"

adb_cmd=("$ADB")
if [[ -n "$DEVICE" ]]; then
    adb_cmd+=("-s" "$DEVICE")
fi

usage() {
    cat <<EOF
Usage: $(basename "$0") [run|start|install|logs|status|stop]

Commands:
  run      Build/install debug APK, start Android Auto head unit server, forward 5277, launch DHU.
  start    Start Android Auto head unit server, forward 5277, launch DHU.
  install  Build and install the debug APK.
  logs     Follow Naviamp Android Auto logs.
  status   Show connected device and Android Auto/Naviamp package state.
  stop     Stop DHU-facing Gearhead server and remove port forward.

Environment:
  ANDROID_HOME  Android SDK root. Defaults to $ANDROID_HOME
  DEVICE        Optional adb serial, for example emulator-5556.
  ADB           Optional adb path.
  DHU           Optional desktop-head-unit path.
EOF
}

require_tooling() {
    if [[ ! -x "$ADB" ]]; then
        echo "ADB not found or not executable: $ADB" >&2
        exit 1
    fi
    if [[ ! -x "$DHU" ]]; then
        echo "Desktop Head Unit not found or not executable: $DHU" >&2
        echo "Install Android Auto Desktop Head Unit through Android Studio SDK Manager." >&2
        exit 1
    fi
}

wait_for_device() {
    "${adb_cmd[@]}" wait-for-device
}

install_debug() {
    (
        cd "$ROOT_DIR"
        ANDROID_HOME="$ANDROID_HOME" ./gradlew --configure-on-demand :apps:android:installDebug
    )
}

start_head_unit_server() {
    wait_for_device
    if ! "${adb_cmd[@]}" shell dumpsys package "$GEARHEAD_ID" | grep -F "$GEARHEAD_SERVICE_CLASS" >/dev/null; then
        echo "Android Auto DHU service was not listed by package inspection:" >&2
        echo "  $GEARHEAD_SERVICE" >&2
        echo >&2
        echo "If you already started the Android Auto head unit server on the phone, continuing with port forwarding." >&2
        echo "If this is a stub-only emulator, DHU will fail to connect." >&2
    else
        "${adb_cmd[@]}" shell am force-stop "$GEARHEAD_ID" >/dev/null 2>&1 || true
        "${adb_cmd[@]}" shell am start-foreground-service -n "$GEARHEAD_SERVICE" >/dev/null 2>&1 ||
            "${adb_cmd[@]}" shell am startservice -n "$GEARHEAD_SERVICE" >/dev/null
    fi
    "${adb_cmd[@]}" forward tcp:5277 tcp:5277 >/dev/null
}

launch_dhu() {
    exec "$DHU"
}

show_status() {
    "$ADB" devices -l
    wait_for_device
    echo
    echo "Device:"
    "${adb_cmd[@]}" shell getprop ro.product.model | tr -d '\r'
    echo
    echo "Android Auto package:"
    "${adb_cmd[@]}" shell pm path "$GEARHEAD_ID" | tr -d '\r' || true
    "${adb_cmd[@]}" shell dumpsys package "$GEARHEAD_ID" | grep -E "versionName=|codePath=" | head -2 | tr -d '\r' || true
    if "${adb_cmd[@]}" shell dumpsys package "$GEARHEAD_ID" | grep -F "$GEARHEAD_SERVICE_CLASS" >/dev/null; then
        echo "DHU service: available"
    else
        echo "DHU service: missing"
    fi
    echo
    echo "Naviamp package:"
    "${adb_cmd[@]}" shell pm path "$APP_ID" | tr -d '\r' || true
    echo
    echo "Naviamp media browser service:"
    "${adb_cmd[@]}" shell dumpsys package "$APP_ID" | grep -F "android.media.browse.MediaBrowserService" || true
}

follow_logs() {
    wait_for_device
    "${adb_cmd[@]}" logcat -v time -s NaviampAutoCommand NaviampPlayback NaviampCache AndroidRuntime
}

stop_head_unit_server() {
    wait_for_device
    "${adb_cmd[@]}" forward --remove tcp:5277 >/dev/null 2>&1 || true
    "${adb_cmd[@]}" shell am force-stop "$GEARHEAD_ID" >/dev/null 2>&1 || true
}

command="${1:-run}"
case "$command" in
    run)
        require_tooling
        install_debug
        start_head_unit_server
        launch_dhu
        ;;
    start)
        require_tooling
        start_head_unit_server
        launch_dhu
        ;;
    install)
        require_tooling
        install_debug
        ;;
    logs)
        require_tooling
        follow_logs
        ;;
    status)
        require_tooling
        show_status
        ;;
    stop)
        require_tooling
        stop_head_unit_server
        ;;
    help|-h|--help)
        usage
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac
