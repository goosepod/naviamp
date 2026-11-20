#!/usr/bin/env python3
"""Seal/verify the reviewed #590 local repositories; not a source-build or publishing service.

Consumers must obtain the manifest SHA-256 from reviewed source, separately from the artifact.
This tool deliberately never downloads, extracts, mutates, or selects a Gradle repository.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re


PREFIX = "app.naviamp.tvosprobe."
TARGETS = {"tvosarm64": "tvos_arm64", "tvossimulatorarm64": "tvos_simulator_arm64"}


def expected_coordinates():
    roots = {
        "compose.ui": ("ui", "ui-backhandler", "ui-geometry", "ui-graphics", "ui-text",
                       "ui-unit", "ui-util", "ui-uikit", "ui-test"),
        "compose.animation": ("animation", "animation-core"),
        "compose.foundation": ("foundation", "foundation-layout"),
        "compose.material": ("material-ripple",),
        "compose.material3": ("material3",),
        "androidx.navigationevent": ("navigationevent-compose",),
    }
    result = {}
    for group, modules in roots.items():
        version = "1.9.0" if group.endswith("material3") else "1.1.0" if group.endswith("navigationevent") else "1.12.2"
        for module in modules:
            for suffix in ("", "-tvosarm64", "-tvossimulatorarm64"):
                result[(PREFIX + group, module + suffix, version)] = "ui"
    for suffix in ("", "-tvosArm64", "-tvosSimulatorArm64"):
        result[(PREFIX + "compose.components", "components-resources" + suffix, "1.12.2")] = "resources"
    result[(PREFIX + "compose", "compose-gradle-plugin", "1.12.2")] = "resources"
    return result


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def inventory(repositories, expected=None):
    expected = expected_coordinates() if expected is None else expected
    found = set()
    files = {}
    for label, root in repositories.items():
        if root.is_symlink() or not root.is_dir():
            raise ValueError(f"Repository must be an actual directory: {root}")
        for path in sorted(root.rglob("*")):
            if path.is_symlink():
                raise ValueError(f"Repository symlink is not permitted: {path}")
            if path.is_dir():
                continue
            if not path.is_file():
                raise ValueError(f"Not a regular artifact: {path}")
            relative = path.relative_to(root)
            # Only the 52 reviewed private publications and their Maven metadata/checksums.
            parts = relative.parts
            metadata_name = re.fullmatch(r"maven-metadata(?:-local)?\.xml(?:\.(?:md5|sha1|sha256|sha512))?", path.name)
            if metadata_name:
                allowed = any(label == owner and tuple(group.split(".")) + (module,) == parts[:-1]
                              for (group, module, _), owner in expected.items())
            else:
                coordinate = (".".join(parts[:-3]), parts[-3], parts[-2]) if len(parts) >= 4 else None
                allowed = expected.get(coordinate) == label
            if not allowed:
                raise ValueError(f"Unreviewed publication path: {label}/{relative}")
            files[f"{label}/{relative.as_posix()}"] = {"size": path.stat().st_size, "sha256": digest(path)}
            if path.suffix != ".module":
                continue
            found.add(coordinate)
            metadata = json.loads(path.read_text())
            component = metadata["component"]
            group, module, version = coordinate
            # Native leaf metadata identifies the umbrella component through component.url.
            umbrella = re.sub(r"-(tvosarm64|tvossimulatorarm64)$", "", module, flags=re.I)
            if (component["group"], component["module"], component["version"]) != (group, umbrella, version):
                raise ValueError(f"Module component does not match its coordinate: {relative}")
            target = next((target for suffix, target in TARGETS.items() if module.lower().endswith("-" + suffix)), None)
            if target and (not metadata["variants"] or any(
                    v.get("attributes", {}).get("org.jetbrains.kotlin.native.target") != target
                    for v in metadata["variants"])):
                raise ValueError(f"Non-tvOS or wrong-target leaf variant: {relative}")
            # Check every metadata file reference against the real bytes, not just the lock.
            for variant in metadata["variants"]:
                for artifact in variant.get("files", []):
                    url = artifact["url"]
                    if not re.fullmatch(r"[A-Za-z0-9_.-]+", url) or url in (".", "..") or PurePosixPath(url).name != url:
                        raise ValueError(f"Non-local artifact URL: {url}")
                    artifact_path = path.parent / url
                    if artifact_path.is_symlink() or not artifact_path.is_file():
                        raise ValueError(f"Missing artifact: {artifact_path}")
                    if artifact_path.stat().st_size != artifact["size"] or digest(artifact_path) != artifact["sha256"]:
                        raise ValueError(f"Metadata artifact checksum mismatch: {artifact_path}")
    if found != set(expected):
        raise ValueError(f"Publication inventory mismatch; missing={sorted(set(expected) - found)} extra={sorted(found - set(expected))}")
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("seal", "verify"))
    parser.add_argument("--ui-repository", type=Path, required=True)
    parser.add_argument("--resources-repository", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--provenance", type=Path, help="Required when sealing; reviewed source/toolchain/deployment facts")
    parser.add_argument("--expected-sha256", help="Required when verifying; obtain separately from reviewed source")
    args = parser.parse_args()
    if args.action == "verify":
        if not args.expected_sha256 or not re.fullmatch("[0-9a-f]{64}", args.expected_sha256):
            parser.error("verify requires a reviewed --expected-sha256")
        if digest(args.manifest) != args.expected_sha256:
            raise ValueError("Manifest SHA-256 differs from the separately trusted identity")
        manifest = json.loads(args.manifest.read_text())
        if manifest.get("schemaVersion") != 1:
            raise ValueError("Unsupported manifest schema")
    else:
        if not args.provenance or args.manifest.exists():
            parser.error("seal requires --provenance and a fresh --manifest path")
        manifest = {"schemaVersion": 1, "provenance": json.loads(args.provenance.read_text())}
        if not manifest["provenance"]:
            parser.error("provenance must not be empty")
    files = inventory({"ui": args.ui_repository, "resources": args.resources_repository})
    if args.action == "verify":
        if manifest["files"] != files:
            raise ValueError("Repository bytes/inventory differ from the reviewed manifest")
    else:
        manifest["files"] = files
        args.manifest.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"action": args.action, "publications": 52, "files": len(files),
                      "manifestSha256": digest(args.manifest)}))


if __name__ == "__main__":
    main()
