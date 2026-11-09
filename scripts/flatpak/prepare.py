#!/usr/bin/env python3
"""Package an immutable, published Naviamp Linux image; never relabel development code."""
import argparse
import json
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

APP_ID = "io.github.goosepod.Naviamp"
ROOT = Path(__file__).resolve().parents[2]
PACKAGING = ROOT / "packaging/flatpak"


def release_source(release):
    tag = release.get("tag_name", "")
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag):
        raise ValueError("A stable public release tag is required")
    if release.get("draft") or release.get("prerelease"):
        raise ValueError("Draft and prerelease applications cannot enter the stable remote")
    name = f"Naviamp-{tag}-linux-x64-release.zip"
    assets = [a for a in release.get("assets", []) if a.get("name") == name]
    if len(assets) != 1:
        raise ValueError("Exactly one official Linux image is required")
    asset = assets[0]
    expected_url = f"https://github.com/goosepod/naviamp/releases/download/{tag}/{name}"
    if asset.get("browser_download_url") != expected_url:
        raise ValueError("The image must come from the canonical Naviamp release")
    digest = asset.get("digest", "")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        raise ValueError("The published image must have a SHA-256 digest")
    date = release.get("published_at", "")[:10]
    if not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", date):
        raise ValueError("The public release date is required")
    return tag[1:], date, expected_url, digest[7:]


def prepare(release, output):
    version, date, url, digest = release_source(release)
    output.mkdir(parents=True, exist_ok=True)
    for name in ("naviamp", f"{APP_ID}.desktop", "libsecret-COPYING", "THIRD-PARTY.txt"):
        shutil.copyfile(PACKAGING / name, output / name)
    shutil.copyfile(ROOT / "LICENSE", output / "LICENSE")
    shutil.copyfile(ROOT / "platforms/desktop/src/desktopMain/resources/icons/naviamp.png",
                    output / "naviamp.png")
    metadata = ET.parse(PACKAGING / f"{APP_ID}.metainfo.xml")
    ET.SubElement(metadata.getroot().find("releases"), "release", version=version, date=date)
    metadata.write(output / f"{APP_ID}.metainfo.xml", encoding="UTF-8", xml_declaration=True)
    provenance = {"application_version": version, "source_url": url, "sha256": digest,
                  "source_code": f"https://github.com/goosepod/naviamp/tree/v{version}",
                  "runtime": "org.freedesktop.Platform//25.08"}
    (output / "source.json").write_text(json.dumps(provenance, indent=2) + "\n")
    manifest = {
        "app-id": APP_ID, "branch": "stable", "runtime": "org.freedesktop.Platform",
        "runtime-version": "25.08", "sdk": "org.freedesktop.Sdk", "command": "naviamp",
        "finish-args": ["--share=network", "--share=ipc", "--socket=x11",
                        "--socket=pulseaudio", "--device=dri",
                        "--talk-name=org.freedesktop.secrets",
                        "--own-name=org.mpris.MediaPlayer2.naviamp",
                        "--filesystem=xdg-download", "--persist=.local/share/naviamp",
                        "--env=GTK_USE_PORTAL=1"],
        "modules": [
            {"name": "secret-tool", "buildsystem": "meson",
             "config-opts": ["-Dmanpage=false", "-Dvapi=false", "-Dgtk_doc=false",
                             "-Dintrospection=false", "-Dbash_completion=disabled"],
             "cleanup": ["/include", "/lib", "/lib64", "/share"],
             "sources": [{"type": "archive",
                          "url": "https://download.gnome.org/sources/libsecret/0.21/libsecret-0.21.7.tar.xz",
                          "sha256": "6b452e4750590a2b5617adc40026f28d2f4903de15f1250e1d1c40bfd68ed55e"}]},
            {"name": "naviamp", "buildsystem": "simple",
             "build-options": {"strip": False, "no-debuginfo": True},
             "build-commands": [
                 "install -d /app/lib/naviamp",
                 "cp -a Naviamp/. /app/lib/naviamp/",
                 "chmod +x /app/lib/naviamp/bin/Naviamp /app/lib/naviamp/lib/runtime/lib/jspawnhelper",
                 "install -Dm755 naviamp /app/bin/naviamp",
                 f"install -Dm644 {APP_ID}.desktop /app/share/applications/{APP_ID}.desktop",
                 f"install -Dm644 naviamp.png /app/share/icons/hicolor/512x512/apps/{APP_ID}.png",
                 f"install -Dm644 {APP_ID}.metainfo.xml /app/share/metainfo/{APP_ID}.metainfo.xml",
                 f"install -Dm644 LICENSE /app/share/licenses/{APP_ID}/LICENSE",
                 f"install -Dm644 libsecret-COPYING /app/share/licenses/{APP_ID}/libsecret-COPYING",
                 f"install -Dm644 THIRD-PARTY.txt /app/share/licenses/{APP_ID}/THIRD-PARTY.txt",
                 f"install -Dm644 source.json /app/share/licenses/{APP_ID}/source.json"],
             "sources": [{"type": "archive", "url": url, "sha256": digest, "strip-components": 0}]
                        + [{"type": "file", "path": name} for name in
                           ("naviamp", f"{APP_ID}.desktop", f"{APP_ID}.metainfo.xml",
                            "naviamp.png", "LICENSE", "libsecret-COPYING", "THIRD-PARTY.txt", "source.json")]}
        ]}
    (output / f"{APP_ID}.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return provenance


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-json", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(prepare(json.loads(args.release_json.read_text()), args.output)))
