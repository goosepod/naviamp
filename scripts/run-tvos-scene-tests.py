#!/usr/bin/env python3
"""Run real tvOS remote/text/main+Aurral recovery tests on a newly owned simulator.

Requires macOS/Xcode and reviewed prebuilt Compose ports. --artifacts prepares an isolated consumer.
Does not build Compose producers, use real credentials, or alter an existing simulator.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import selectors
import runpy
import subprocess
import sys
import time
import urllib.request
import uuid

RUNNER_SHA256 = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
ownership = runpy.run_path(str(Path(__file__).with_name('tvos-build-lock.py')))
APP = 'app.naviamp.tvos'
TEST = 'NaviampTVUITests/TvosRemoteRecoveryTests/'
URL = 'http://127.0.0.1:18790'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkout', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', type=Path, required=True, help='New evidence directory; must not exist')
    parser.add_argument('--build-timeout', type=int, default=1800, help='Xcode build budget in seconds (60–7200); cold Native links may need more than warm builds')
    parser.add_argument('--runtime', required=True, help='Installed com.apple.CoreSimulator.SimRuntime.tvOS-… identifier')
    parser.add_argument('--artifacts', type=Path, help='Verified installed bundle; prepare a fresh isolated scene consumer')
    parser.add_argument('--ui-repository', type=Path)
    parser.add_argument('--resources-repository', type=Path)
    parser.add_argument('--artifact-manifest', type=Path)
    parser.add_argument('--expected-manifest-sha256', required=True)
    args = parser.parse_args()
    if not 60 <= args.build_timeout <= 7200: parser.error('--build-timeout must be between 60 and 7200 seconds')
    if sys.platform != 'darwin': parser.error('This test requires macOS and Xcode')
    if not args.runtime.startswith('com.apple.CoreSimulator.SimRuntime.tvOS-'): parser.error('A tvOS runtime is required')
    if args.artifacts and any((args.ui_repository, args.resources_repository, args.artifact_manifest)):
        parser.error('--artifacts cannot be mixed with legacy repository arguments')
    if not args.artifacts and not all((args.ui_repository, args.resources_repository, args.artifact_manifest)):
        parser.error('Supply --artifacts or all three legacy repository arguments')
    lease = runpy.run_path(str(Path(__file__).with_name('tvos-build-lock.py')))['exclusive_build']
    with ownership['cancellation_signals'](), lease(Path(__file__).resolve().parents[1]):
        run_scene(args)


def run_scene(args):
    root, out = args.checkout.resolve(), args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    report = dict(passed=False, timings={}, tests={}, cleanup={}, sources={}, artifactManifest=args.expected_manifest_sha256, runnerSha256=RUNNER_SHA256, buildTimeoutSeconds=args.build_timeout)
    device = fixture = None
    scene_passed = False

    def save(): (out/'summary.json').write_text(json.dumps(report, indent=2), encoding='utf-8')

    def run(command, name, timeout=1200):
        started = time.monotonic()
        with (out/(name+'.log')).open('x') as log:
            env = os.environ.copy()
            env['GRADLE_OPTS'] = env.get('GRADLE_OPTS', '') + ' -Dorg.gradle.daemon=false'
            try:
                returncode = ownership['run_owned_command'](command, cwd=root, env=env, stdout=log, timeout=timeout)
            except BaseException as failure:
                report['timings'][name] = round(time.monotonic()-started, 3)
                report['failure'] = {'stage': name, 'reason': 'timeout' if isinstance(failure, subprocess.TimeoutExpired)
                                     else type(failure).__name__, 'budgetSeconds': timeout,
                                     'cleanupNotes': getattr(failure, '__notes__', [])}
                save()
                raise
        report['timings'][name] = round(time.monotonic()-started, 3)
        save()
        if returncode:
            report['failure'] = {'stage': name, 'reason': 'exit', 'returncode': returncode}
            save()
            raise RuntimeError(f'{name} failed; see {out/(name+".log")}')
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
        def evidence():
            summary = json.loads(subprocess.check_output(['xcrun','xcresulttool','get','test-results','summary',
                '--path',str(result),'--format','json'], text=True, timeout=30))
            report['tests']['scene'] = summary
            save()
            run(['xcrun','xcresulttool','export','attachments','--path',str(result),
                 '--output-path',str(out/'scene-attachments')], 'scene-attachments', 120)
            return summary

        try:
            run(['xcodebuild', 'test-without-building', *xcode, '-resultBundlePath', str(result),
                 '-only-testing:'+TEST+'testMainAndCompanionRemoteRecovery', '-parallel-testing-enabled', 'NO'], 'scene')
        except BaseException:
            failure = report.get('failure')
            if result.exists():
                try:
                    evidence()
                except Exception as error:
                    report.setdefault('evidenceErrors', []).append(str(error))
                finally:
                    report['failure'] = failure
                    save()
            raise
        summary = evidence()
        assert summary['passedTests'] == 1 and summary['totalTestCount'] == 1
        assert summary['failedTests'] == summary['skippedTests'] == summary['expectedFailures'] == 0
        assert summary['devicesAndConfigurations'][0]['device']['deviceId'] == device
        telemetry()

    try:
        if args.artifacts:
            artifacts = args.artifacts.resolve()
            prepare = runpy.run_path(str(root/'scripts/prepare-tvos-scene-consumer.py'))['prepare']
            report['consumer'] = prepare(root, artifacts, args.expected_manifest_sha256, out/'consumer')
            root = out/'consumer'
            args.ui_repository, args.resources_repository = artifacts/'ui', artifacts/'resources'
            args.artifact_manifest = artifacts/'manifest.json'
            save()
        run([sys.executable,str(root/'scripts/tvos-compose-artifacts.py'),'verify',
             '--ui-repository',str(args.ui_repository.resolve()),
             '--resources-repository',str(args.resources_repository.resolve()),
             '--manifest',str(args.artifact_manifest.resolve()),
             '--expected-sha256',args.expected_manifest_sha256], 'verify-artifacts')
        run(['./gradlew', '--console=plain', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8',
             '-Pnaviamp.tvos.enabled=true', '-Pnaviamp.tvos.sceneHost=true',
             '--init-script', 'scripts/probe-compose-tvos-shared-ui.init.gradle',
             'verifyFullSharedUiProbeVariants'], 'consumer-graph')
        (out/'consumer-variants.json').write_bytes((root/'build/full-ui-probe-variants.json').read_bytes())
        device = subprocess.check_output(['xcrun','simctl','create','Naviamp590Scene-'+uuid.uuid4().hex[:8],
            'com.apple.CoreSimulator.SimDeviceType.Apple-TV-4K-3rd-generation-4K',args.runtime], text=True).strip()
        uuid.UUID(device)
        report['device'] = device
        (out/'ownership.json').write_text(json.dumps(dict(device=device, application=APP)), encoding='utf-8')
        xcode = ['-project','apps/tvos/NaviampTV.xcodeproj','-scheme','NaviampTVSceneTests',
            '-configuration','Debug','-destination','id='+device,'-derivedDataPath',str(out/'derived')]
        run(['xcodebuild','build-for-testing',*xcode], 'build', args.build_timeout)
        run([sys.executable, str(root/'scripts/audit-tvos-native-deployment.py'),
             '--archive', str(root/'apps/tvos/build/bin/tvosSimulatorArm64/debugFramework/NaviampTvShared.framework/NaviampTvShared'),
             '--platform', '8', '--maximum-os', '18.5', '--output', str(out/'native-deployment.json')],
            'native-deployment', 180)
        run(['xcrun','simctl','boot',device], 'boot', 120)
        run(['xcrun','simctl','bootstatus',device,'-b'], 'boot-ready', 300)
        fixture = subprocess.Popen([sys.executable,str(root/'scripts/tvos-scene-fixture.py'),'--owned-device',device],
            stdout=subprocess.PIPE, stderr=(out/'fixture-errors.log').open('x'), text=True)
        with selectors.DefaultSelector() as ready:
            ready.register(fixture.stdout, selectors.EVENT_READ)
            assert ready.select(10), 'Fixture startup timed out'
            assert json.loads(fixture.stdout.readline())['port'] == 18790
        test()
        scene_passed = True
    finally:
        if device is not None:
            # The TV button can activate a system Now Playing banner instead of leaving the app.
            # Keep OS routing evidence without adding logging or test hooks to the product.
            try:
                with (out/'system-media.log').open('x') as log:
                    subprocess.run(['xcrun','simctl','spawn',device,'log','show','--last','8m','--style','compact',
                        '--predicate','(process == "NaviampTV" AND subsystem == "com.apple.amp.mediaplayer") OR '
                        '(process == "PineBoard" AND (subsystem == "com.apple.PineBoard.gestures" OR '
                        'subsystem == "com.apple.PineBoard.mediaRemote" OR subsystem == "com.apple.PineBoard.bulletin"))'],
                        stdout=log,stderr=subprocess.STDOUT,timeout=30)
            except (OSError, subprocess.TimeoutExpired) as failure:
                (out/'system-media-error.txt').write_text(str(failure),encoding='utf-8')
        report['cleanup'], report['cleanupErrors'] = ownership['cleanup_owned_scene'](device, fixture)
        # Preserve cleanup evidence even if collecting source hashes fails afterward.
        save()
        for path in (root/'apps/tvos').rglob('*'):
            if path.is_file() and path.suffix in ('.swift','.plist','.xcprivacy','.pbxproj','.xcscheme','.kt','.kts') and 'build' not in path.relative_to(root/'apps/tvos').parts:
                report['sources'][str(path.relative_to(root))] = hashlib.sha256(path.read_bytes().replace(b'\r\n',b'\n')).hexdigest()
        for name in ('core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampTelevisionAppShell.kt','core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampBackNavigation.kt','core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampTelevisionPlaybackKeys.kt','core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampTelevisionContent.kt','scripts/tvos-scene-fixture.py','scripts/run-tvos-scene-tests.py'):
            report['sources'][name] = hashlib.sha256((root/name).read_bytes().replace(b'\r\n',b'\n')).hexdigest()
        for name in ('core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampExternalPlaybackSurface.kt',
                     'core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCoreExternalPlaybackBridge.kt',
                     'core/bootstrap/src/commonMain/kotlin/app/naviamp/bootstrap/NaviampRecoverableHostController.kt',
                     'core/bootstrap/src/commonMain/kotlin/app/naviamp/bootstrap/NaviampStoredPlaybackSurface.kt',
                     'platforms/apple-media/build.gradle.kts',
                     'platforms/apple-media/src/appleMain/kotlin/app/naviamp/apple/media/ApplePlaybackSurface.kt'):
            report['sources'][name] = hashlib.sha256((root/name).read_bytes().replace(b'\r\n',b'\n')).hexdigest()
        image = 'scripts/animation-probe/fixtures/artwork-0.png'
        report['sources'][image] = hashlib.sha256((root/image).read_bytes()).hexdigest()
        report['passed'] = scene_passed and not any(report['cleanup'].values())
        save()
    assert not any(report['cleanup'].values()), 'Owned simulator cleanup failed'


if __name__ == '__main__':
    main()
