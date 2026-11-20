#!/usr/bin/env python3
"""Exercise public tvOS BASS in a disposable simulator app; always remove the owned simulator."""
import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import shutil
import signal
import subprocess
import time
import uuid
import wave
import zipfile

EXPECTED_ARCHIVE = "8f3bef0a67c20fdf1b65a7b6164b25bfb3a75e47e8bd5f1094eb0fad1ac3bbe8"
CHECKS = ("audioSession", "initialized", "flacRegistered", "opusRegistered", "streamCreated",
          "volume", "endSyncInstalled", "play", "positionAdvanced", "pause", "pausedPositionStable",
          "seek", "seekPosition", "resume", "endSyncDelivered", "streamFreed", "freed",
          "networkConfigured", "networkValuesReadBack", "equalizerApplied", "waveformRmsRead")


def native_checks_pass(result):
    # BASS uses a C int BOOL; NSNumber serializes those successes as JSON 1, not true.
    return all(type(result.get(key)) in (bool, int) and result[key] == 1 for key in CHECKS)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--header-directory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if hashlib.sha256(args.archive.read_bytes()).hexdigest() != EXPECTED_ARCHIVE:
        parser.error("Inspect changed archive inventory/ABI before changing the pinned hash")
    if not (args.header_directory / "bass.h").is_file():
        parser.error("Supply the existing public BASS header directory")
    if args.output.exists():
        parser.error("Choose a fresh evidence directory")
    out = args.output.resolve()
    out.mkdir(parents=True)
    commands = []

    def run(*command, check=True, timeout=180):
        result = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
        commands.append(dict(command=command, returncode=result.returncode,
                             stdout=result.stdout, stderr=result.stderr))
        (out / "commands.json").write_text(json.dumps(commands, indent=2) + "\n")
        if check and result.returncode:
            raise RuntimeError(f"Command failed: {command}; see commands.json")
        return result.stdout.strip()

    app = out / "BassProbe.app"
    app.mkdir()
    identifier = "app.naviamp.feasibility.bassprobe"
    (app / "Info.plist").write_bytes(plistlib.dumps(dict(
        CFBundleIdentifier=identifier, CFBundleExecutable="BassProbe", CFBundleName="BassProbe",
        CFBundleVersion="1", CFBundleShortVersionString="1.0", CFBundlePackageType="APPL",
        MinimumOSVersion="17.0", UIDeviceFamily=[3], CFBundleSupportedPlatforms=["AppleTVSimulator"],
        LSRequiresIPhoneOS=True, UILaunchScreen={},
        UIApplicationSceneManifest=dict(UIApplicationSupportsMultipleScenes=False,
            UISceneConfigurations={"UIWindowSceneSessionRoleApplication": [dict(
                UISceneConfigurationName="Probe", UISceneDelegateClassName="ProbeSceneDelegate")]}),
    )))
    frameworks = app / "Frameworks"
    frameworks.mkdir()
    with zipfile.ZipFile(args.archive) as archive:
        for component in ("bass", "bassflac", "bassopus"):
            prefix = f"{component}.xcframework/tvos-arm64_x86_64-simulator/{component}.framework/"
            target = frameworks / f"{component}.framework"
            target.mkdir()
            # Only the binary and plist are needed; do not retain the vendor's old signature.
            for name in (component, "Info.plist"):
                (target / name).write_bytes(archive.read(prefix + name))
            run("xcrun", "vtool", "-arch", "arm64", "-show-build", str(target / component))
            run("codesign", "--force", "--sign", "-", str(target))
    with wave.open(str(app / "fixture.wav"), "wb") as wav:
        wav.setparams((2, 2, 48000, 0, "NONE", "not compressed"))
        wav.writeframes(b"\0\0\0\0" * 48000 * 3)
    sdk = run("xcrun", "--sdk", "appletvsimulator", "--show-sdk-path")
    run("xcrun", "--sdk", "appletvsimulator", "clang", "-fobjc-arc", "-target",
        "arm64-apple-tvos17.0-simulator", "-isysroot", sdk,
        "-I" + str(args.header_directory.resolve()), str(Path(__file__).with_name("tvos-bass-runtime-probe.m")),
        "-F" + str(frameworks), "-framework", "bass", "-framework", "UIKit", "-framework",
        "AVFoundation", "-framework", "AudioToolbox", "-Wl,-rpath,@executable_path/Frameworks",
        "-o", str(app / "BassProbe"))
    run("codesign", "--force", "--sign", "-", str(app))
    runtimes = json.loads(run("xcrun", "simctl", "list", "runtimes", "--json"))["runtimes"]
    runtime = next(r for r in reversed(runtimes) if r["isAvailable"] and "tvOS" in r["name"])
    types = json.loads(run("xcrun", "simctl", "list", "devicetypes", "--json"))["devicetypes"]
    runtime_parts = [int(part) for part in runtime["version"].split(".")]
    runtime_number = sum(part << shift for part, shift in zip(runtime_parts, (16, 8, 0)))
    device_type = next(t for t in types if t["productFamily"] == "Apple TV" and
                       t["minRuntimeVersion"] <= runtime_number <= t["maxRuntimeVersion"])
    device = None
    cleanup = {}
    result = {}
    def interrupted(signum, frame):
        raise InterruptedError("tvOS native probe interrupted")
    signal.signal(signal.SIGTERM, interrupted)
    try:
        device = run("xcrun", "simctl", "create", "Naviamp 590 BASS " + uuid.uuid4().hex[:8],
                     device_type["identifier"], runtime["identifier"])
        uuid.UUID(device)
        (out / "device.json").write_text(json.dumps(dict(device=device, runtime=runtime, type=device_type), indent=2))
        run("xcrun", "simctl", "boot", device)
        run("xcrun", "simctl", "bootstatus", device, "-b", timeout=300)
        run("xcrun", "simctl", "install", device, str(app))
        run("xcrun", "simctl", "launch", device, identifier)
        container = Path(run("xcrun", "simctl", "get_app_container", device, identifier, "data"))
        evidence = container / "Library/Caches/result.json"
        deadline = time.monotonic() + 30
        while not evidence.exists() and time.monotonic() < deadline:
            time.sleep(0.25)
        if not evidence.exists():
            run("xcrun", "simctl", "spawn", device, "log", "show", "--last", "2m", "--style", "compact",
                "--predicate", 'process == "BassProbe"', check=False)
            reports = Path.home() / "Library/Logs/DiagnosticReports"
            for report in reports.glob("BassProbe*.ips"):
                if device in report.read_text():
                    shutil.copy2(report, out / report.name)
        shutil.copy2(evidence, out / "native-result.json")
        result = json.loads((out / "native-result.json").read_text())
        result["passed"] = native_checks_pass(result)
    finally:
        if device:
            for operation in ("shutdown", "delete"):
                before = len(commands)
                run("xcrun", "simctl", operation, device, check=False)
                cleanup[operation] = commands[before]["returncode"]
        (out / "summary.json").write_text(json.dumps(dict(result=result, cleanup=cleanup), indent=2) + "\n")
    if not result.get("passed") or any(cleanup.values()):
        raise SystemExit(1)
    print("tvOS direct BASS playback controls passed; acoustic output not verified; owned simulator removed")


if __name__ == "__main__":
    main()
