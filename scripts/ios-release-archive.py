#!/usr/bin/env python3
"""Opt-in, isolated measurements of the actual optimized unsigned iOS archive."""
import argparse
from datetime import datetime
import hashlib
import importlib.util
import json
import os
import re
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('ios_cache', ROOT / 'scripts/ios-ci-cache.py')
CACHE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CACHE)


def identity(root, profile_phases=False, cold_compile=False):
    source = CACHE.command('git', '-C', str(root), 'rev-parse', 'HEAD')
    dirty = bool(CACHE.command('git', '-C', str(root), 'status', '--porcelain', '--untracked-files=normal'))
    inputs, prefix = CACHE.compatibility(root, CACHE.platform.machine(), CACHE.command('xcodebuild', '-version'),
        CACHE.command('xcrun', '--sdk', 'iphonesimulator', '--show-sdk-build-version'),
        CACHE.command('xcrun', '--sdk', 'iphoneos', '--show-sdk-build-version'))
    java_env = os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS'):
        java_env.pop(key, None)
    java = subprocess.run(['java', '-version'], check=True, text=True, env=java_env,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT).stdout.strip()
    runner = {key: os.environ.get(key) for key in ('ImageOS', 'ImageVersion', 'RUNNER_ARCH')}
    runner.update(logicalCpus=os.cpu_count(), memoryBytes=CACHE.command('sysctl', '-n', 'hw.memsize'),
                  cpuModel=CACHE.command('sysctl', '-n', 'machdep.cpu.brand_string'))
    return dict(source=source, dirty=dirty, compatibility=inputs, prefix=prefix, java=java, runner=runner,
                configuration='Release', architecture='arm64', archiveHeapGiB=6,
                profilePhases=profile_phases, coldCompile=cold_compile, compilerBuildReports=True)


def prepare(root, cache_root, report, profile_phases=False, cold_compile=False):
    cache_root, report = cache_root.resolve(), report.resolve()
    # Instrument only an explicitly isolated cache; never modify a developer's normal Gradle home.
    forbidden = {Path.home() / '.gradle', Path.home() / '.konan', root, Path('/')}
    if cache_root in forbidden or cache_root == report or cache_root in report.parents or report in cache_root.parents:
        raise ValueError('Cache and measurement directories must be separate isolated paths')
    home, native = CACHE.configure_homes(cache_root)
    props = home / 'gradle.properties'
    props.write_text(props.read_text() + 'org.gradle.caching=' + ('false' if cold_compile else 'true') + '\n')
    with props.open('a') as output:
        output.write('kotlin.build.report.output=file\n')
        output.write('kotlin.build.report.file.output_dir=' + str(report / 'kotlin-build') + '\n')
        output.write('kotlin.build.report.include_compiler_arguments=false\n')
    if profile_phases:
        with props.open('a') as output:
            output.write('naviamp.ios.profilePhases=true\n')
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


def framework_inventory(root):
    framework = root / 'apps/ios/build/bin/iosArm64/releaseFramework/NaviampShared.framework'
    header, library = framework / 'Headers/NaviampShared.h', framework / 'NaviampShared'
    if not header.is_file() or not library.is_file():
        return None
    lines = header.read_text().splitlines()
    return dict(headerBytes=header.stat().st_size, headerLines=len(lines),
                interfaces=sum(line.startswith('@interface ') for line in lines),
                protocols=sum(line.startswith('@protocol ') for line in lines),
                staticLibraryBytes=library.stat().st_size)


