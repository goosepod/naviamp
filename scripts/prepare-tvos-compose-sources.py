#!/usr/bin/env python3
"""Reconstruct #590's reviewed source producers; never builds or publishes artifacts."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import stat
import subprocess
import zipfile

BUNDLE_SHA256 = "3eb79a0aab2314002db1694a7783aabfd67a5c6050ec048c8b1a25703db583cf"
PRODUCERS = {
    "ui": ("https://github.com/JetBrains/compose-multiplatform-core.git",
           "5f017375d926c73f1937408601b6cb28c12a8812"),
    "resources": ("https://github.com/JetBrains/compose-multiplatform.git",
                  "ab304f2538657d9f7077bdb40797f5db618b4c84"),
}


def read_zip(data, maximum=20_000_000):
    """Read bounded regular files in memory; never use ZIP extraction paths directly."""
    result = {}
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if len(archive.infolist()) > 100 or sum(i.file_size for i in archive.infolist()) > maximum:
            raise ValueError("Source archive exceeds its reviewed size budget")
        for item in archive.infolist():
            # Python may normalize Windows separators or truncate NULs in filename.
            name = item.orig_filename
            path = PurePosixPath(name)
            if (not name or "\0" in name or path.is_absolute() or ":" in name or "\\" in name or
                    any(part in ("", ".", "..") for part in name.rstrip("/").split("/")) or
                    any(part == ".git" for part in path.parts) or
                    stat.S_ISLNK(item.external_attr >> 16)):
                raise ValueError(f"Unsafe source archive path: {name}")
            if item.is_dir():
                continue
            if name in result:
                raise ValueError(f"Duplicate source archive member: {name}")
            result[name] = archive.read(item)
    return result


def load_bundle(path, expected_sha256=BUNDLE_SHA256):
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != expected_sha256:
        raise ValueError("Source bundle differs from its separately reviewed identity")
    files = read_zip(data)
    recipe = json.loads(files["recipe.json"])
    if recipe["schemaVersion"] != 1 or set(recipe["producers"]) != set(PRODUCERS):
        raise ValueError("Unreviewed source recipe")
    for label, (_, base) in PRODUCERS.items():
        entry = recipe["producers"][label]
        packed = files[label + ".patch.gz"]
        manifest = files[label + "-files.json"]
        if (entry["baseCommit"] != base or
                hashlib.sha256(packed).hexdigest() != entry["packedSha256"] or
                hashlib.sha256(manifest).hexdigest() != entry["manifestSha256"]):
            raise ValueError(f"Unreviewed source inputs: {label}")
        with gzip.GzipFile(fileobj=io.BytesIO(packed)) as stream:
            patch = stream.read(16_000_001)
        if len(patch) > 16_000_000 or hashlib.sha256(patch).hexdigest() != entry["patchSha256"]:
            raise ValueError(f"Invalid source patch: {label}")
        files[label + ".patch"] = patch
    return recipe, files


def git(directory, *arguments, data=None):
    result = subprocess.run(["git", "-c", "core.autocrlf=false", "-c", "core.eol=lf",
                             "-c", "pack.threads=2", "-c", "submodule.recurse=false", *arguments],
                            cwd=directory, input=data, capture_output=True, timeout=600)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors="replace")[-8000:])
    return result.stdout


def replay(source, target, base, patch, manifest, expected_tree, reference=None):
    if target.exists():
        raise ValueError("Source destination must be fresh")
    if reference:
        git(target.parent, "clone", "--shared", "--no-checkout", "--", reference.as_posix(), target.as_posix())
    else:
        target.mkdir()
        git(target, "init")
        git(target, "remote", "add", "origin", source)
        git(target, "fetch", "--depth=1", "origin", base)
    git(target, "checkout", "--detach", base)
    # A user's apply.whitespace=fix must not rewrite embedded source strings.
    git(target, "apply", "--index", "--binary", "--whitespace=nowarn", "-", data=patch)
    paths = {p.decode() for p in git(target, "diff", "--cached", base, "--name-only",
                                     "--no-renames", "-z").split(b"\0") if p}
    if paths != set(manifest) or git(target, "write-tree").decode().strip() != expected_tree:
        raise ValueError("Replayed source tree differs from reviewed inputs")
    for name, digest in manifest.items():
        parts = PurePosixPath(name).parts
        if (not parts or PurePosixPath(name).is_absolute() or ".." in parts or
                ":" in name or "\\" in name or ".git" in parts):
            raise ValueError("Unsafe source manifest path")
        path = target.joinpath(*parts)
        if digest is None:
            if path.exists():
                raise ValueError(f"Source deletion did not replay: {name}")
        elif (path.is_symlink() or not path.is_file() or
              hashlib.sha256(path.read_bytes()).hexdigest() != digest):
            raise ValueError(f"Source bytes differ: {name}")
    return {"baseCommit": base, "tree": expected_tree, "paths": len(paths),
            "referenceObjectsShared": reference is not None}


def prepare(bundle, destination, references):
    recipe, files = load_bundle(bundle)
    plugin = read_zip(files["settings-plugin.zip"])
    prefix = "build-logic/compose-tvos/"
    if any(not name.startswith(prefix) for name in plugin):
        raise ValueError("Settings-plugin archive contains an unexpected root")
    destination.mkdir(parents=True, exist_ok=False)
    report = {"passed": False, "bundleSha256": BUNDLE_SHA256, "producers": {},
              "artifactsBuilt": False, "artifactsPublished": False}
    try:
        for label, (url, base) in PRODUCERS.items():
            print(f"Replaying reviewed {label} sources", flush=True)
            report["producers"][label] = replay(
                url, destination / label, base, files[label + ".patch"],
                json.loads(files[label + "-files.json"]), recipe["producers"][label]["tree"],
                references.get(label))
        for name, data in plugin.items():
            path = destination / "settings-plugin" / name[len(prefix):]
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        report["settingsPluginFiles"] = len(plugin)
        report["passed"] = True
    finally:
        (destination / "source-replay.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--ui-reference", type=Path, help="Optional local official Git object source; read-only")
    parser.add_argument("--resources-reference", type=Path, help="Optional local official Git object source; read-only")
    args = parser.parse_args()
    bundle = Path(__file__).resolve().parents[1] / "build-support/tvos-compose/sources.zip"
    references = {name: path.resolve(strict=True) for name, path in
                  (("ui", args.ui_reference), ("resources", args.resources_reference)) if path}
    report = prepare(bundle, args.destination.resolve(), references)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
