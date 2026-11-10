#!/usr/bin/env python3
"""Compatibility keys and measurable iOS cache-stage evidence; never caches test reports."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import time
import tomllib


def compatibility(root, architecture, xcode, simulator_sdk, device_sdk):
    kotlin = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())['versions']['kotlin']
    wrapper = hashlib.sha256((root / 'gradle/wrapper/gradle-wrapper.properties').read_bytes()).hexdigest()
    identity = dict(architecture=architecture, kotlin=kotlin, xcode=xcode.strip(),
                    simulatorSdk=simulator_sdk.strip(), deviceSdk=device_sdk.strip(), wrapper=wrapper)
    digest = hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()[:24]
    return identity, 'naviamp-ios-v1-' + digest


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def configure_homes(cache_root):
    gradle_home, konan_home = cache_root / 'gradle', cache_root / 'konan'
    gradle_home.mkdir(parents=True, exist_ok=True)
    # Xcode invokes Gradle again without the compile step's -P arguments. User-home
    # properties apply to every invocation and are deliberately outside the archive paths.
    native_path = str(konan_home).replace('\\', '\\\\')
    (gradle_home / 'gradle.properties').write_text('konan.data.dir=' + native_path + '\n')
    return gradle_home, konan_home


def metadata(root, cache_root, generation):
    if not re.fullmatch(r'[a-zA-Z0-9_-]{1,100}', generation):
        raise ValueError('Invalid cache generation')
    identity, prefix = compatibility(root, platform.machine(), command('xcodebuild', '-version'),
        command('xcrun', '--sdk', 'iphonesimulator', '--show-sdk-build-version'),
        command('xcrun', '--sdk', 'iphoneos', '--show-sdk-build-version'))
    cache_root = cache_root.resolve()
    gradle_home, konan_home = configure_homes(cache_root)
    output = root / 'build/ci-cache'
    output.mkdir(parents=True, exist_ok=True)
    result = dict(identity=identity, prefix=prefix, generation=generation,
                  runnerImage={key: os.environ.get(key) for key in ('ImageOS', 'ImageVersion', 'RUNNER_ARCH')},
                  source=command('git', '-C', str(root), 'rev-parse', 'HEAD'))
    (output / 'compatibility.json').write_text(json.dumps(result, indent=2))
    values = {'prefix': prefix, 'gradle-key': f'{prefix}-gradle-{generation}',
              'konan-key': f'{prefix}-konan-{generation}',
              'gradle-home': str(gradle_home), 'konan-home': str(konan_home)}
    with open(os.environ['GITHUB_OUTPUT'], 'a') as target:
        for key, value in values.items():
            target.write(f'{key}={value}\n')
    with open(os.environ['GITHUB_ENV'], 'a') as target:
        target.write(f'GRADLE_USER_HOME={values["gradle-home"]}\n')
        target.write(f'NAVIAMP_KONAN_HOME={values["konan-home"]}\n')


def inventory(directory):
    files = [path for path in Path(directory).rglob('*') if path.is_file()]
    return dict(files=len(files), bytes=sum(path.stat().st_size for path in files))


def outcomes(text):
    tasks = {}
    for match in re.finditer(r'^> Task (\S+)(?: (FROM-CACHE|UP-TO-DATE|NO-SOURCE|SKIPPED|FAILED))?$', text, re.M):
        tasks[match.group(1)] = match.group(2) or 'EXECUTED'
    counts = {}
    for state in tasks.values():
        counts[state] = counts.get(state, 0) + 1
    return counts


def build(root, native, focused):
    output = root / 'build/ci-cache'
    output.mkdir(parents=True, exist_ok=True)
    args = ['./gradlew', '--parallel', '--max-workers=2', '--profile', '--console=plain', '--build-cache',
            '--init-script', 'scripts/ios-ci-cache.init.gradle.kts',
            '-Pkonan.data.dir=' + os.environ['NAVIAMP_KONAN_HOME'],
            'verifyCoreFirstArchitecture',
            ':platforms:ios:linkDebugTestIosSimulatorArm64',
            ':platforms:ios:stageIosSimulatorTestBassFrameworks']
    if not focused:
        args.extend(['iosSimulatorArm64Test', '-x', ':platforms:ios:iosSimulatorArm64Test',
                     ':apps:ios:compileKotlinIosArm64'])
    if native:
        args.append(':core:testkit:prepareMobileMutualTlsFixture')
    started = time.monotonic()
    with (output / 'gradle.log').open('w') as log:
        process = subprocess.Popen(args, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in process.stdout:
            print(line, end='', flush=True)
            log.write(line)
        code = process.wait()
    summary = dict(source=command('git', '-C', str(root), 'rev-parse', 'HEAD'),
                   gradleSeconds=time.monotonic() - started, gradleExit=code,
                   taskOutcomes=outcomes((output / 'gradle.log').read_text()),
                   gradleCache=inventory(os.environ['GRADLE_USER_HOME']),
                   konanCache=inventory(os.environ['NAVIAMP_KONAN_HOME']), nativeRequested=native,
                   focusedDiagnostic=focused)
    (output / 'build.json').write_text(json.dumps(summary, indent=2))
    if not code and native:
        started = time.monotonic()
        code = subprocess.call(['python3', 'scripts/replay-ios-native-tls.py', '--runs', '1',
                                '--output', 'build/ci-cache/native'], cwd=root)
        summary.update(nativeSeconds=time.monotonic() - started, nativeExit=code)
        (output / 'build.json').write_text(json.dumps(summary, indent=2))
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as target:
        target.write('### iOS cache stage\n```json\n' + json.dumps(summary, indent=2) + '\n```\n')
    raise SystemExit(code)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest='mode', required=True)
    key = subs.add_parser('key')
    key.add_argument('--cache-root', type=Path, required=True)
    key.add_argument('--generation', required=True)
    stage = subs.add_parser('build')
    stage.add_argument('--native', action='store_true')
    stage.add_argument('--focused-native', action='store_true',
                       help='Build only the native boundary suite, without unrelated common test links')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.mode == 'key':
        metadata(root, args.cache_root, args.generation)
    else:
        build(root, args.native or args.focused_native, args.focused_native)


if __name__ == '__main__':
    main()