def compare_cold(root, baseline, candidate):
    for result in (baseline, candidate):
        workload = result['workload']
        if not result['validated'] or result['exitCode'] or workload['dirty'] or not workload.get('coldCompile'):
            raise ValueError('Both measurements must be validated clean-source cold compilations')
        if len(result['releaseLinks']) != 1 or result['releaseLinks'][0]['outcome'] != 'EXECUTED':
            raise ValueError('Both optimized framework links must execute')
        if any(task['outcome'] == 'FROM-CACHE' for task in result['tasks']):
            raise ValueError('Cold compilation must not restore task outputs')
    before, after = baseline['workload'], candidate['workload']
    if {key: value for key, value in before.items() if key != 'source'} != {key: value for key, value in after.items() if key != 'source'}:
        raise ValueError('Compiler, SDK, architecture, heap and measurement flags must match')
    path = 'apps/ios/build.gradle.kts'
    changed = CACHE.command('git', '-C', str(root), 'diff', '--name-only', before['source'], after['source'])
    if changed != path:
        raise ValueError('Only the framework export configuration may differ')
    original = CACHE.command('git', '-C', str(root), 'show', before['source'] + ':' + path)
    narrowed = CACHE.command('git', '-C', str(root), 'show', after['source'] + ':' + path)
    export = '            export(project(":core:storage"))\n'
    dependency = '            api(project(":core:storage"))'
    if export not in original or dependency not in original or original.replace(export, '').replace(dependency, '            implementation(project(":core:storage"))') != narrowed:
        raise ValueError('The controlled source difference must only narrow storage exports')
    before_link, after_link = (value['releaseLinks'][0]['durationMillis'] / 1000 for value in (baseline, candidate))
    if min(before_link, after_link, baseline['totalSeconds'], candidate['totalSeconds']) <= 0:
        raise ValueError('Invalid compilation duration')
    return dict(baselineSource=before['source'], candidateSource=after['source'],
                baselineArchiveSeconds=baseline['totalSeconds'], candidateArchiveSeconds=candidate['totalSeconds'],
                baselineLinkSeconds=before_link, candidateLinkSeconds=after_link,
                linkImprovementPercent=(before_link - after_link) * 100 / before_link,
                archiveImprovementPercent=(baseline['totalSeconds'] - candidate['totalSeconds']) * 100 / baseline['totalSeconds'],
                baselineApi=baseline.get('frameworkApi'), candidateApi=candidate.get('frameworkApi'),
                hostedImprovementEstablished=False,
                note='Controlled local cold compilation with downloaded dependencies; hosted evidence remains required.')


