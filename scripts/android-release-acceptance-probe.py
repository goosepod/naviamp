#!/usr/bin/env python3
"""Run explicitly authorized physical-device checks through native accessibility.

Requires installed .v2test host + instrumentation APK. It never installs, clears,
uninstalls or upgrades the production package. Treat input steps and all captured
windows/logs as private: account and library labels may be present.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--steps-file', type=Path, required=True)
parser.add_argument('--evidence-dir', type=Path, required=True)
parser.add_argument('--name', required=True)
parser.add_argument('--package', choices=['app.naviamp.android', 'app.naviamp.android.v2test', 'com.tailscale.ipn'], default='app.naviamp.android')
parser.add_argument('--adb', default=str(Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk'))) / 'platform-tools/adb'))
args = parser.parse_args()
if not re.fullmatch(r'[A-Za-z0-9-]+', args.name):
    parser.error('--name must contain only letters, digits and hyphens')
steps = json.loads(args.steps_file.read_text(encoding='utf-8-sig'))
if not isinstance(steps, list):
    parser.error('steps must be a JSON array')
args.evidence_dir.mkdir(parents=True, exist_ok=True)
adb = [args.adb, '-s', args.serial]
# adb shell has a second parsing layer even when the host uses subprocess argv.
payload = shlex.quote(json.dumps(steps, separators=(',', ':')))
command = ['shell', 'am', 'instrument', '-w', '-r',
           '-e', 'class', 'app.naviamp.android.AndroidReleaseAcceptanceInstrumentedTest',
           '-e', 'releaseAcceptance', 'true', '-e', 'acceptancePackage', args.package,
           '-e', 'acceptanceSteps', payload,
           'app.naviamp.android.v2test.test/androidx.test.runner.AndroidJUnitRunner']
result = subprocess.run(adb + command, capture_output=True, text=True, encoding='utf-8', errors='replace')
log = args.evidence_dir / (args.name + '.log')
log.write_text(result.stdout + result.stderr, encoding='utf-8')
for step in steps:
    if step.get('op') != 'snapshot':
        continue
    name = step.get('name', '')
    if f'Release acceptance: captured {name}' not in result.stdout:
        continue  # Do not copy a stale capture from an earlier failed phase.
    if not re.fullmatch(r'[A-Za-z0-9-]+', name):
        continue
    for extension in ('txt', 'png'):
        capture = subprocess.run(adb + ['exec-out', 'run-as', 'app.naviamp.android.v2test', 'cat',
                                       f'cache/release-acceptance/{name}.{extension}'], capture_output=True)
        if capture.returncode == 0:
            (args.evidence_dir / f'{name}.{extension}').write_bytes(capture.stdout)
passed = result.returncode == 0 and 'OK (1 test)' in result.stdout and 'FAILURES' not in result.stdout
print(f'{args.name}: {"PASS" if passed else "FAIL"}; private evidence: {log}')
sys.exit(0 if passed else 1)
