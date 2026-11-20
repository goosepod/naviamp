#!/usr/bin/env python3
"""Package/install reviewed local tvOS ports. Trust identities must arrive separately."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import runpy
import shutil
import stat
import tempfile
import zipfile

integrity = runpy.run_path(str(Path(__file__).with_name('tvos-compose-artifacts.py')))
digest = integrity['digest']
MAX_BYTES = 1_000_000_000


def verify(root, expected):
    manifest = root / 'manifest.json'
    if len(expected) != 64 or digest(manifest) != expected:
        raise ValueError('Manifest differs from independently reviewed identity')
    data = json.loads(manifest.read_text())
    if data.get('schemaVersion') != 1 or data['files'] != integrity['inventory']({
            'ui': root / 'ui', 'resources': root / 'resources'}):
        raise ValueError('Repository differs from manifest')
    return data


def pack(ui, resources, manifest, expected, output):
    if output.exists():
        raise ValueError('Bundle output must be fresh')
    if digest(manifest) != expected:
        raise ValueError('Manifest differs from independently reviewed identity')
    data = json.loads(manifest.read_text())
    if data.get('schemaVersion') != 1 or data['files'] != integrity['inventory']({'ui': ui, 'resources': resources}):
        raise ValueError('Repository differs from manifest')
    paths = {'manifest.json': manifest}
    for label, root in [('ui', ui), ('resources', resources)]:
        paths.update({label + '/' + p.relative_to(root).as_posix(): p for p in root.rglob('*') if p.is_file()})
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.tvos-pack-', dir=output.parent) as temporary:
        staged = Path(temporary) / 'bundle.zip'
        with staged.open('xb') as stream, zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_STORED) as archive:
            for name, path in sorted(paths.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = (stat.S_IFREG | 0o644) << 16
                archive.writestr(info, path.read_bytes())
        os.link(staged, output) # Atomic no-replace publication, even if another writer raced us.
    return digest(output)


def install(bundle, bundle_sha, manifest_sha, destination):
    if destination.exists() or destination.is_symlink():
        raise ValueError('Install destination must be fresh')
    if bundle.stat().st_size > MAX_BYTES or digest(bundle) != bundle_sha:
        raise ValueError('Bundle differs from independently reviewed identity/size limit')
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.tvos-install-', dir=destination.parent) as temporary:
        stage = Path(temporary) / 'artifacts'
        stage.mkdir()
        with zipfile.ZipFile(bundle) as archive:
            members = archive.infolist()
            if len(members) > 10000 or sum(i.file_size for i in members) > MAX_BYTES:
                raise ValueError('Artifact bundle exceeds bounds')
            seen = set()
            for info in members:
                name = info.orig_filename
                path = PurePosixPath(name)
                mode = info.external_attr >> 16
                if (name in seen or not name or name != info.filename or '\\' in name or ':' in name or
                        path.is_absolute() or any(p in ('', '.', '..') for p in name.split('/')) or
                        (name != 'manifest.json' and path.parts[0] not in ('ui', 'resources')) or
                        info.is_dir() or (stat.S_IFMT(mode) not in (0, stat.S_IFREG))):
                    raise ValueError('Unsafe/duplicate artifact member: ' + name)
                seen.add(name)
                target = stage.joinpath(*path.parts)
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(info) as source, target.open('xb') as output:
                    shutil.copyfileobj(source, output)
            data = verify(stage, manifest_sha)
            if seen != set(data['files']) | {'manifest.json'}:
                raise ValueError('Unmanifested bundle members')
        stage.rename(destination)
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    p = commands.add_parser('pack')
    for key in ('ui-repository', 'resources-repository', 'manifest', 'output'):
        p.add_argument('--' + key, type=Path, required=True)
    p.add_argument('--manifest-sha256', required=True)
    p = commands.add_parser('install')
    p.add_argument('--bundle', type=Path, required=True)
    p.add_argument('--bundle-sha256', required=True)
    p.add_argument('--manifest-sha256', required=True)
    p.add_argument('--destination', type=Path, required=True)
    args = parser.parse_args()
    if args.action == 'pack':
        result = pack(args.ui_repository, args.resources_repository, args.manifest, args.manifest_sha256, args.output)
        print(json.dumps({'bundleSha256': result, 'manifestSha256': args.manifest_sha256}))
    else:
        print(install(args.bundle, args.bundle_sha256, args.manifest_sha256, args.destination))


if __name__ == '__main__': main()
