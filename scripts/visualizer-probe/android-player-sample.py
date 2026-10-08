#!/usr/bin/env python3
"""Sample a visible, already-playing profileable app; never launches or changes its UI.

Keep the same track, window, display mode and power conditions across comparisons.
Check both screenshots: a frozen/blank surface is not a passing performance result.
Raw traces/screenshots can contain private device content; keep the output local.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--package', default='app.naviamp.android.benchmark')
    parser.add_argument('--phase', required=True)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--seconds', type=int, default=15)
    parser.add_argument('--trace', action='store_true', help='capture a separate equal-length Perfetto interval')
    parser.add_argument('--compositor', action='store_true', help='measure SurfaceFlinger CPU counters during the untraced CPU interval')
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

    def save(suffix, *command, input=None):
        result = subprocess.run(adb + list(command), capture_output=True, input=input)
        (args.output / (args.phase + suffix)).write_bytes(result.stdout)
        if result.stderr:
            (args.output / (args.phase + suffix + '.stderr')).write_bytes(result.stderr)
        result.check_returncode()

    save('-pid-before.txt', 'shell', 'pidof', args.package)
    save('-before.png', 'exec-out', 'screencap', '-p')
    save('-thermal.txt', 'shell', 'dumpsys', 'thermalservice')
    save('-battery.txt', 'shell', 'dumpsys', 'battery')
    save('-gfx-reset.txt', 'shell', 'dumpsys', 'gfxinfo', args.package, 'reset')
    compositor = None
    if args.compositor:
        compositor_pid = subprocess.check_output(adb + ['shell', 'pidof', 'surfaceflinger'], text=True).strip()
        if not compositor_pid.isdigit():
            raise RuntimeError('Expected one SurfaceFlinger PID')
        ticks_per_second = int(subprocess.check_output(adb + ['shell', 'getconf', 'CLK_TCK'], text=True))

        def compositor_counter():
            begin = time.monotonic()
            stat = subprocess.check_output(adb + ['shell', 'cat', '/proc/' + compositor_pid + '/stat'], text=True)
            end = time.monotonic()
            fields = stat[stat.rfind(')') + 2:].split()  # Fields start at Linux stat field 3.
            return dict(cpu_ticks=int(fields[11]) + int(fields[12]), start_ticks=int(fields[19]),
                        monotonic_seconds=(begin + end) / 2)

        compositor = compositor_counter()
    save('-cpu.txt', 'shell', 'simpleperf', 'stat', '--app', args.package,
         '--duration', str(args.seconds), '-e', 'task-clock')
    if compositor is not None:
        after = compositor_counter()
        if compositor['start_ticks'] != after['start_ticks']:
            raise RuntimeError('SurfaceFlinger restarted; reject compositor sample')
        elapsed = after['monotonic_seconds'] - compositor['monotonic_seconds']
        cpu_seconds = (after['cpu_ticks'] - compositor['cpu_ticks']) / ticks_per_second
        summary = dict(pid=int(compositor_pid), ticks_per_second=ticks_per_second,
                       elapsed_seconds=elapsed, cpu_seconds=cpu_seconds,
                       cpu_percent_one_core=cpu_seconds / elapsed * 100,
                       before=compositor, after=after, perfetto_active=False)
        (args.output / (args.phase + '-compositor.json')).write_text(json.dumps(summary, indent=2) + '\n')
        print(f"Untraced SurfaceFlinger CPU: {summary['cpu_percent_one_core']:.2f}% of one core")
    save('-gfx.txt', 'shell', 'dumpsys', 'gfxinfo', args.package)
    save('-after.png', 'exec-out', 'screencap', '-p')
    if args.trace:
        remote = '/data/misc/perfetto-traces/naviamp-' + args.phase + '.trace'
        # Legacy CLI defaults can overwrite process metadata in busy 30-second GPU captures.
        config = f"""buffers {{ size_kb: 131072 fill_policy: RING_BUFFER }}
        duration_ms: {args.seconds * 1000}
        data_sources {{ config {{ name: "linux.process_stats"
          process_stats_config {{ scan_all_processes_on_start: true }} }} }}
        data_sources {{ config {{ name: "linux.ftrace" ftrace_config {{
          ftrace_events: "sched/sched_switch" ftrace_events: "sched/sched_waking"
          ftrace_events: "power/cpu_frequency" ftrace_events: "power/cpu_idle"
          atrace_categories: "gfx" atrace_categories: "view" atrace_categories: "wm"
          atrace_apps: "{args.package}"
        }} }} }}"""
        (args.output / (args.phase + '-perfetto-config.txt')).write_text(config)
        save('-perfetto.txt', 'shell', 'perfetto', '--txt', '-c', '-', '-o', remote, input=config.encode())
        subprocess.run(adb + ['pull', remote, str(args.output / (args.phase + '.trace'))], check=True)
        subprocess.run(adb + ['shell', 'rm', remote], check=True)
    save('-pid-after.txt', 'shell', 'pidof', args.package)
    if (args.output / (args.phase + '-pid-before.txt')).read_bytes() != (args.output / (args.phase + '-pid-after.txt')).read_bytes():
        raise RuntimeError('Benchmark process changed during capture; reject this sample')
    for path in (args.output / (args.phase + '-cpu.txt'), args.output / (args.phase + '-cpu.txt.stderr')):
        if path.exists():
            print(path.read_text(), end='')


if __name__ == '__main__':
    main()
