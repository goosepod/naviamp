#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
device=${1:?Supply a booted iOS simulator UDID}
./gradlew -Pnaviamp.animationProbe=true :core:ui:linkDebugFrameworkIosSimulatorArm64 --console=plain
probe_dir=build/animation-probe-ios
mkdir -p "$probe_dir/NaviampAnimationProbe.app"
cp scripts/animation-probe/Info.plist "$probe_dir/NaviampAnimationProbe.app/Info.plist"
xcrun --sdk iphonesimulator swiftc -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -target arm64-apple-ios17.2-simulator -parse-as-library -module-name NaviampAnimationProbeApp \
  -F core/ui/build/bin/iosSimulatorArm64/debugFramework -framework NaviampAnimationProbe \
  scripts/animation-probe/IosProbe.swift -o "$probe_dir/NaviampAnimationProbe.app/NaviampAnimationProbe"
xcrun simctl install "$device" "$probe_dir/NaviampAnimationProbe.app"
xcrun simctl launch --terminate-running-process --console "$device" app.naviamp.animation-probe
