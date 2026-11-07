#!/usr/bin/env python3
"""Require independent cold native suites using the already-built test binary and fixture jars."""
import argparse
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET


def read_results(directory):
    cases = []
    for path in sorted(directory.glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        if any(int(suite.get(key, '0')) for key in ('failures', 'errors', 'skipped')):
            raise RuntimeError(f'Native suite did not pass every case: {path.name}')
        tests = suite.findall('testcase')
        if len(tests) != int(suite.get('tests', '0')):
            raise RuntimeError(f'Incomplete native report: {path.name}')
        for case in tests:
            if any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped')):
                raise RuntimeError(f'Failed or skipped native case: {case.get("name")}')
            owner, name = case.get('classname', suite.get('name')), case.get('name')
            if not owner or not name:
                raise RuntimeError('Native case has no identity')
            cases.append((owner, name))
    if len(set(cases)) != len(cases):
        raise RuntimeError('Duplicate native results')
    if not any(name.startswith('realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback')
               and owner.endswith('IosMutualTlsPlaybackIntegrationTest') for owner, name in cases):
        raise RuntimeError('Native mTLS contract did not execute')
    return frozenset(cases)


def simctl(*arguments, timeout=30):
    return subprocess.check_output(['xcrun', 'simctl', *arguments], text=True, timeout=timeout).strip()


def stop_fixture(fixture, bootstrap, root):
    try:
        if fixture.poll() is None and bootstrap.exists():
            subprocess.run(['python3', 'scripts/mobile-mtls-fixture.py', 'stop', str(bootstrap)],
                           cwd=root, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
    finally:
        try:
            fixture.wait(timeout=10)
        except subprocess.TimeoutExpired:
            fixture.terminate()
            try:
                fixture.wait(timeout=5)
            except subprocess.TimeoutExpired:
                fixture.kill()
                fixture.wait()


def wait_fixture_ready(root, bootstrap, fixture, output, timeout=180):
    """Provision generated certificates/server before any native assertion deadline starts."""
    started = time.monotonic()
    report = {'budgetSeconds': timeout, 'bootstrapSeconds': None, 'probes': [], 'status': 'starting'}
    try:
        while True:
            if fixture.poll() is not None:
                raise RuntimeError('Owned fixture exited during startup')
            remaining = timeout - (time.monotonic() - started)
            if remaining <= 0:
                raise RuntimeError('Owned fixture did not become ready')
            if bootstrap.exists():
                if report['bootstrapSeconds'] is None:
                    report['bootstrapSeconds'] = time.monotonic() - started
                probe_started = time.monotonic()
                probe = {'startedSeconds': probe_started - started, 'exitCode': None}
                with (output / 'fixture-readiness.log').open('a') as log:
                    try:
                        probe['exitCode'] = subprocess.run(
                            ['python3', 'scripts/mobile-mtls-fixture.py', 'ready', str(bootstrap)],
                            cwd=root, stdout=log, stderr=subprocess.STDOUT,
                            timeout=min(10, remaining)).returncode
                    except subprocess.TimeoutExpired:
                        log.write('Host readiness probe exceeded its setup budget\n')
                probe['seconds'] = time.monotonic() - probe_started
                report['probes'].append(probe)
                if probe['exitCode'] == 0:
                    report['status'] = 'ready'
                    return
            time.sleep(min(0.25, remaining))
    finally:
        report['seconds'] = time.monotonic() - started
        if report['status'] != 'ready':
            report['status'] = 'failed'
        (output / 'fixture-startup.json').write_text(json.dumps(report, indent=2))


def remove_owned_device(record):
    if not record.exists():
        return
    info = json.loads(record.read_text())
    if not info['name'].startswith('Naviamp TLS cold replay '):
        raise RuntimeError('Refusing to remove a device without the replay owner marker')
    devices = json.loads(simctl('list', 'devices', '--json'))['devices']
    if info.get('id'):
        uuid.UUID(info['id'])
        found = next((d for group in devices.values() for d in group if d['udid'] == info['id']), None)
    else:
        # A clone RPC can time out after creating the device but before returning its UUID.
        matches = [d for group in devices.values() for d in group if d['name'] == info['name']]
        if len(matches) > 1:
            raise RuntimeError('Replay clone identity is ambiguous; refusing cleanup')
        found = matches[0] if matches else None
    if found is None:
        return
    if found['name'] != info['name']:
        raise RuntimeError('Replay device identity changed; refusing cleanup')
    uuid.UUID(found['udid'])
    subprocess.run(['xcrun', 'simctl', 'shutdown', found['udid']],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
    simctl('delete', found['udid'])


def replay(root, output, runs):
    results = root / 'platforms/ios/build/test-results/iosSimulatorArm64Test'
    reports = root / 'platforms/ios/build/reports/tests/iosSimulatorArm64Test'
    expected = read_results(results)
    initial = output / 'initial'
    initial.mkdir(parents=True, mode=0o700)
    shutil.copytree(results, initial / 'results')
    if reports.exists():
        shutil.copytree(reports, initial / 'report')
    groups = json.loads(simctl('list', 'devices', 'available', '--json'))['devices']
    runtime, template = next((runtime, device) for runtime, group in groups.items()
                             for device in group if device['name'].startswith('iPhone'))
    (output / 'plan.json').write_text(json.dumps({
        'runs': runs, 'runtime': runtime, 'template': template['name'],
        'expectedCases': sorted(expected),
        'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
        'sourceDirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=root, text=True).strip()),
    }, indent=2))
    java = Path(os.environ['JAVA_HOME']) / 'bin/java'
    for index in range(1, runs + 1):
        run = output / f'run-{index}'
        run.mkdir(mode=0o700)
        bootstrap = run / 'fixture.bootstrap'
        device_record = run / 'device.json'
        stage = 'simulator boot'
        fixture = None
        with (run / 'fixture.log').open('w') as log:
            try:
                name = f'Naviamp TLS cold replay {index} {uuid.uuid4().hex[:12]}'
                device_record.write_text(json.dumps({'id': None, 'name': name}))
                device = simctl('clone', template['udid'], name)
                device_record.write_text(json.dumps({'id': device, 'name': name}))
                simctl('boot', device)
                with (run / 'boot.log').open('w') as boot_log:
                    subprocess.run(['xcrun', 'simctl', 'bootstatus', device, '-b'], check=True,
                                   stdout=boot_log, stderr=subprocess.STDOUT, timeout=300)
                stage = 'fixture startup'
                fixture = subprocess.Popen([str(java), '@build/mobile-mtls-fixture.args', str(bootstrap)],
                                           cwd=root, stdout=log, stderr=subprocess.STDOUT)
                (run / 'fixture.pid').write_text(str(fixture.pid))
                wait_fixture_ready(root, bootstrap, fixture, run)
                stage = 'native execution'
                env = os.environ.copy()
                env['SIMCTL_CHILD_NAVIAMP_MTLS_FIXTURE_BOOTSTRAP'] = bootstrap.read_text()
                command = ['python3', 'scripts/ios-native-test-diagnostics.py',
                           '--capture-loopback',
                           '--executable', str(root / 'platforms/ios/build/bin/iosSimulatorArm64/debugTest/test.kexe'),
                           '--fixture-bootstrap', str(bootstrap), '--output', str(run / 'diagnostics'), '--',
                           './gradlew', '--max-workers=2', '--console=plain', '--profile',
                           '-Pnaviamp.ios.testDevice=' + device, ':platforms:ios:iosSimulatorArm64Test', '--rerun']
                started = time.monotonic()
                with (run / 'native.log').open('w') as native_log:
                    result = subprocess.run(command, cwd=root, env=env, stdout=native_log,
                                            stderr=subprocess.STDOUT, timeout=300)
                if result.returncode != 0:
                    raise RuntimeError(f'Native command failed with exit {result.returncode}')
                if read_results(results) != expected:
                    raise RuntimeError('Cold replay executed a different native test set')
                task_line = '> Task :platforms:ios:iosSimulatorArm64Test'
                if task_line not in (run / 'native.log').read_text().splitlines():
                    raise RuntimeError('Native task did not execute; cached or skipped output is not acceptance')
                summary = {
                    'tests': len(expected), 'failures': 0, 'errors': 0, 'skipped': 0,
                    'nativeSeconds': time.monotonic() - started,
                }
            except BaseException as error:
                (run / 'summary.json').write_text(json.dumps({'status': 'failed', 'stage': stage, 'message': str(error)}))
                print(f'Cold native replay {index} failed during {stage}; evidence: {run}', flush=True)
                raise
            finally:
                try:
                    native_log = run / 'native.log'
                    lines = native_log.read_text().splitlines() if native_log.exists() else []
                    task = '> Task :platforms:ios:iosSimulatorArm64Test'
                    executed = task in lines or task + ' FAILED' in lines
                    if executed and results.exists():
                        shutil.copytree(results, run / 'results')
                    if executed and reports.exists():
                        shutil.copytree(reports, run / 'report')
                    if device_record.exists() and json.loads(device_record.read_text()).get('id'):
                        device = json.loads(device_record.read_text())['id']
                        with (run / 'services.log').open('w') as service_log:
                            try:
                                subprocess.run(['xcrun', 'simctl', 'spawn', device, 'log', 'show', '--style', 'compact', '--last', '5m',
                                                '--predicate', 'process == "test.kexe" OR process == "trustd"'],
                                               stdout=service_log, stderr=subprocess.STDOUT, timeout=30)
                            except (OSError, subprocess.TimeoutExpired) as error:
                                service_log.write(f'Diagnostic unavailable: {error}\n')
                finally:
                    try:
                        remove_owned_device(device_record)
                    finally:
                        if fixture is not None:
                            stop_fixture(fixture, bootstrap, root)
                (run / 'cleanup-complete').write_text('Owned fixture and simulator released\n')
            (run / 'summary.json').write_text(json.dumps(summary))
            print(f'Cold native replay {index}: {len(expected)} executed, zero failures/errors/skips; owned resources cleaned', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--runs', type=int, default=2)
    parser.add_argument('--cleanup-only', action='store_true')
    args = parser.parse_args()
    if not 1 <= args.runs <= 10:
        parser.error('--runs must be between 1 and 10')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    if args.cleanup_only:
        for run in output.glob('run-*'):
            if (run / 'cleanup-complete').exists():
                continue
            remove_owned_device(run / 'device.json')
            bootstrap = run / 'fixture.bootstrap'
            if bootstrap.exists():
                subprocess.run(['python3', 'scripts/mobile-mtls-fixture.py', 'stop', str(bootstrap)],
                               cwd=args.root.resolve(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
        return
    def interrupted(signum, frame):
        raise InterruptedError('Native replay interrupted')
    signal.signal(signal.SIGTERM, interrupted)
    replay(args.root.resolve(), output, args.runs)


if __name__ == '__main__':
    main()
