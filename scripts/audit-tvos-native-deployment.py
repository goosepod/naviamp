#!/usr/bin/env python3
"""Audit ARM64 Mach-O deployment metadata without changing native inputs."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


def version(value):
    if not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", value):
        raise ValueError(f"Invalid OS version: {value}")
    parts = tuple(int(part) for part in value.split("."))
    return parts + (0,) * (3 - len(parts))


def objects(text):
    headers = list(re.finditer(r"(?m)^(?!Archive :)(\S[^\n]*):\n", text))
    result = []
    for index, header in enumerate(headers):
        block = text[header.end():headers[index + 1].start() if index + 1 < len(headers) else len(text)]
        current = re.findall(r"cmd LC_BUILD_VERSION\n\s+cmdsize \d+\n\s+platform (\S+)\n\s+minos (\S+)\n\s+sdk (\S+)", block)
        legacy = re.findall(r"cmd LC_VERSION_MIN_TVOS\n\s+cmdsize \d+\n\s+version (\S+)\n\s+sdk (\S+)", block)
        versions = current + [("3", minimum, sdk) for minimum, sdk in legacy]
        if len(versions) > 1:
            raise ValueError(f"Conflicting deployment commands: {header[1]}")
        entry = dict(object=header[1], platform=None, minimum=None, sdk=None)
        if versions:
            platform, minimum, sdk = versions[0]
            platform = {"TVOS": "3", "TVOSSIMULATOR": "8"}.get(platform, platform)
            entry.update(platform=platform, minimum=minimum, sdk=sdk)
            version(minimum)
        result.append(entry)
    if not result:
        raise ValueError("No native object headers were reported")
    return result


def violations(entries, platform, maximum):
    result = []
    for entry in entries:
        if entry["platform"] != str(platform):
            result.append(dict(**entry, reason="missing or incorrect platform metadata"))
        elif entry["minimum"] is None:
            result.append(dict(**entry, reason="missing minimum OS metadata"))
        elif maximum and version(entry["minimum"]) > version(maximum):
            result.append(dict(**entry, reason=f"minimum exceeds {maximum}"))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, action="append", required=True)
    parser.add_argument("--platform", type=int, choices=(3, 8), required=True,
                        help="Mach-O platform 3 = tvOS device, 8 = tvOS simulator")
    parser.add_argument("--maximum-os")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.maximum_os:
        version(args.maximum_os)
    if args.output.exists():
        parser.error("Choose a fresh evidence directory")
    out = args.output.resolve()
    out.mkdir(parents=True)
    report = dict(passed=False, architecture="arm64", platform=args.platform,
                  maximumOs=args.maximum_os, inputs=[])
    try:
        for index, archive in enumerate(args.archive):
            archive = archive.resolve(strict=True)
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            command = ["xcrun", "otool", "-arch", "arm64", "-l", str(archive)]
            process = subprocess.run(command, capture_output=True, text=True, timeout=120)
            (out / f"archive-{index:02d}.stdout").write_text(process.stdout)
            (out / f"archive-{index:02d}.stderr").write_text(process.stderr)
            if process.returncode:
                raise RuntimeError(f"otool failed for {archive}: {process.returncode}")
            entries = objects(process.stdout)
            rejected = violations(entries, args.platform, args.maximum_os)
            if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
                raise RuntimeError("Native input changed during the audit")
            report["inputs"].append(dict(path=str(archive), sha256=digest, objects=entries,
                                         violations=rejected))
        report["passed"] = not any(item["violations"] for item in report["inputs"])
    finally:
        (out / "summary.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(dict(passed=report["passed"], inputs=len(report["inputs"]),
                         objects=sum(len(item["objects"]) for item in report["inputs"]),
                         violations=sum(len(item["violations"]) for item in report["inputs"])), indent=2))
    if not report["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
