#!/usr/bin/env python3
"""Opt-in, isolated measurements of the actual optimized unsigned iOS archive."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import time
import tarfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('ios_cache', ROOT / 'scripts/ios-ci-cache.py')
CACHE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CACHE)


def identity(root):
    source = CACHE.command('git', '-C', str(root), 'rev-parse', 'HEAD')
    dirty = bool(CACHE.command('git', '-C', str(root), 'status', '--porcelain', '--untracked-files=normal'))
    inputs, prefix = CACHE.compatibility(root, CACHE.platform.machine(), CACHE.command('xcodebuild', '-version'),
        CACHE.command('xcrun', '--sdk', 'iphonesimulator', '--show-sdk-build-version'),
        CACHE.command('xcrun', '--sdk', 'iphoneos', '--show-sdk-build-version'))
    java_env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS'):
        java_env.pop(key, None)  # Version evidence must not print arbitrary JVM option values.
    java = subprocess.run(['java', '-version'], check=True, text=True, env=java_env,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT).stdout.strip()
    return dict(source=source, dirty=dirty, compatibility=inputs, prefix=prefix, java=java,
                configuration='Release', architecture='arm64', archiveHeapGiB=6)


def isolated_cache_root(cache_root, root=ROOT):
    cache_root = cache_root.resolve()
    forbidden = {path.resolve() for path in (Path.home() / '.gradle', Path.home() / '.konan', root, Path('/'))}
    if cache_root in forbidden:
        raise ValueError('Use an isolated compiler cache root')
    return cache_root


def prepare(root, cache_root, report):
    cache_root, report = isolated_cache_root(cache_root, root), report.resolve()
    if cache_root == report or cache_root in report.parents or report in cache_root.parents:
        raise ValueError('Cache and measurement directories must be separate isolated paths')
    home, native = CACHE.configure_homes(cache_root)
    props = home / 'gradle.properties'
    props.write_text(props.read_text() + 'org.gradle.caching=true\n')
    init = home / 'init.d'
    init.mkdir(exist_ok=True)
    for name in ('ios-ci-cache.init.gradle.kts', 'ios-release-profile.init.gradle.kts'):
        (init / name).write_bytes((root / 'scripts' / name).read_bytes())
    report.mkdir(parents=True, exist_ok=False)
    return dict(GRADLE_USER_HOME=str(home), NAVIAMP_KONAN_HOME=str(native),
                NAVIAMP_IOS_ARCHIVE_REPORT_DIR=str(report))


def event(report, name, state):
    with (report / 'phases.jsonl').open('a') as output:
        output.write(json.dumps(dict(phase=name, state=state, monotonicNs=time.monotonic_ns(),
                                     epochSeconds=time.time())) + '\n')


def phase_durations(events):
    pending, durations = {}, {}
    for item in events:
        phase = item['phase']
        if item['state'] == 'start':
            if phase in pending or phase in durations:
                raise ValueError('Duplicate phase: ' + phase)
            pending[phase] = item['monotonicNs']
        elif item['state'] == 'end':
            if phase not in pending:
                raise ValueError('Phase ended without a start: ' + phase)
            duration = item['monotonicNs'] - pending.pop(phase)
            if duration < 0:
                raise ValueError('Invalid phase clock: ' + phase)
            durations[phase] = duration / 1e9
        else:
            raise ValueError('Unknown phase state')
    return durations, sorted(pending)


def summarize(report, workload, elapsed, code, stable=True):
    events = [json.loads(line) for line in (report / 'phases.jsonl').read_text().splitlines()] \
        if (report / 'phases.jsonl').exists() else []
    phases, incomplete = phase_durations(events)
    tasks = []
    for path in sorted((report / 'tasks').glob('gradle-*.json')):
        tasks.extend(json.loads(path.read_text()))
    links = [item for item in tasks if 'linkReleaseFrameworkIosArm64' in item['task']]
    # A fast command which never produced/validated an archive is not a successful benchmark.
    validated = stable and code == 0 and not incomplete and all(name in phases for name in ('archive', 'validation', 'packaging')) and bool(links) and not any(task['outcome'] == 'FAILED' for task in tasks)
    summary = dict(workload=workload, totalSeconds=elapsed, exitCode=code, phases=phases,
                   incompletePhases=incomplete, releaseLinks=links, tasks=tasks, validated=validated, sourceStable=stable)
    (report / 'archive.json').write_text(json.dumps(summary, indent=2))
    return summary


def compare(cold, warm):
    for value in (cold, warm):
        if not value['validated'] or value['exitCode'] or value['workload']['dirty']:
            raise ValueError('Both archives must be successful, validated clean-source builds')
    if cold['workload'] != warm['workload']:
        raise ValueError('Source, toolchain and Release workload must match')
    before, after = cold['totalSeconds'], warm['totalSeconds']
    if before <= 0 or after < 0:
        raise ValueError('Invalid archive durations')
    return dict(coldArchiveSeconds=before, warmArchiveSeconds=after,
                archiveImprovementPercent=(before - after) * 100 / before,
                warmReleaseLinkOutcomes=[link['outcome'] for link in warm['releaseLinks']],
                hostedNetImprovementEstablished=False,
                note='Archive-only comparison; hosted restore/save/setup/upload overhead must be included separately.')


def archive(root, cache_root, report):
    workload = identity(root)
    if workload['dirty']:
        raise ValueError('Commit local changes before recording a matched archive workload')
    env = os.environ.copy()
    env.update(prepare(root, cache_root, report))
    started = time.monotonic()
    with (report / 'archive.log').open('w') as output:
        process = subprocess.Popen(['bash', 'scripts/build-ios-unsigned-ipa.sh'], cwd=root, env=env,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in process.stdout:
            print(line, end='', flush=True)
            output.write(line)
            output.flush()
        code = process.wait()
    result = summarize(report, workload, time.monotonic() - started, code, stable=identity(root) == workload)
    if not result['validated'] and not code:
        raise ValueError('Archive lacks complete phase or optimized-link measurement evidence')
    return code



STORES = ('gradle/caches', 'gradle/wrapper', 'konan')


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def allowed_member(member, destination):
    name = Path(member.name)
    if name.is_absolute() or '..' in name.parts or 'keyrings' in name.parts:
        raise ValueError('Unsafe or credential-bearing cache member: ' + member.name)
    if not any(member.name == store or member.name.startswith(store + '/') for store in STORES):
        raise ValueError('Cache member outside compiler stores: ' + member.name)
    # Reject special files and links which escape, including chains created during extraction.
    return tarfile.data_filter(member, destination)


def pack_cache(cache_root, bundle, workload):
    cache_root, bundle = isolated_cache_root(cache_root), bundle.resolve()
    if cache_root in bundle.parents:
        raise ValueError('Cache bundle must live outside the cache being packed')
    started = time.monotonic()
    bundle.parent.mkdir(parents=True, exist_ok=True)
    def filter_member(member):
        if 'keyrings' in Path(member.name).parts:
            return None
        allowed_member(member, str(cache_root))
        return member
    with tarfile.open(bundle, 'w:gz', compresslevel=1) as target:
        for store in STORES:
            path = cache_root / store
            if not path.is_dir():
                raise ValueError('Missing compiler store: ' + store)
            target.add(path, arcname=store, filter=filter_member)
    manifest = dict(schema=1, workload=workload, sha256=digest(bundle), bytes=bundle.stat().st_size,
                    packSeconds=time.monotonic() - started, stores=list(STORES))
    bundle.with_suffix(bundle.suffix + '.json').write_text(json.dumps(manifest, indent=2))
    return manifest


def restore_cache(cache_root, bundle, workload):
    cache_root, bundle = isolated_cache_root(cache_root), bundle.resolve()
    manifest = json.loads(bundle.with_suffix(bundle.suffix + '.json').read_text())
    if manifest.get('schema') != 1 or manifest.get('stores') != list(STORES) or manifest['workload'] != workload:
        raise ValueError('Cache workload/source/toolchain mismatch')
    if manifest['sha256'] != digest(bundle):
        raise ValueError('Cache bundle checksum mismatch')
    for store in STORES:
        path = cache_root / store
        if path.exists() and (not path.is_dir() or any(path.iterdir())):
            raise ValueError('Restore requires empty compiler stores')
    started = time.monotonic()
    cache_root.mkdir(parents=True, exist_ok=True)
    with tarfile.open(bundle, 'r:gz') as source:
        # Validate every member before writing anything; repeat checks as links are extracted.
        for member in source.getmembers():
            allowed_member(member, str(cache_root))
        source.extractall(cache_root, filter=allowed_member)
    return dict(restoreSeconds=time.monotonic() - started, bytes=manifest['bytes'], sha256=manifest['sha256'])

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    build = commands.add_parser('archive')
    build.add_argument('--cache-root', type=Path, required=True)
    build.add_argument('--report', type=Path, required=True)
    mark = commands.add_parser('event')
    mark.add_argument('--phase', required=True)
    mark.add_argument('--state', choices=('start', 'end'), required=True)
    report = commands.add_parser('compare')
    report.add_argument('--cold', type=Path, required=True)
    report.add_argument('--warm', type=Path, required=True)
    for name in ('pack-cache', 'restore-cache'):
        cache = commands.add_parser(name)
        cache.add_argument('--cache-root', type=Path, required=True)
        cache.add_argument('--bundle', type=Path, required=True)
        cache.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    if args.command == 'event':
        event(Path(os.environ['NAVIAMP_IOS_ARCHIVE_REPORT_DIR']), args.phase, args.state)
    elif args.command in ('pack-cache', 'restore-cache'):
        workload = identity(ROOT)
        if workload['dirty']:
            raise ValueError('Cache transfers require the frozen clean-source benchmark')
        function = pack_cache if args.command == 'pack-cache' else restore_cache
        result = function(args.cache_root, args.bundle, workload)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2))
    elif args.command == 'archive':
        raise SystemExit(archive(ROOT, args.cache_root, args.report))
    else:
        print(json.dumps(compare(json.loads(args.cold.read_text()), json.loads(args.warm.read_text())), indent=2))


if __name__ == '__main__':
    main()
