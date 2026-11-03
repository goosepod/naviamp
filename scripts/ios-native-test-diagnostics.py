#!/usr/bin/env python3
"""Observe only this invocation's standalone native test; preserve its exit status."""
import argparse
import os
from pathlib import Path
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
        if command == executable or command.startswith(executable + ' '):
            result.add(int(fields[0]))
    return result


def processes(executable):
    try:
        table = subprocess.check_output(['ps', '-axo', 'pid=,command='], text=True)
        return matching_pids(table, executable)
    except (OSError, subprocess.CalledProcessError) as error:
        print(f'Native process diagnostics unavailable: {error}', flush=True)
        return set()


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
    stem = output / f'native-{pid}-{age}s'
    commands = [
        ('cpu', ['ps', '-p', str(pid), '-o', 'pid,pcpu,comm']),
        ('sockets', ['lsof', '-nP', '-a', '-p', str(pid), '-iTCP']),
        ('stack', ['sample', str(pid), '2', '-file', str(stem) + '.sample.txt']),
    ]
    if fixture_bootstrap is not None:
        pid_file = fixture_bootstrap.with_name('fixture.pid')
        try:
            fixture_pid = int(pid_file.read_text().strip())
            command = subprocess.check_output(['ps', '-p', str(fixture_pid), '-o', 'command='], text=True).strip()
            # A recycled PID or another fixture must never be sampled.
            if owns_fixture(command, fixture_bootstrap):
                jcmd = Path(os.environ['JAVA_HOME']) / 'bin/jcmd'
                commands.insert(0, ('fixture-threads', [str(jcmd), str(fixture_pid), 'Thread.print']))
                commands.insert(1, ('fixture-sockets', ['lsof', '-nP', '-a', '-p', str(fixture_pid), '-iTCP']))
        except (OSError, ValueError, KeyError, subprocess.CalledProcessError):
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
            time.sleep(0.5)
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
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    if not command:
        parser.error('A test command is required after --')
    return observe(command, str(args.executable.resolve()), args.output, args.fixture_bootstrap)


if __name__ == '__main__':
    raise SystemExit(main())