def compare_toolchain(root, baseline, candidate):
    for result in (baseline, candidate):
        workload = result['workload']
        if not result['validated'] or result['exitCode'] or workload['dirty'] or not workload.get('coldCompile'):
            raise ValueError('Both toolchains must produce validated clean-source cold archives')
        if workload['configuration'] != 'Release' or workload['archiveHeapGiB'] != 6 or workload.get('profilePhases') or not workload.get('compilerBuildReports'):
            raise ValueError('Use the same optimized 6 GiB workload and low-volume file reports')
        if len(result['releaseLinks']) != 1 or result['releaseLinks'][0]['outcome'] != 'EXECUTED':
            raise ValueError('Both optimized links must execute')
        compiles = [task for task in result['tasks'] if task['task'].endswith(':compileKotlinIosArm64')]
        if not compiles or any(task['outcome'] != 'EXECUTED' for task in compiles):
            raise ValueError('Product native sources must compile from fresh outputs')
        if any(task['outcome'] in ('FROM-CACHE', 'FAILED') for task in result['tasks']):
            raise ValueError('Cached or failed tasks invalidate the cold comparison')
    before, after = baseline['workload'], candidate['workload']
    ignored = {'source', 'compatibility', 'prefix'}
    if {k: v for k, v in before.items() if k not in ignored} != {k: v for k, v in after.items() if k not in ignored}:
        raise ValueError('Host, Java and workload controls must match')
    if {k: v for k, v in before['compatibility'].items() if k != 'kotlin'} != {k: v for k, v in after['compatibility'].items() if k != 'kotlin'}:
        raise ValueError('Wrapper, architecture, Xcode and SDK controls must match')
    path = 'gradle/libs.versions.toml'
    if CACHE.command('git', '-C', str(root), 'diff', '--name-only', before['source'], after['source']) != path:
        raise ValueError('Only the Kotlin version may differ in this controlled toolchain pair')
    original = CACHE.command('git', '-C', str(root), 'show', before['source'] + ':' + path)
    updated = CACHE.command('git', '-C', str(root), 'show', after['source'] + ':' + path)
    old, new = before['compatibility']['kotlin'], after['compatibility']['kotlin']
    if old == new or original.replace('kotlin = "' + old + '"', 'kotlin = "' + new + '"', 1) != updated:
        raise ValueError('Catalog difference must only change the recorded compiler version')
    props = CACHE.command('git', '-C', str(root), 'show', before['source'] + ':gradle.properties')
    if re.findall(r'^kotlin\.native\.binary\.gc\s*=\s*(\S+)\s*$', props, re.M) != ['pmcs']:
        raise ValueError('Both sources must explicitly preserve the existing PMCS runtime policy')
    script = CACHE.command('git', '-C', str(root), 'show', before['source'] + ':build.gradle.kts')
    optimizer = re.search(r'com\.android\.tools:r8:(\d+)\.(\d+)\.(\d+)', script)
    if not optimizer or tuple(map(int, optimizer.groups())) < (9, 1, 29):
        raise ValueError('Both sources need the same Kotlin 2.4-compatible Android optimizer')
    seconds = [result['releaseLinks'][0]['durationMillis'] / 1000 for result in (baseline, candidate)]
    if min(*seconds, baseline['totalSeconds'], candidate['totalSeconds']) <= 0:
        raise ValueError('Invalid compiler timing evidence')
    return dict(baselineSource=before['source'], candidateSource=after['source'],
                baselineKotlin=old, candidateKotlin=new, configuredNativeGc='pmcs',
                configuredR8='.'.join(optimizer.groups()),
                baselineLinkSeconds=seconds[0], candidateLinkSeconds=seconds[1],
                linkImprovementPercent=(seconds[0] - seconds[1]) * 100 / seconds[0],
                baselineArchiveSeconds=baseline['totalSeconds'], candidateArchiveSeconds=candidate['totalSeconds'],
                archiveImprovementPercent=(baseline['totalSeconds'] - candidate['totalSeconds']) * 100 / baseline['totalSeconds'],
                hostedImprovementEstablished=False,
                note='Controlled local compiler comparison; runtime/Android checks and matched hosted validation remain required.')


def compare_hosted_toolchain(root, baseline, candidate, jobs):
    result = compare_toolchain(root, baseline, candidate)
    selected = {}
    for job in jobs:
        if job['name'] in ('iOS cold compiler (baseline)', 'iOS cold compiler (candidate)'):
            if job['name'] in selected or job['conclusion'] != 'success' or job['head_sha'] != candidate['workload']['source']:
                raise ValueError('Hosted source/run jobs must be successful and unambiguous')
            selected[job['name']] = job
    if len(selected) != 2 or len({job['run_id'] for job in selected.values()}) != 1:
        raise ValueError('Need both compiler jobs from the same workflow run')
    if not baseline['workload'].get('runner', {}).get('ImageVersion'):
        raise ValueError('Hosted runner-image evidence is required')
    def elapsed(start, end):
        duration = (datetime.fromisoformat(end.replace('Z', '+00:00')) - datetime.fromisoformat(start.replace('Z', '+00:00'))).total_seconds()
        if duration < 0:
            raise ValueError('Invalid job clock')
        return duration
    durations = []
    for variant, archive_result in (('baseline', baseline), ('candidate', candidate)):
        job = selected['iOS cold compiler (' + variant + ')']
        seconds = elapsed(job['started_at'], job['completed_at'])
        if seconds < archive_result['totalSeconds']:
            raise ValueError('Job duration cannot contain the measured archive')
        durations.append(seconds)
    improvement = (durations[0] - durations[1]) * 100 / durations[0]
    result.update(baselineJobSeconds=durations[0], candidateJobSeconds=durations[1],
                  wholeJobImprovementPercent=improvement, hostedImprovementEstablished=improvement > 0,
                  jobs=[dict(id=job['id'], name=job['name'], steps=job['steps']) for job in selected.values()],
                  note='Fresh hosted whole-job comparison includes setup, dependency downloads, archive validation and artifact uploads. Queue wait is excluded; no compiler caches are restored or saved.')
    return result


