#!/usr/bin/env python3
"""Sample a visible, already-playing profileable app; never launches or changes its UI.

Keep the same track, window, display mode and power conditions across comparisons.
Check both screenshots: a frozen/blank surface is not a passing performance result.
Raw traces/screenshots can contain private device content; keep the output local.
"""
import argparse
from pathlib import Path
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--package', default='app.naviamp.android.benchmark')
    parser.add_argument('--phase', required=True)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--seconds', type=int, default=15)
    parser.add_argument('--trace', action='store_true', help='capture a separate equal-length Perfetto interval')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', args.phase):
        parser.error('phase must contain only letters, digits, dots, hyphens or underscores')
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_.]+', args.package):
        parser.error('invalid package name')
    if not 1 <= args.seconds <= 60:
        parser.error('seconds must be between 1 and 60')
    args.output.mkdir(parents=True, exist_ok=True)
    if list(args.output.glob(args.phase + '-*')) or (args.output / (args.phase + '.trace')).exists():
        parser.error('phase already exists; choose a fresh phase to preserve evidence')
    adb = [args.adb, '-s', args.serial]

    def save(suffix, *command):
        result = subprocess.run(adb + list(command), capture_output=True)
        (args.output / (args.phase + suffix)).write_bytes(result.stdout)
        if result.stderr:
            (args.output / (args.phase + suffix + '.stderr')).write_bytes(result.stderr)
        result.check_returncode()

    save('-before.png', 'exec-out', 'screencap', '-p')
    save('-thermal.txt', 'shell', 'dumpsys', 'thermalservice')
    save('-battery.txt', 'shell', 'dumpsys', 'battery')
    save('-gfx-reset.txt', 'shell', 'dumpsys', 'gfxinfo', args.package, 'reset')
    save('-cpu.txt', 'shell', 'simpleperf', 'stat', '--app', args.package,
         '--duration', str(args.seconds), '-e', 'task-clock')
    save('-gfx.txt', 'shell', 'dumpsys', 'gfxinfo', args.package)
    save('-after.png', 'exec-out', 'screencap', '-p')
    if args.trace:
        remote = '/data/misc/perfetto-traces/naviamp-' + args.phase + '.trace'
        save('-perfetto.txt', 'shell', 'perfetto', '-o', remote, '-t', str(args.seconds) + 's',
             'sched', 'freq', 'idle', 'gfx', 'view', 'wm')
        subprocess.run(adb + ['pull', remote, str(args.output / (args.phase + '.trace'))], check=True)
        subprocess.run(adb + ['shell', 'rm', remote], check=True)
    for path in (args.output / (args.phase + '-cpu.txt'), args.output / (args.phase + '-cpu.txt.stderr')):
        if path.exists():
            print(path.read_text(), end='')


if __name__ == '__main__':
    main()
