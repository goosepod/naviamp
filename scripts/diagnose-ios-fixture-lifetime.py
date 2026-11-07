#!/usr/bin/env python3
"""Matched hosted diagnostics only; never emits merge/release verification evidence."""
import argparse
from contextlib import contextmanager, ExitStack
import hashlib
import json
import os
from pathlib import Path
import runpy
import shutil
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET


def read_results(directory):
    cases = []
    for path in sorted(directory.glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        tests = suite.findall('testcase')
        if len(tests) != int(suite.get('tests', '0')):
            raise RuntimeError('Incomplete native report: ' + path.name)
        for case in tests:
            failure = next((case.find(tag) for tag in ('failure', 'error', 'skipped')
                            if case.find(tag) is not None), None)
            cases.append({'owner': case.get('classname', suite.get('name')), 'name': case.get('name'),
                          'outcome': failure.tag if failure is not None else 'passed',
                          'message': failure.get('message', '') if failure is not None else ''})
    identities = [(case['owner'], case['name']) for case in cases]
    if not cases or len(set(identities)) != len(identities):
        raise RuntimeError('Missing or duplicate native cases')
    if not any(owner and owner.endswith('IosMutualTlsPlaybackIntegrationTest')
               and name and name.startswith('realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback')
               for owner, name in identities):
        raise RuntimeError('Native mTLS contract did not execute')
    return cases


def fixture_ports(bootstrap):
    fields = dict(line.split('=', 1) for line in bootstrap.read_text().splitlines() if '=' in line)
    ports = [int(fields[key]) for key in ('controlPort', 'mutualPort', 'privateCaPort', 'wrongHostPort')]
    if len(set(ports)) != 4 or not all(1 <= port <= 65535 for port in ports):
        raise ValueError('Invalid owned fixture ports')
    return ports


def packet_filter(ports):
    if len(ports) != 4 or len(set(ports)) != 4 or not all(isinstance(p, int) and 1 <= p <= 65535 for p in ports):
        raise ValueError('Only four owned loopback ports may be observed')
    return 'tcp and host 127.0.0.1 and (' + ' or '.join('port ' + str(p) for p in ports) + ')'


@contextmanager
def packets(bootstrap, output):
    # Text metadata only: no payload capture, no -A/-X/-w, no non-loopback interface.
    expression = packet_filter(fixture_ports(bootstrap))
    with output.open('w') as log:
        process = subprocess.Popen(['sudo', '-n', '/usr/sbin/tcpdump', '-n', '-tttt', '-l', '-q',
                                    '-i', 'lo0', expression], stdout=log, stderr=subprocess.STDOUT)
        try:
            yield
        finally:
            if process.poll() is None:
                command = subprocess.check_output(['ps', '-p', str(process.pid), '-o', 'command='], text=True)
                if 'tcpdump' not in command or expression not in command:
                    raise RuntimeError('Packet observer ownership changed; refusing signal')
                subprocess.run(['sudo', '-n', 'kill', '-INT', str(process.pid)], check=True, timeout=10)
                process.wait(timeout=15)


def run_logged(command, log, root, env=None, timeout=900):
    with log.open('w') as output:
        return subprocess.run(command, cwd=root, env=env, stdout=output,
                              stderr=subprocess.STDOUT, timeout=timeout).returncode


@contextmanager
def fixture(root, directory, replay):
    directory.mkdir(mode=0o700)
    bootstrap = directory / 'fixture.bootstrap'
    with (directory / 'fixture.log').open('w') as log:
        process = subprocess.Popen([str(Path(os.environ['JAVA_HOME']) / 'bin/java'),
                                    '@build/mobile-mtls-fixture.args', str(bootstrap)],
                                   cwd=root, stdout=log, stderr=subprocess.STDOUT)
        (directory / 'fixture.pid').write_text(str(process.pid))
        try:
            deadline = time.monotonic() + 120
            while not bootstrap.exists():
                if process.poll() is not None:
                    raise RuntimeError('Owned fixture exited before bootstrap')
                if time.monotonic() >= deadline:
                    raise TimeoutError('Owned fixture did not publish bootstrap')
                time.sleep(0.25)
            subprocess.run(['python3', 'scripts/mobile-mtls-fixture.py', 'ready', str(bootstrap)],
                           cwd=root, check=True, timeout=10)
            yield bootstrap, time.monotonic()
        finally:
            replay['stop_fixture'](process, bootstrap, root)


def native_run(root, directory, bootstrap, fixture_started, replay, expected_hash):
    directory.mkdir(mode=0o700)
    record = directory / 'device.json'
    groups = json.loads(replay['simctl']('list', 'devices', 'available', '--json'))['devices']
    runtime, template = next((runtime, device) for runtime, group in groups.items()
                             for device in group if device['name'].startswith('iPhone'))
    name = 'Naviamp TLS cold replay lifetime ' + uuid.uuid4().hex[:12]
    record.write_text(json.dumps({'id': None, 'name': name}))
    summary = {'runtime': runtime, 'template': template['name']}
    try:
        device = replay['simctl']('clone', template['udid'], name)
        record.write_text(json.dumps({'id': device, 'name': name}))
        replay['simctl']('boot', device)
        if run_logged(['xcrun', 'simctl', 'bootstatus', device, '-b'], directory / 'boot.log', root, timeout=300):
            raise RuntimeError('Owned simulator did not boot')
        executable = root / 'platforms/ios/build/bin/iosSimulatorArm64/debugTest/test.kexe'
        if hashlib.sha256(executable.read_bytes()).hexdigest() != expected_hash:
            raise RuntimeError('Native binary changed between matched cases')
        summary['fixtureAgeAtExecutionSeconds'] = time.monotonic() - fixture_started
        summary['nativeExecutableSha256'] = expected_hash
        env = os.environ.copy()
        env['SIMCTL_CHILD_NAVIAMP_MTLS_FIXTURE_BOOTSTRAP'] = bootstrap.read_text()
        command = ['python3', 'scripts/ios-native-test-diagnostics.py', '--executable', str(executable),
                   '--fixture-bootstrap', str(bootstrap), '--output', str(directory / 'diagnostics'), '--',
                   './gradlew', '--max-workers=2', '--console=plain', '--profile',
                   '-Pnaviamp.ios.testDevice=' + device, ':platforms:ios:iosSimulatorArm64Test', '--rerun']
        started = time.monotonic()
        with packets(bootstrap, directory / 'loopback-packets.log'):
            summary['exitCode'] = run_logged(command, directory / 'native.log', root, env, timeout=300)
        summary['nativeCommandSeconds'] = time.monotonic() - started
        summary['cases'] = read_results(root / 'platforms/ios/build/test-results/iosSimulatorArm64Test')
        task = '> Task :platforms:ios:iosSimulatorArm64Test'
        lines = (directory / 'native.log').read_text().splitlines()
        if task not in lines and task + ' FAILED' not in lines:
            raise RuntimeError('Native task did not execute; cached output is not evidence')
        # Probe only AFTER execution; heartbeats must not warm an aged baseline.
        started = time.monotonic()
        summary['hostControlAfterTestExitCode'] = run_logged(
            ['python3', 'scripts/mobile-mtls-fixture.py', 'ready', str(bootstrap)],
            directory / 'host-control-after-test.log', root, timeout=10)
        summary['hostControlAfterTestSeconds'] = time.monotonic() - started
    finally:
        for relative, destination in (
            ('platforms/ios/build/test-results/iosSimulatorArm64Test', 'results'),
            ('platforms/ios/build/reports/tests/iosSimulatorArm64Test', 'report'),
        ):
            source = root / relative
            if source.exists():
                shutil.move(str(source), str(directory / destination))
        if record.exists() and json.loads(record.read_text()).get('id'):
            device = json.loads(record.read_text())['id']
            run_logged(['xcrun', 'simctl', 'spawn', device, 'log', 'show', '--style', 'compact', '--last', '5m',
                        '--predicate', 'process == "test.kexe" OR process == "trustd" OR subsystem == "com.apple.CFNetwork"'],
                       directory / 'services.log', root, timeout=45)
        replay['remove_owned_device'](record)
        (directory / 'summary.json').write_text(json.dumps(summary, indent=2))
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--minimum-age-seconds', type=int, default=1200)
    parser.add_argument('--pairs', type=int, default=2)
    args = parser.parse_args()
    if not 1 <= args.pairs <= 3 or not 0 <= args.minimum_age_seconds <= 1800:
        parser.error('Use 1–3 pairs and an aged fixture minimum of 0–1800 seconds')
    root, output = args.root.resolve(), args.output.resolve()
    if not output.is_relative_to(root / 'build'):
        parser.error('Diagnostic output must remain in this checkout build directory')
    output.mkdir(parents=True, mode=0o700, exist_ok=False)
    replay = runpy.run_path(str(root / 'scripts/replay-ios-native-tls.py'))
    plan = {'kind': 'diagnostic-only-not-merge-or-release-verification',
            'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
            'pairs': args.pairs, 'minimumFixtureAgeSeconds': args.minimum_age_seconds}
    (output / 'plan.json').write_text(json.dumps(plan, indent=2))
    if run_logged(['./gradlew', ':core:testkit:prepareMobileMutualTlsFixture', '--console=plain'],
                  output / 'prepare-fixture.log', root):
        raise RuntimeError('Fixture runtime compilation failed')
    summaries = []
    with ExitStack() as owners:
        aged = [owners.enter_context(fixture(root, output / f'aged-fixture-{index}', replay))
                for index in range(args.pairs)]
        if run_logged(['./gradlew', '--max-workers=2', '--console=plain', '--profile',
                       ':platforms:ios:linkDebugTestIosSimulatorArm64',
                       ':platforms:ios:stageIosSimulatorTestBassFrameworks'],
                      output / 'compile-native.log', root, timeout=1800):
            raise RuntimeError('Native diagnostic binary compilation failed')
        executable = root / 'platforms/ios/build/bin/iosSimulatorArm64/debugTest/test.kexe'
        binary_hash = hashlib.sha256(executable.read_bytes()).hexdigest()
        while time.monotonic() - aged[-1][1] < args.minimum_age_seconds:
            remaining = args.minimum_age_seconds - (time.monotonic() - aged[-1][1])
            print(f'Aged control: {int(remaining)} seconds until matched native comparison', flush=True)
            time.sleep(min(30, remaining))
        for index in range(args.pairs):
            for condition in (('aged', 'fresh') if index % 2 == 0 else ('fresh', 'aged')):
                directory = output / f'pair-{index + 1}-{condition}'
                if condition == 'aged':
                    summary = native_run(root, directory, *aged[index], replay, binary_hash)
                else:
                    with fixture(root, output / f'fresh-fixture-{index}', replay) as fresh:
                        summary = native_run(root, directory, *fresh, replay, binary_hash)
                summary.update(condition=condition, pair=index + 1)
                summaries.append(summary)
                (output / 'summary.json').write_text(json.dumps(summaries, indent=2))
                print(f'Pair {index + 1} {condition}: exit={summary["exitCode"]}, '
                      f'age={summary["fixtureAgeAtExecutionSeconds"]:.1f}s, '
                      f'failures={sum(c["outcome"] != "passed" for c in summary["cases"])}', flush=True)
    print('Diagnostic comparison complete; this does not satisfy merge or release checks.', flush=True)


if __name__ == '__main__':
    main()
