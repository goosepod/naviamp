#!/usr/bin/env python3
"""Sign a built Flatpak and prepare Pages plus immutable release assets. Never deploys."""
import argparse
import base64
import hashlib
import html
import json
import shutil
import subprocess
from pathlib import Path
from prepare import APP_ID, PACKAGING, release_source


def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, **kwargs)


def publish(repo, signing, public, assets, release):
    version, date, source_url, digest = release_source(release)
    config = json.loads((PACKAGING / "repository.json").read_text())
    fingerprint = config["signing_fingerprint"]
    base = config["url"]
    repo, signing, public, assets = [p.resolve() for p in (repo, signing, public, assets)]
    if any(signing.is_relative_to(p) or p.is_relative_to(signing) for p in (repo, public, assets)):
        raise ValueError("Signing material must be separate from all published output")
    for directory in (public, assets):
        if directory.exists() and any(directory.iterdir()):
            raise ValueError("Choose fresh public and asset output directories")
        directory.mkdir(parents=True, exist_ok=True)
    if not (repo / "config").is_file():
        raise ValueError("A built OSTree repository is required")
    keys = run("gpg", "--homedir", signing, "--with-colons", "--list-secret-keys", fingerprint,
               capture_output=True, text=True).stdout
    if f"fpr:::::::::{fingerprint}:" not in keys:
        raise ValueError("The expected repository signing identity is unavailable")
    public_key = run("gpg", "--homedir", signing, "--export", fingerprint, capture_output=True).stdout
    if not public_key:
        raise ValueError("The public signing key is empty")
    run("flatpak", "build-sign", f"--gpg-homedir={signing}", f"--gpg-sign={fingerprint}", repo, APP_ID, "stable")
    run("flatpak", "build-update-repo", f"--gpg-homedir={signing}", f"--gpg-sign={fingerprint}",
        "--generate-static-deltas", "--prune", "--prune-depth=2", repo)
    shutil.copytree(repo, public / "repo")
    armored = run("gpg", "--homedir", signing, "--armor", "--export", fingerprint, capture_output=True).stdout
    (public / "naviamp.asc").write_bytes(armored)
    key_file = assets / "naviamp.gpg"
    key_file.write_bytes(public_key)
    encoded = base64.b64encode(public_key).decode("ascii")
    remote = f"""[Flatpak Repo]
Title=Naviamp
Url={base}repo/
Homepage=https://goosepod.com/naviamp/
Description=Official Naviamp Flatpak updates
GPGKey={encoded}
"""
    reference = f"""[Flatpak Ref]
Name={APP_ID}
Branch=stable
Title=Naviamp
Url={base}repo/
SuggestRemoteName=naviamp
IsRuntime=false
RuntimeRepo=https://dl.flathub.org/repo/flathub.flatpakrepo
GPGKey={encoded}
"""
    for name, content in (("Naviamp.flatpakref", reference), ("naviamp.flatpakrepo", remote)):
        (public / name).write_text(content)
        (assets / name).write_text(content)
    shutil.copyfile(public / "naviamp.asc", assets / "naviamp.asc")
    provenance = {"application_version": version, "source_url": source_url, "source_sha256": digest,
                  "signing_fingerprint": fingerprint, "repository_url": base + "repo/",
                  "source_code": f"https://github.com/goosepod/naviamp/tree/v{version}"}
    (assets / "source.json").write_text(json.dumps(provenance, indent=2) + "\n")
    run("flatpak", "build-bundle", f"--gpg-keys={key_file}", f"--repo-url={base}repo/",
        "--runtime-repo=https://dl.flathub.org/repo/flathub.flatpakrepo", repo,
        assets / f"Naviamp-v{version}-x86_64.flatpak", APP_ID, "stable")
    run("tar", "--zstd", "-cf", assets / "repository.tar.zst", "-C", public, "repo")
    checksums = "".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n"
                        for p in sorted(assets.iterdir()) if p.is_file())
    (assets / "SHA256SUMS").write_text(checksums)
    (public / ".nojekyll").write_text("")
    (public / "index.html").write_text(f"""<!doctype html>
<html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Naviamp Flatpak</title><body><main>
<h1>Naviamp for Linux</h1><p>Stable version {html.escape(version)}, x86_64.</p>
<p><a href="Naviamp.flatpakref">Install Naviamp with Flatpak</a></p>
<p>Open the installer in your software center, or download it and run:</p>
<pre>flatpak install --user --from ./Naviamp.flatpakref
flatpak run {APP_ID}</pre>
<p>Update with <code>flatpak update --user {APP_ID}</code>, then restart Naviamp.</p>
<p>Signing fingerprint: <code>{fingerprint}</code> · <a href="naviamp.asc">Public key</a></p>
<p><a href="https://goosepod.com/naviamp/">Naviamp website</a> ·
<a href="https://github.com/goosepod/naviamp-flatpak/releases">Bundles and checksums</a> ·
<a href="https://github.com/goosepod/naviamp/tree/v{version}">Application source</a></p>
</main></body></html>""")
    size = sum(p.stat().st_size for p in public.rglob("*") if p.is_file())
    if size > 900 * 1024 * 1024:
        raise ValueError("The repository exceeds the GitHub Pages publication budget")
    print(json.dumps({**provenance, "published_bytes": size}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("repo", "signing", "public", "assets", "release-json"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    publish(args.repo, args.signing, args.public, args.assets, json.loads(args.release_json.read_text()))
