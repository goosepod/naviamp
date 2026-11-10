#!/usr/bin/env python3
"""Observe only this invocation's standalone native test; preserve its exit status."""
import argparse
from contextlib import nullcontext
import json
import os
from pathlib import Path
import runpy
import shlex
import subprocess
import time


def matching_pids(process_table, executable):
    result = set()
    for line in process_table.splitlines():
        fields = line.strip().split(None, 1)
        if len(fields) != 2 or not fields[0].isdigit():
            continue
        command = fields[1]
        if command == executable:
            result.add(int(fields[0]))
    return result


def processes(executable):
    try:
        # Read executable paths, not every process's potentially paged-out argument vector.
        table = subprocess.check_output(['ps', '-axo', 'pid=,comm='], text=True, timeout=5)
        return matching_pids(table, executable)
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        print(f'Native process diagnostics unavailable: {error}', flush=True)
        return set()


def host_resources(output, stage):
    """Manual diagnostics only; retain bounded observations without changing native results."""
    commands = {
        'memory': ['vm_stat'],
        'swap': ['sysctl', 'vm.swapusage'],
        'pressure': ['memory_pressure', '-Q'],
        # Executable names and numeric resource counters, never arguments or environment.
        'processes': ['ps', '-axo', 'pid=,ppid=,pcpu=,rss=,comm='],
        'booted-simulators': ['xcrun', 'simctl', 'list', 'devices', 'booted', '--json'],
    }
    results = {}
    for name, command in commands.items():
        started = time.monotonic()
        wall_started = time.time()
        with (output / f'host-{stage}-{name}.log').open('w') as log:
            try:
                result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=5)
                results[name] = {'exitCode': result.returncode}
            except (OSError, subprocess.TimeoutExpired) as error:
                log.write(f'Diagnostic unavailable: {error}\n')
                results[name] = {'unavailable': type(error).__name__}
        results[name]['seconds'] = time.monotonic() - started
        results[name]['startedUnixSeconds'] = wall_started
    (output / f'host-{stage}-timing.json').write_text(json.dumps(results, indent=2))


def owns_fixture(command, bootstrap):
    try:
        arguments = shlex.split(command)
        return (len(arguments) == 3 and Path(arguments[0]).name == 'java'
                and arguments[1].startswith('@')
                and Path(arguments[1][1:]).name == 'mobile-mtls-fixture.args'
                and arguments[2] == str(bootstrap.resolve()))
    except ValueError:
        return False


def capture(pid, output, age, fixture_bootstrap=None):
    suffix = f'{age}s' if isinstance(age, int) else age
    stem = output / f'native-{pid}-{suffix}'
    commands = [] if age == 'failed' else [
        ('cpu', ['ps', '-p', str(pid), '-o', 'pid,pcpu,comm']),
        ('sockets', ['lsof', '-nP', '-a', '-p', str(pid), '-iTCP']),
        ('stack', ['sample', str(pid), '2', '-file', str(stem) + '.sample.txt']),
    ]
    if fixture_bootstrap is not None:
        pid_file = fixture_bootstrap.with_name('fixture.pid')
        try:
            fixture_pid = int(pid_file.read_text().strip())
            command = subprocess.check_output(['ps', '-p', str(fixture_pid), '-o', 'command='],
                                              text=True, timeout=5).strip()
            # A recycled PID or another fixture must never be sampled.
            if owns_fixture(command, fixture_bootstrap):
                jcmd = Path(os.environ['JAVA_HOME']) / 'bin/jcmd'
                # Observe socket state during the test; attach to the JVM only after a failure.
                commands.insert(0, ('fixture-sockets', ['lsof', '-nP', '-a', '-p', str(fixture_pid), '-iTCP']))
                if age == 'failed':
                    commands.append(('fixture-threads', [str(jcmd), str(fixture_pid), 'Thread.print']))
        except (OSError, ValueError, KeyError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
            pass
    for name, command in commands:
        with Path(str(stem) + f'.{name}.log').open('w') as log:
            try:
                subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=15)
            except (OSError, subprocess.TimeoutExpired) as error:
                log.write(f'Diagnostic unavailable: {error}\n')


def observe(command, executable, output, fixture_bootstrap=None):
    existing = processes(executable)
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    first_seen = {}
    captured = set()
    test = subprocess.Popen(command)
    try:
        while test.poll() is None:
            for pid in processes(executable) - existing:
                started = first_seen.setdefault(pid, time.monotonic())
                for age in (8, 18):
                    key = (pid, age)
                    if time.monotonic() - started >= age and key not in captured:
                        captured.add(key)
                        capture(pid, output, age, fixture_bootstrap)
            time.sleep(1)
        if test.returncode != 0:
            for pid in first_seen:
                capture(pid, output, 'failed', fixture_bootstrap)
        return test.returncode
    finally:
        # Interrupting the wrapper must not leave its own Gradle invocation running.
        if test.poll() is None:
            test.terminate()
            try:
                test.wait(timeout=10)
            except subprocess.TimeoutExpired:
                test.kill()
                test.wait()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--executable', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--fixture-bootstrap', type=Path)
    parser.add_argument('--capture-loopback', action='store_true',
                        help='Capture packet metadata on this owned fixture only (manual diagnosis)')
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    if not command:
        parser.error('A test command is required after --')
    if args.capture_loopback and args.fixture_bootstrap is None:
        parser.error('Loopback observation requires the owned fixture bootstrap')
    args.output.mkdir(parents=True, exist_ok=True, mode=0o700)
    scope = nullcontext()
    if args.capture_loopback:
        helper = runpy.run_path(str(Path(__file__).with_name('diagnose-ios-fixture-lifetime.py')))
        scope = helper['packets'](args.fixture_bootstrap, args.output / 'loopback-packets.log')
        host_resources(args.output, 'before-native')
    try:
        with scope:
            result = observe(command, str(args.executable.resolve()), args.output, args.fixture_bootstrap)
            # Retain the actual command result even if diagnostic observer teardown fails afterward.
            (args.output / 'native-command-result.json').write_text(json.dumps({'exitCode': result}))
    finally:
        if args.capture_loopback:
            host_resources(args.output, 'after-native')
    return result


if __name__ == '__main__':
    raise SystemExit(main())
