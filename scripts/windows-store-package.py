#!/usr/bin/env python3
"""Wrap the pinned public Windows release without rebuilding application code.

Uses only Python's standard library. MakeAppx on Windows creates/validates the MSIX;
this tool independently checks the input, manifest and every packaged payload byte.
"""

import argparse
import hashlib
import json
import shutil
import stat
import struct
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parents[1]
METADATA = ROOT / "apps/desktop/packaging/windows-store"
NS = {
    "p": "http://schemas.microsoft.com/appx/manifest/foundation/windows10",
    "uap": "http://schemas.microsoft.com/appx/manifest/uap/windows10",
    "uap10": "http://schemas.microsoft.com/appx/manifest/uap/windows10/10",
    "rescap": "http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities",
}
REQUIRED_PAYLOAD = (
    "Naviamp/Naviamp.exe",
    "Naviamp/app/Naviamp.cfg",
    "Naviamp/runtime/bin/server/jvm.dll",
    "Naviamp/app/resources/playback/bass/windows-x64/bass.dll",
    "Naviamp/app/resources/playback/bass/windows-x64/naviamp_bass.dll",
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def release():
    return json.loads((METADATA / "release.json").read_text(encoding="utf-8"))


def checked_entries(archive):
    """Reject ambiguous or unsafe names before extracting any file on Windows."""
    entries = {}
    seen = set()
    for entry in archive.infolist():
        name = entry.filename
        parts = PurePosixPath(name).parts
        require(entry.orig_filename == name and parts and parts[0] == "Naviamp" and ".." not in parts
                and "\\" not in name and ":" not in name
                and all(part and not part.endswith((".", " ")) for part in parts),
                f"Unsafe release entry: {name}")
        require(not stat.S_ISLNK(entry.external_attr >> 16), f"Symlink in release: {name}")
        normalized = name.rstrip("/").casefold()
        require(normalized not in seen, f"Duplicate release entry: {name}")
        seen.add(normalized)
        if not entry.is_dir():
            entries[name] = entry
    require(all(name in entries for name in REQUIRED_PAYLOAD), "Release is missing launcher, runtime or BASS")
    cfg = archive.read(entries["Naviamp/app/Naviamp.cfg"])
    require(b"naviamp.developmentDataProfile=true" not in cfg, "Development data profile in release")
    for name in (REQUIRED_PAYLOAD[0], REQUIRED_PAYLOAD[2], REQUIRED_PAYLOAD[3], REQUIRED_PAYLOAD[4]):
        data = archive.read(entries[name])
        require(data[:2] == b"MZ" and len(data) >= 64, f"Invalid PE: {name}")
        offset = struct.unpack_from("<I", data, 60)[0]
        require(offset + 6 <= len(data) and data[offset:offset + 4] == b"PE\0\0"
                and struct.unpack_from("<H", data, offset + 4)[0] == 0x8664,
                f"Expected x64 PE: {name}")
    return entries


def checked_source(path, config):
    require(file_digest(path) == config["sha256"], "Release ZIP SHA-256 does not match the published asset")
    return zipfile.ZipFile(path)


def check_manifest(data, config):
    manifest = ET.fromstring(data)
    identity = manifest.find("p:Identity", NS)
    expected = {"Name": config["identity"], "Publisher": config["publisher"],
                "Version": config["msix_version"], "ProcessorArchitecture": "x64"}
    require(identity is not None and identity.attrib == expected, "Package identity does not match release lock")
    version = [int(part) for part in config["msix_version"].split(".")]
    require(len(version) == 4 and 0 < version[0] <= 65535 and version[3] == 0
            and all(0 <= part <= 65535 for part in version), "Invalid Store package version")
    require(manifest.findtext("p:Properties/p:DisplayName", namespaces=NS) == "Naviamp", "Incorrect display name")
    require(manifest.findtext("p:Properties/p:PublisherDisplayName", namespaces=NS)
            == config["publisher_display_name"], "Incorrect publisher display name")
    applications = manifest.findall("p:Applications/p:Application", NS)
    require(len(applications) == 1, "Expected exactly one application")
    app = applications[0]
    require(app.get("Id") == "Naviamp" and app.get("Executable") == "Naviamp\\Naviamp.exe"
            and app.get("{" + NS["uap10"] + "}RuntimeBehavior") == "packagedClassicApp"
            and app.get("{" + NS["uap10"] + "}TrustLevel") == "mediumIL", "Invalid desktop entry point")
    capabilities = manifest.find("p:Capabilities", NS)
    require(capabilities is not None and len(capabilities) == 1
            and capabilities[0].tag == "{" + NS["rescap"] + "}Capability"
            and capabilities[0].get("Name") == "runFullTrust", "Unexpected package capabilities")
    return manifest


def prepare(source, output):
    config = release()
    require(not output.exists(), f"Output already exists; choose a fresh directory: {output}")
    with checked_source(source, config) as archive:
        entries = checked_entries(archive)
        manifest = (METADATA / "AppxManifest.xml").read_bytes()
        check_manifest(manifest, config)
        output.mkdir(parents=True)
        for name, entry in entries.items():
            destination = output / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(entry) as src, destination.open("wb") as dst:
                shutil.copyfileobj(src, dst)
        (output / "AppxManifest.xml").write_bytes(manifest)
        shutil.copytree(METADATA / "Assets", output / "Assets")
    print(f"Prepared {len(entries)} unchanged release files in {output}")


def verify(source, package, evidence):
    config = release()
    with checked_source(source, config) as original, zipfile.ZipFile(package) as msix:
        entries = checked_entries(original)
        names = msix.namelist()
        require(len(names) == len(set(n.casefold() for n in names)), "Duplicate package entries")
        check_manifest(msix.read("AppxManifest.xml"), config)
        require(msix.read("AppxManifest.xml") == (METADATA / "AppxManifest.xml").read_bytes(), "Manifest changed")
        wrappers = {"AppxManifest.xml", "AppxBlockMap.xml", "[Content_Types].xml"}
        for asset in (METADATA / "Assets").glob("*.png"):
            name = f"Assets/{asset.name}"
            require(msix.read(name) == asset.read_bytes(), f"Brand asset changed: {name}")
            wrappers.add(name)
        require(set(names) == set(entries) | wrappers, "Unexpected or missing package files")
        payload = {}
        for name, entry in entries.items():
            expected = digest(original.read(entry))
            require(digest(msix.read(name)) == expected, f"Released application file changed: {name}")
            payload[name] = expected
        report = {"release": config, "package": package.name, "package_sha256": file_digest(package),
                  "payload_file_count": len(payload), "payload_sha256": payload,
                  "makeappx_semantic_validation": "Required by build-windows-store.ps1; see build log",
                  "interactive_windows_acceptance": "pending", "store_certification": "pending"}
        evidence.parent.mkdir(parents=True, exist_ok=True)
        evidence.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Verified all {len(payload)} released application files byte-for-byte; evidence: {evidence}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    download = sub.add_parser("download")
    download.add_argument("destination", type=Path)
    prep = sub.add_parser("prepare")
    prep.add_argument("source", type=Path)
    prep.add_argument("output", type=Path)
    check = sub.add_parser("verify")
    check.add_argument("source", type=Path)
    check.add_argument("package", type=Path)
    check.add_argument("evidence", type=Path)
    args = parser.parse_args()
    if args.command == "download":
        config = release()
        args.destination.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(config["url"], args.destination)
        with checked_source(args.destination, config):
            pass
        print(f"Downloaded and verified {config['asset']}")
    elif args.command == "prepare":
        prepare(args.source, args.output)
    else:
        verify(args.source, args.package, args.evidence)


if __name__ == "__main__":
    main()
