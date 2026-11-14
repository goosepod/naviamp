#!/usr/bin/env python3
"""Link-only BASS core tvOS probe on a Mac; never executes the resulting binaries."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import zipfile

EXPECTED_ARCHIVE = "8f3bef0a67c20fdf1b65a7b6164b25bfb3a75e47e8bd5f1094eb0fad1ac3bbe8"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--archive", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    if hashlib.sha256(args.archive.read_bytes()).hexdigest() != EXPECTED_ARCHIVE:
        p.error("Archive changed; inspect and review its inventory/hash before updating the probe")
    if args.output.exists():
        p.error("Choose a fresh output directory")
    args.output.mkdir(parents=True)
    records = []
    with zipfile.ZipFile(args.archive) as archive:
        for variant, sdk, target, identifier in [
            ("device", "appletvos", "arm64-apple-tvos17.0", "tvos-arm64"),
            ("simulator", "appletvsimulator", "arm64-apple-tvos17.0-simulator", "tvos-arm64_x86_64-simulator"),
        ]:
            root = args.output.resolve() / variant
            framework = root / "bass.framework"
            framework.mkdir(parents=True)
            binary = framework / "bass"
            binary.write_bytes(archive.read(f"bass.xcframework/{identifier}/bass.framework/bass"))
            source = root / "probe.c"
            source.write_text("extern unsigned int BASS_GetVersion(void);\nint main(void) { return BASS_GetVersion() == 0; }\n")
            commands = [["xcrun", "vtool", "-arch", "arm64", "-show-build", str(binary)],
                        ["xcrun", "--sdk", sdk, "clang", "-target", target,
                         "-isysroot", subprocess.check_output(["xcrun", "--sdk", sdk, "--show-sdk-path"], text=True).strip(),
                         str(source), "-F" + str(root), "-framework", "bass",
                         "-framework", "AVFoundation", "-framework", "AudioToolbox", "-o", str(root / "probe")]]
            outputs = []
            for command in commands:
                started = time.monotonic()
                result = subprocess.run(command, capture_output=True, text=True)
                outputs.append(dict(command=command, seconds=time.monotonic() - started,
                                    returncode=result.returncode, stdout=result.stdout, stderr=result.stderr))
            records.append(dict(variant=variant, commands=outputs,
                                platformValidated=("TVOSSIMULATOR" in outputs[0]["stdout"] if variant == "simulator"
                                                   else "LC_VERSION_MIN_TVOS" in outputs[0]["stdout"] or
                                                   "platform TVOS\n" in outputs[0]["stdout"]),
                                linked=outputs[-1]["returncode"] == 0, executed=False))
    (args.output / "result.json").write_text(json.dumps(records, indent=2) + "\n")
    print(json.dumps(records, indent=2))
    if any(not record["linked"] or not record["platformValidated"] or
           record["commands"][0]["returncode"] for record in records):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
