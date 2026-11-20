#!/usr/bin/env python3
"""Retain fresh instrumentation XML under portable names and require the shared runtime case."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

REQUIRED_CLASS = "app.naviamp.ui.VisualizerCompositionClockTest"
REQUIRED_METHOD = "movingClockIsReadableByDrawCallbacksWithoutRecomposingTheParent"


def collect(project, output, started):
    if not math.isfinite(started) or started <= 0:
        raise ValueError("Instrumentation start timestamp must be finite and positive")
    output.mkdir(parents=True, exist_ok=False)
    (output / "junit").mkdir()
    report = {"passed": False, "started": started, "modules": {}, "files": [], "errors": []}
    required = []
    for label, module in (("app", "apps/android"), ("ui", "core/ui")):
        root = project / module / "build"
        paths = sorted((root / "outputs/androidTest-results").rglob("*.xml"))
        if not paths:
            report["errors"].append(f"Missing instrumentation XML: {module}")
        cases = []
        for index, path in enumerate(paths):
            if path.is_symlink():
                report["errors"].append(f"Symlink is not a test report: {path.relative_to(project)}")
                continue
            data = path.read_bytes()
            target = output / "junit" / f"{label}-{index:03}.xml"
            target.write_bytes(data)
            report["files"].append({"source": str(path.relative_to(project)),
                                    "saved": str(target.relative_to(output)),
                                    "sha256": hashlib.sha256(data).hexdigest()})
            if path.stat().st_mtime < started:
                report["errors"].append(f"Stale instrumentation XML: {path.relative_to(project)}")
            try:
                cases.extend(ET.fromstring(data).iter("testcase"))
            except ET.ParseError as error:
                report["errors"].append(f"Invalid instrumentation XML: {path.name}: {error}")
        failed = sum(any(case.find(tag) is not None for tag in ("failure", "error")) for case in cases)
        skipped = sum(case.find("skipped") is not None for case in cases)
        report["modules"][module] = {"cases": len(cases), "failuresOrErrors": failed, "skips": skipped}
        if not cases or failed:
            report["errors"].append(f"Empty or failed instrumentation inventory: {module}")
        if label == "ui":
            required = [case for case in cases if case.get("classname") == REQUIRED_CLASS and
                        case.get("name") == REQUIRED_METHOD]
        html = root / "reports/androidTests"
        if html.is_dir():
            with zipfile.ZipFile(output / f"{label}-html.zip", "x", zipfile.ZIP_DEFLATED) as archive:
                for path in sorted(html.rglob("*")):
                    if path.is_file() and not path.is_symlink():
                        archive.write(path, path.relative_to(html).as_posix())
    if len(required) != 1 or any(required[0].find(tag) is not None for tag in ("failure", "error", "skipped")):
        report["errors"].append("Required shared composition-clock case must execute exactly once and pass")
    report["passed"] = not report["errors"]
    (output / "summary.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def diagnostics(output):
    destination = output / "host-logs"
    destination.mkdir(parents=True, exist_ok=True)
    patterns = ("naviamp-emulator.log", "naviamp-sdkmanager.log", "naviamp-avdmanager.log", "naviamp-dnssd-repeat-*.txt")
    for pattern in patterns:
        for source in Path("/tmp").glob(pattern):
            shutil.copy2(source, destination / source.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--started-file", type=Path)
    parser.add_argument("--diagnostics-only", action="store_true")
    args = parser.parse_args()
    if args.diagnostics_only:
        diagnostics(args.output)
        return
    if not args.started_file:
        parser.error("Supply the timestamp written before instrumentation starts")
    started = float(args.started_file.read_text())
    report = collect(args.project.resolve(), args.output.resolve(), started)
    print(json.dumps(report, indent=2))
    if not report["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
