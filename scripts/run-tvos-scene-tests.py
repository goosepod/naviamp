#!/usr/bin/env python3
"""Run real tvOS remote/text/main+Aurral recovery tests on a newly owned simulator.

Requires macOS/Xcode and an already configured, reviewed Compose-port consumer checkout.
Does not build Compose producers, use real credentials, or alter an existing simulator.
"""
import argparse
import hashlib
import json
from pathlib import Path
import selectors
import subprocess
import sys
import time
import urllib.request
import uuid

APP = 'app.naviamp.tvos'
TEST = 'NaviampTVUITests/TvosRemoteRecoveryTests/'
URL = 'http://127.0.0.1:18790'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkout', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', type=Path, required=True, help='New evidence directory; must not exist')
    parser.add_argument('--runtime', required=True, help='Installed com.apple.CoreSimulator.SimRuntime.tvOS-… identifier')
    parser.add_argument('--ui-repository', type=Path, required=True)
    parser.add_argument('--resources-repository', type=Path, required=True)
    parser.add_argument('--artifact-manifest', type=Path, required=True)
    parser.add_argument('--expected-manifest-sha256', required=True)
    args = parser.parse_args()
    if sys.platform != 'darwin': parser.error('This test requires macOS and Xcode')
    if not args.runtime.startswith('com.apple.CoreSimulator.SimRuntime.tvOS-'): parser.error('A tvOS runtime is required')
    root, out = args.checkout.resolve(), args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    report = dict(passed=False, timings={}, tests={}, cleanup={}, sources={}, artifactManifest=args.expected_manifest_sha256)
    device = fixture = None

    def save(): (out/'summary.json').write_text(json.dumps(report, indent=2), encoding='utf-8')

    def run(command, name, timeout=1200):
        started = time.monotonic()
        with (out/(name+'.log')).open('x') as log:
            result = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
        report['timings'][name] = round(time.monotonic()-started, 3)
        save()
        if result.returncode: raise RuntimeError(f'{name} failed; see {out/(name+".log")}')
        print(name+' passed', flush=True)

    def telemetry():
        data = urllib.request.urlopen(URL+'/_test/status', timeout=5).read()
        (out/'telemetry.json').write_bytes(data)
        state = json.loads(data)['acceptance']
        assert state['finished'] and state['rebuilt']['databaseExists']
        assert state['purge']['preferencesPreserved']
        assert set(state['stages']) == {'create','relaunch','purged'}
        for stage, evidence in state['stages'].items():
            probe, rows = evidence['preflight'], evidence['events']
            assert probe['databaseExists'] == probe['expectedDatabaseExists'] == (stage == 'relaunch')
            assert probe['recoveryRecordExists'] == (stage != 'create')
            assert rows and not any(row['authenticated'] is False for row in rows), 'Authentication failure'
            routes = ['/main/rest/ping', '/companion/api/auth/login', '/companion/api/discover']
            if stage != 'relaunch': routes.append('/main/rest/getArtists')
            if stage == 'purged': routes.append('/main/rest/stream')
            for route in routes:
                assert any(row['route'] == route and row['authenticated'] is True for row in rows), route
        report['stages'], report['purge'], report['rebuilt'] = state['stages'], state['purge'], state['rebuilt']

    def test():
        result = out/'scene.xcresult'
        run(['xcodebuild', 'test-without-building', *xcode, '-resultBundlePath', str(result),
             '-only-testing:'+TEST+'testMainAndCompanionRemoteRecovery', '-parallel-testing-enabled', 'NO'], 'scene')
        summary = json.loads(subprocess.check_output(['xcrun','xcresulttool','get','test-results','summary',
            '--path',str(result),'--format','json'], text=True))
        report['tests']['scene'] = summary
        assert summary['passedTests'] == 1 and summary['totalTestCount'] == 1
        assert summary['failedTests'] == summary['skippedTests'] == summary['expectedFailures'] == 0
        assert summary['devicesAndConfigurations'][0]['device']['deviceId'] == device
        run(['xcrun','xcresulttool','export','attachments','--path',str(result),
             '--output-path',str(out/'scene-attachments')], 'scene-attachments', 120)
        telemetry()

    try:
        run([sys.executable,str(root/'scripts/tvos-compose-artifacts.py'),'verify',
             '--ui-repository',str(args.ui_repository.resolve()),
             '--resources-repository',str(args.resources_repository.resolve()),
             '--manifest',str(args.artifact_manifest.resolve()),
             '--expected-sha256',args.expected_manifest_sha256], 'verify-artifacts')
        device = subprocess.check_output(['xcrun','simctl','create','Naviamp590Scene-'+uuid.uuid4().hex[:8],
            'com.apple.CoreSimulator.SimDeviceType.Apple-TV-4K-3rd-generation-4K',args.runtime], text=True).strip()
        uuid.UUID(device)
        report['device'] = device
        (out/'ownership.json').write_text(json.dumps(dict(device=device, application=APP)), encoding='utf-8')
        run(['xcrun','simctl','boot',device], 'boot', 120)
        run(['xcrun','simctl','bootstatus',device,'-b'], 'boot-ready', 300)
        fixture = subprocess.Popen([sys.executable,str(root/'scripts/tvos-scene-fixture.py'),'--owned-device',device],
            stdout=subprocess.PIPE, stderr=(out/'fixture-errors.log').open('x'), text=True)
        with selectors.DefaultSelector() as ready:
            ready.register(fixture.stdout, selectors.EVENT_READ)
            assert ready.select(10), 'Fixture startup timed out'
            assert json.loads(fixture.stdout.readline())['port'] == 18790
        xcode = ['-project','apps/tvos/NaviampTV.xcodeproj','-scheme','NaviampTVSceneTests',
            '-configuration','Debug','-destination','id='+device,'-derivedDataPath',str(out/'derived')]
        run(['xcodebuild','build-for-testing',*xcode], 'build', 1800)
        test()
        report['passed'] = True
    finally:
        if fixture is not None:
            fixture.terminate()
            fixture.wait(timeout=10)
        if device is not None:
            report['cleanup'] = {action:subprocess.run(['xcrun','simctl',action,device],capture_output=True).returncode
                                 for action in ('shutdown','delete')}
        for path in (root/'apps/tvos').rglob('*'):
            if path.is_file() and path.suffix in ('.swift','.plist','.xcprivacy','.pbxproj','.xcscheme','.kt','.kts') and 'build' not in path.relative_to(root/'apps/tvos').parts:
                report['sources'][str(path.relative_to(root))] = hashlib.sha256(path.read_bytes().replace(b'\r\n',b'\n')).hexdigest()
        for name in ('core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampTelevisionAppShell.kt','core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampBackNavigation.kt','core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampTelevisionPlaybackKeys.kt','core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampTelevisionContent.kt','scripts/tvos-scene-fixture.py','scripts/run-tvos-scene-tests.py'):
            report['sources'][name] = hashlib.sha256((root/name).read_bytes().replace(b'\r\n',b'\n')).hexdigest()
        image = 'scripts/animation-probe/fixtures/artwork-0.png'
        report['sources'][image] = hashlib.sha256((root/image).read_bytes()).hexdigest()
        if any(report['cleanup'].values()): report['passed'] = False
        save()
    assert not any(report['cleanup'].values()), 'Owned simulator cleanup failed'


if __name__ == '__main__': main()