def archive(root, cache_root, report, profile_phases=False, cold_compile=False):
    workload = identity(root, profile_phases, cold_compile)
    if workload['dirty']:
        raise ValueError('Commit local changes before recording a matched archive workload')
    env = os.environ.copy()
    env.update(prepare(root, cache_root, report, profile_phases, cold_compile))
    started = time.monotonic()
    with (report / 'archive.log').open('w') as output:
        process = subprocess.Popen(['bash', 'scripts/build-ios-unsigned-ipa.sh'], cwd=root, env=env,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in process.stdout:
            print(line, end='', flush=True)
            output.write(line)
            output.flush()
        code = process.wait()
    result = summarize(report, workload, time.monotonic() - started, code,
                       stable=identity(root, profile_phases, cold_compile) == workload)
    result['frameworkApi'] = framework_inventory(root)
    (report / 'archive.json').write_text(json.dumps(result, indent=2))
    if not result['validated'] and not code:
        raise ValueError('Archive lacks complete phase or optimized-link measurement evidence')
    return code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    build = commands.add_parser('archive')
    build.add_argument('--cache-root', type=Path, required=True)
    build.add_argument('--report', type=Path, required=True)
    build.add_argument('--profile-phases', action='store_true', help='Record Kotlin/Native backend phase timings without changing optimization')
    build.add_argument('--cold-compile', action='store_true', help='Disable task-output caching for fresh compilation with downloaded dependencies')
    mark = commands.add_parser('event')
    mark.add_argument('--phase', required=True)
    mark.add_argument('--state', choices=('start', 'end'), required=True)
    report = commands.add_parser('compare')
    report.add_argument('--cold', type=Path, required=True)
    report.add_argument('--warm', type=Path, required=True)
    cold = commands.add_parser('compare-cold')
    cold.add_argument('--baseline', type=Path, required=True)
    cold.add_argument('--candidate', type=Path, required=True)
    toolchain = commands.add_parser('compare-toolchain')
    toolchain.add_argument('--baseline', type=Path, required=True)
    toolchain.add_argument('--candidate', type=Path, required=True)
    toolchain.add_argument('--jobs', type=Path, help='GitHub API jobs for a matched hosted whole-job result')
    args = parser.parse_args()
    if args.command == 'event':
        event(Path(os.environ['NAVIAMP_IOS_ARCHIVE_REPORT_DIR']), args.phase, args.state)
    elif args.command == 'archive':
        raise SystemExit(archive(ROOT, args.cache_root, args.report, args.profile_phases, args.cold_compile))
    elif args.command in ('compare-cold', 'compare-toolchain'):
        if args.command == 'compare-toolchain' and args.jobs:
            print(json.dumps(compare_hosted_toolchain(ROOT, json.loads(args.baseline.read_text()),
                json.loads(args.candidate.read_text()), json.loads(args.jobs.read_text())), indent=2))
            return
        comparison = compare_cold if args.command == 'compare-cold' else compare_toolchain
        print(json.dumps(comparison(ROOT, json.loads(args.baseline.read_text()),
                                      json.loads(args.candidate.read_text())), indent=2))
    else:
        print(json.dumps(compare(json.loads(args.cold.read_text()), json.loads(args.warm.read_text())), indent=2))


if __name__ == '__main__':
    main()
