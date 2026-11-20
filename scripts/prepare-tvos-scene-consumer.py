#!/usr/bin/env python3
"""Create an isolated scene checkout from current tracked/explicit tooling inputs and a verified port."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


def prepare(root, artifacts, expected, destination):
    delivery = runpy.run_path(str(root / 'scripts/tvos-compose-delivery.py'))
    delivery['verify'](artifacts, expected) # Fail before creating or selecting any repository.
    if destination.exists():
        raise ValueError('Consumer destination must be fresh')
    paths = set(subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')) - {''}
    # New source files must participate before git add, while unrelated worktrees/config stay out.
    additions = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '-z', '--',
        'core', 'apps', 'platforms', 'providers', 'native', 'gradle', 'build-logic', 'build-support',
        'scripts', 'tools'], cwd=root).decode().split('\0')
    paths.update(name for name in additions if name)
    # Include explicit tooling inputs for minimal source fixtures as well.
    for folder, pattern in [('scripts', '*.py'), ('scripts', '*.gradle'), ('build-support/tvos-compose', '*')]:
        paths.update(p.relative_to(root).as_posix() for p in (root / folder).glob(pattern) if p.is_file())
    destination.mkdir(parents=True)
    hashes = {}
    for name in sorted(paths):
        source = root / name
        if not source.exists(): continue # Current working-tree deletion.
        if source.is_symlink() or not source.is_file():
            raise ValueError('Consumer inputs must be regular files: ' + name)
        data = source.read_bytes()
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        target.chmod(source.stat().st_mode & 0o777)
        hashes[name] = hashlib.sha256(data).hexdigest()
    original = (root / 'settings.gradle.kts').read_text()
    if not original.startswith('pluginManagement {'):
        raise ValueError('Unreviewed consumer settings layout')
    tail = original[original.index('rootProject.name'):]
    template = (root / 'build-support/tvos-compose/consumer-settings.gradle.kts').read_text()
    header = template.replace('@@UI_URI@@', (artifacts / 'ui').as_uri().replace('$', '\\$')).replace('@@RESOURCES_URI@@', (artifacts / 'resources').as_uri().replace('$', '\\$'))
    (destination / 'settings.gradle.kts').write_text(header + tail)
    report = {'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
              'manifestSha256': expected, 'sourceFiles': hashes,
              'settingsSha256': hashlib.sha256((destination / 'settings.gradle.kts').read_bytes()).hexdigest(),
              'note': 'Working tree snapshot, including local edits; producers are not in the app graph.'}
    (destination / 'consumer-inputs.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--checkout', type=Path, default=Path(__file__).resolve().parents[1])
    p.add_argument('--artifacts', type=Path, required=True)
    p.add_argument('--manifest-sha256', required=True)
    p.add_argument('--destination', type=Path, required=True)
    a = p.parse_args()
    report = prepare(a.checkout.resolve(), a.artifacts.resolve(), a.manifest_sha256, a.destination.resolve())
    print(json.dumps({'sourceCommit': report['sourceCommit'], 'sourceFiles': len(report['sourceFiles'])}))


if __name__ == '__main__': main()
