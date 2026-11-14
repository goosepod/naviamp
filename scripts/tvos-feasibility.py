#!/usr/bin/env python3
"""Offline evidence for #590. Inventories supplied inputs; never installs or executes them."""
import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import tomllib
import zipfile

REQUIRED_BASS = ("bass", "bassmix", "bassflac", "bassopus", "bassmidi", "basswv",
                 "bassdsd", "basswebm", "basshls", "bassape", "bass_mpc")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def inspect_bass(path):
    frameworks = {}
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(set(names)) != len(names):
            raise ValueError("Duplicate archive members")
        for name in names:
            if name.count("/") != 1 or not name.endswith(".xcframework/Info.plist"):
                continue
            component = name.split(".xcframework/")[0]
            libraries = plistlib.loads(archive.read(name))["AvailableLibraries"]
            slices = []
            for library in libraries:
                identifier = library["LibraryIdentifier"]
                library_path = library["LibraryPath"]
                binary = f"{component}.xcframework/{identifier}/{library_path}/{component}"
                # ZIP members are read in memory, never extracted or executed.
                if library["SupportedPlatform"] != "tvos":
                    raise ValueError(f"{component}: non-tvOS slice")
                variant = library.get("SupportedPlatformVariant", "device")
                if variant not in ("device", "simulator"):
                    raise ValueError(f"{component}: unexpected platform variant {variant}")
                if "arm64" not in library["SupportedArchitectures"]:
                    raise ValueError(f"{component}: missing arm64 {variant}")
                slices.append({"identifier": identifier, "variant": variant,
                               "architectures": library["SupportedArchitectures"],
                               "binarySha256": digest(archive.read(binary))})
            if {s["variant"] for s in slices} != {"device", "simulator"}:
                raise ValueError(f"{component}: device/simulator pair required")
            frameworks[component] = slices
    if "bass" not in frameworks:
        raise ValueError("BASS core XCFramework missing")
    missing = sorted(set(REQUIRED_BASS) - frameworks.keys())
    return {"archiveSha256": digest(Path(path).read_bytes()), "frameworks": frameworks,
            "missingRequiredComponents": missing, "completePlaybackInventory": not missing,
            "machOAndLinkValidated": False, "playbackValidated": False}


def report(archive, manifest, catalog):
    versions = tomllib.loads(Path(catalog).read_text())["versions"]
    raw = Path(manifest).read_bytes()
    policy = json.loads(raw)
    if policy.get("schema") != 2 or policy.get("mappings") != {}:
        raise ValueError("Unreviewed Compose manifest schema or version substitutions")
    plugin_version = policy["gradlePlugin"]
    return {"bass": inspect_bass(archive), "compose": {
        "manifestSha256": digest(raw), "projectVersion": versions["compose"],
        "manifestPluginVersion": plugin_version,
        "sameComposeLine": versions["compose"].split(".")[:2] == plugin_version.split(".")[:2],
        "resolvedGraphValidated": False},
        "scope": "Metadata inventory only; no dependency, ABI, playback or rendering acceptance"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bass-archive", required=True, type=Path)
    parser.add_argument("--compose-manifest", required=True, type=Path)
    parser.add_argument("--catalog", type=Path, default=Path("gradle/libs.versions.toml"))
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = report(args.bass_archive, args.compose_manifest, args.catalog)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
