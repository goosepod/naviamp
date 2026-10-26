#!/usr/bin/env python3
"""Collect bounded CI diagnostics; absent/offline emulators never receive shell commands."""
import argparse
import os
from pathlib import Path
import signal
import subprocess


def capture(output, timeout):
    output.mkdir(parents=True, exist_ok=True)
    def run(arguments, filename):
        path = output / filename
        with path.open('w') as log:
            try:
                process = subprocess.Popen(arguments, stdout=log, stderr=subprocess.STDOUT,
                                           start_new_session=True)
            except OSError as error:
                log.write(f'Cannot start command: {error}\n')
                return ''
            try:
                process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait()
                log.write(f'\nDiagnostics command timed out after {timeout:g}s.\n')
        return path.read_text(errors='replace') if process.returncode == 0 else ''

    (output / 'environment.txt').write_text('\n'.join(
        f'{name}={os.environ.get(name, "unset")}'
        for name in ['ANDROID_HOME', 'ANDROID_SDK_ROOT', 'ANDROID_AVD_HOME']) + '\n')
    devices = run(['adb', 'devices', '-l'], 'devices.txt')
    serial = next((parts[0] for line in devices.splitlines()
                   if len(parts := line.split()) >= 2
                   and parts[0].startswith('emulator-') and parts[1] == 'device'), None)
    if serial is None:
        (output / 'status.txt').write_text('No online emulator; device shell diagnostics skipped.\n')
        return
    (output / 'status.txt').write_text(f'Collecting from {serial}.\n')
    for arguments, filename in [(['shell', 'getprop'], 'properties.txt'),
                                (['shell', 'dumpsys', 'connectivity'], 'connectivity.txt'),
                                (['shell', 'dumpsys', 'mdns'], 'mdns.txt'),
                                (['logcat', '-d', '-t', '8000', '-v', 'threadtime'], 'logcat.txt')]:
        run(['adb', '-s', serial, *arguments], filename)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path('build/android-emulator-diagnostics'))
    parser.add_argument('--timeout-seconds', type=float, default=10)
    args = parser.parse_args()
    if not 0 < args.timeout_seconds <= 20:
        parser.error('timeout must be between 0 and 20 seconds')
    capture(args.output, args.timeout_seconds)
