#!/usr/bin/env python3
"""Verify locally signed public output against the accepted repository identity."""
import argparse
import base64
import configparser
import json
import subprocess
from pathlib import Path
from prepare import APP_ID, PACKAGING, release_source


def verify_descriptors(public, config, public_key):
    for name, section in (("Naviamp.flatpakref", "Flatpak Ref"), ("naviamp.flatpakrepo", "Flatpak Repo")):
        descriptor = configparser.ConfigParser(interpolation=None)
        descriptor.read(public / name)
        values = descriptor[section]
        if values["Url"] != config["url"] + "repo/":
            raise ValueError("The installer points to a different update remote")
        if base64.b64decode(values["GPGKey"], validate=True) != public_key:
            raise ValueError("The installer signing key differs from the pinned identity")
        if section == "Flatpak Ref" and (values["Name"] != APP_ID or values["Branch"] != "stable"):
            raise ValueError("The installer application/branch differs from the stable contract")


def verify(public, source, release):
    version, _, url, digest = release_source(release)
    config = json.loads((PACKAGING / "repository.json").read_text())
    provenance = json.loads(source.read_text())
    if (provenance.get("application_version"), provenance.get("source_url"), provenance.get("source_sha256"),
        provenance.get("signing_fingerprint")) != (version, url, digest, config["signing_fingerprint"]):
        raise ValueError("Published provenance differs from the canonical release/signing identity")
    allowed = {"repo", "naviamp.asc", "Naviamp.flatpakref", "naviamp.flatpakrepo", ".nojekyll", "index.html"}
    if {p.name for p in public.iterdir()} != allowed:
        raise ValueError("Unexpected files in the public publication root")
    public_key = subprocess.run(["gpg", "--dearmor"], input=(PACKAGING / "naviamp.asc").read_bytes(),
                                check=True, capture_output=True).stdout
    provided_key = subprocess.run(["gpg", "--dearmor"], input=(public / "naviamp.asc").read_bytes(),
                                  check=True, capture_output=True).stdout
    if public_key != provided_key:
        raise ValueError("The published key differs from accepted source")
    verify_descriptors(public, config, public_key)
    if any(p.is_symlink() for p in public.rglob("*")):
        raise ValueError("Publication must contain ordinary files, not symlinks")
    if sum(p.stat().st_size for p in public.rglob("*") if p.is_file()) > 900 * 1024 * 1024:
        raise ValueError("Publication exceeds the Pages size budget")
    print(f"PUBLIC_FILES_OK version={version} fingerprint={config['signing_fingerprint']}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("public", "source", "release-json"):
        parser.add_argument("--" + option, required=True, type=Path)
    args = parser.parse_args()
    verify(args.public, args.source, json.loads(args.release_json.read_text()))
