#!/usr/bin/env python3
"""Reject stale Xcode archive defaults before release packaging (#447)."""
import re
import sys
from pathlib import Path


def validate(root):
    version = (root / "VERSION").read_text().strip().removeprefix("v")
    marketing = re.split(r"[-+]", version, maxsplit=1)[0]
    build = (root / "VERSION_CODE").read_text().strip()
    projects = [root / "apps/ios/Naviamp.xcodeproj/project.pbxproj"]
    # The Apple TV app shares the iOS App Store record, so it must ship the same version.
    tvos = root / "apps/tvos/NaviampTV.xcodeproj/project.pbxproj"
    if tvos.exists():
        projects.append(tvos)
    for path in projects:
        project = path.read_text()
        for setting, expected in (("MARKETING_VERSION", marketing), ("CURRENT_PROJECT_VERSION", build)):
            values = re.findall(rf"\b{setting}\s*=\s*([^;]+);", project)
            if len(values) != 2 or any(value.strip().strip('"') != expected for value in values):
                raise ValueError(f"{path.parent.name} Debug/Release {setting} must both equal {expected}; got {values}")


if __name__ == "__main__":
    try:
        validate(Path(__file__).resolve().parents[1])
    except (ValueError, OSError) as error:
        sys.exit(str(error))
    print("Xcode archive defaults match VERSION and VERSION_CODE")
