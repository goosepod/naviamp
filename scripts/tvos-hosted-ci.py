#!/usr/bin/env python3
"""Native CI runtime selection, helper deployment audit and narrowly owned scene cleanup."""
import argparse
import json
from pathlib import Path
import platform
import runpy
import subprocess
import sys
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ownership = runpy.run_path(str(ROOT/'scripts/tvos-build-lock.py'))


def select_runtime(runtimes):
    choices = [r for r in runtimes if r.get('isAvailable') is True and
               r.get('identifier', '').startswith('com.apple.CoreSimulator.SimRuntime.tvOS-') and
               tuple(map(int, r['version'].split('.'))) >= (18, 5)]
    if not choices:
        raise ValueError('No available tvOS runtime at or above the candidate 18.5 floor')
    return max(choices, key=lambda r: tuple(map(int, r['version'].split('.'))))


def runtime(output, download=False):
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise ValueError('Hosted scene execution requires an ARM64 Mac')
    def available():
        return json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', 'runtimes', '--json'], text=True, timeout=30))['runtimes']
    try:
        selected = select_runtime(available())
    except ValueError:
        if not download:
            raise
        sdk = subprocess.check_output(['xcrun', '--sdk', 'appletvsimulator', '--show-sdk-version'], text=True, timeout=30).strip()
        subprocess.run(['xcodebuild', '-downloadPlatform', 'tvOS', '-buildVersion', sdk,
                        '-architectureVariant', 'arm64'], check=True, timeout=540, stdout=sys.stderr)
        selected = select_runtime(available())
    with output.open('x') as stream:
        stream.write(json.dumps(selected, indent=2)+'\n')
    return selected['identifier']


def cleanup(scene):
    scene.mkdir(parents=True, exist_ok=True)
    result = {'passed': False}
    try:
        record = scene/'ownership.json'
        if not record.exists():
            result.update(passed=True, reason='No scene device was allocated')
            return result
        owner = json.loads(record.read_text())
        device = owner['device']
        uuid.UUID(device)
        if owner.get('application') != 'app.naviamp.tvos':
            raise ValueError('Not a Naviamp scene ownership record')
        devices = json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', 'devices', '--json'], text=True, timeout=30))['devices']
        match = next((d for rows in devices.values() for d in rows if d['udid'] == device), None)
        if match and not match['name'].startswith('Naviamp590Scene-'):
            raise ValueError('Refusing cleanup of an unrelated simulator')
        result['device'] = device
        result['cleanup'], result['errors'] = ownership['cleanup_owned_scene'](device, None)
        ownership['terminate_consumer_gradle'](scene/'consumer')
        result['passed'] = not any(result['cleanup'].values())
        if not result['passed']:
            raise RuntimeError('Owned simulator cleanup did not complete')
        return result
    except BaseException as error:
        result['error'] = str(error)
        raise
    finally:
        (scene/'ci-cleanup.json').write_text(json.dumps(result, indent=2)+'\n')


def audit_helpers(artifacts, manifest_sha, output):
    runpy.run_path(str(ROOT/'scripts/tvos-compose-delivery.py'))['verify'](artifacts, manifest_sha)
    output.mkdir(parents=True, exist_ok=False)
    for target, native, number in [('tvosarm64', 'tvos_arm64', '3'), ('tvossimulatorarm64', 'tvos_simulator_arm64', '8')]:
        name = 'ui-uikit-'+target
        paths = list((artifacts/'ui').glob('**/'+name+'/1.12.2/'+name+'-1.12.2.klib'))
        if len(paths) != 1:
            raise ValueError('Missing or ambiguous UIKit helper publication')
        archive = output/(target+'.a')
        with zipfile.ZipFile(paths[0]) as zipped:
            member = 'default/targets/'+native+'/included/libCMPUIKitUtils.a'
            with archive.open('xb') as stream:
                stream.write(zipped.read(member))
        with (output/(target+'.log')).open('x') as log:
            subprocess.run([sys.executable, str(ROOT/'scripts/audit-tvos-native-deployment.py'), '--archive', str(archive),
                            '--platform', number, '--maximum-os', '18.5', '--output', str(output/target)],
                           stdout=log, stderr=subprocess.STDOUT, check=True, timeout=180)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    commands = p.add_subparsers(dest='command', required=True)
    q = commands.add_parser('runtime')
    q.add_argument('--output', type=Path, required=True)
    q.add_argument('--download', action='store_true')
    q = commands.add_parser('cleanup')
    q.add_argument('--scene', type=Path, required=True)
    q = commands.add_parser('audit-helpers')
    q.add_argument('--artifacts', type=Path, required=True)
    q.add_argument('--manifest-sha256', required=True)
    q.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    if args.command == 'runtime':
        print('runtime='+runtime(args.output, args.download))
    elif args.command == 'cleanup':
        print(json.dumps(cleanup(args.scene)))
    else:
        audit_helpers(args.artifacts, args.manifest_sha256, args.output)


if __name__ == '__main__':
    main()
