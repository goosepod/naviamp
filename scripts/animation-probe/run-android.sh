#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
serial=${1:?Supply an authorized adb device serial}
adb_bin=${ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}
./gradlew -Pnaviamp.animationProbe=true :core:ui:assembleDebugAndroidTest --console=plain
"$adb_bin" -s "$serial" install -r core/ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk
# Filter this process's tagged results; do not clear unrelated device logs.
"$adb_bin" -s "$serial" shell am instrument -w -e class app.naviamp.ui.AndroidAnimationProbeTest app.naviamp.ui.test/androidx.test.runner.AndroidJUnitRunner
"$adb_bin" -s "$serial" logcat -d -s NaviampAnimationProbe:I '*:S'
