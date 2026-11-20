#!/usr/bin/env python3
"""Run shared TLS/range and audio-session recovery through real tvOS adapters locally."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import runpy
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
RUNNER_SHA256 = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
ownership = runpy.run_path(str(ROOT/'scripts/tvos-build-lock.py'))
TLS_CASE = ('app.naviamp.tvos.playback.TvosMutualTlsPlaybackIntegrationTest',
            'realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback')
RECOVERIES = {'unavailable', 'truncated', 'wrong-range'}
AUDIO_CASE = ('app.naviamp.tvos.playback.TvosAudioRecoveryIntegrationTest',
              'realAudioNotificationsRecoverCorePlaybackWithoutUnwantedAutoplay')
REMOTE_CASE = ('app.naviamp.tvos.playback.TvosAudioRecoveryIntegrationTest',
               'remoteCommandsRecoverCorePlaybackThroughTheSurfaceSession')
REMOTE_RECOVERIES = {'play-pause-toggle', 'next-previous-boundaries', 'seek-and-invalid-input',
    'shuffle-repeat', 'unsupported-favorite', 'stop-restart', 'failed-source-recovery',
    'explicit-intent-cancels-resume', 'closed-session'}
APPLE_SURFACE_CASES = {('app.naviamp.apple.media.ApplePlaybackSurfaceTest', name) for name in (
    'realMediaPlayerMetadataCommandFlagsAndImageDecodeAreClearedOnClose',
    'realAudioSessionNotificationsTranslateFactsAndObserversDoNotSurviveClose',
    'lifecycleAvailabilityAndObserverDisposalRespectTheRuntimeProtocol',
    'registeredRemoteCallbacksTranslateCommandsAndInvalidateOnClose')}
AUDIO_RECOVERIES = {'unrelated-notifications', 'allowed-interruption', 'denied-interruption', 'already-paused',
                    'route-disconnect', 'disconnect-during-interruption', 'stopped-source', 'closed-session',
                    'system-generated-lifecycle', 'application-deactivation'}


def required_native_cases():
    bass = runpy.run_path(str(ROOT/'scripts/run-tvos-bass-tests.py'))['REQUIRED_NATIVE_CASES']
    return {('app.naviamp.tvos.playback.TvosBassIntegrationTest', name) for name in bass} | {TLS_CASE, AUDIO_CASE, REMOTE_CASE} | APPLE_SURFACE_CASES


def fixture_state(root, output):
    protocol = runpy.run_path(str(root/'scripts/mobile-mtls-fixture.py'))
    bootstrap = output/'fixture.bootstrap'
    text, _, ports = protocol['read_manifest'](bootstrap)
    if protocol['request'](ports[0], '/fixture') != text:
        raise ValueError('Transport telemetry fixture identity changed')
    state = dict(line.split('=', 1) for line in protocol['request'](ports[0], '/state').splitlines() if '=' in line)
    allowed = {'bytes', 'peers', 'ranges', 'privatePeers', 'wrongHostRequests', 'unavailable', 'truncated', 'wrongRanges'}
    if set(state) != allowed:
        raise ValueError('Unexpected transport telemetry fields')
    (output/'transport-state.json').write_text(json.dumps(state, indent=2)+'\n')
    return state


def validate_native_evidence(output, require_system_context=False):
    report = json.loads((output/'native-execution/execution.json').read_text())
    if report['status'] != 'passed' or report['tests'] != len(required_native_cases()) or any(report[k] for k in ('failures', 'errors', 'skipped')):
        raise ValueError('Incomplete or failed native transport execution')
    log = (output/'native-execution/native.log').read_text()
    observed = {line.split(': ', 1)[1] for line in log.splitlines() if line.startswith('mTLS transport recovery passed: ')}
    if observed != RECOVERIES:
        raise ValueError('A required native fault/recovery phase did not execute')
    remote = {line.split(': ', 1)[1] for line in log.splitlines() if line.startswith('remote transport passed: ')}
    if remote != REMOTE_RECOVERIES or 'remote command native callbacks verified' not in log:
        raise ValueError('A required remote command/recovery phase or native callback check did not execute')
    audio = {line.split(': ', 1)[1] for line in log.splitlines() if line.startswith('audio recovery passed: ')}
    expected_audio = AUDIO_RECOVERIES if require_system_context else AUDIO_RECOVERIES - {'system-generated-lifecycle'}
    if audio != expected_audio:
        raise ValueError('A required native audio-session recovery phase did not execute')
    if 'OS27 notification availability and observer disposal verified' not in log:
        raise ValueError('Missing OS27 observer availability/disposal evidence')
    if require_system_context and not all(marker in log for marker in ('OS27 typed deactivation received: System', 'OS27 typed resumption received: Resume',
                                            'OS27 typed application deactivation verified')):
        raise ValueError('Missing system-created OS27 context evidence')
    return report


def execute(args):
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False, mode=0o700)
    report = {'passed': False, 'timings': {}, 'cleanup': {}, 'manifestSha256': args.expected_manifest_sha256,
              'runtime': args.runtime, 'audibilityVerified': False,
              'runnerSha256': RUNNER_SHA256}
    report.update(scope='os27-system-context-probe' if args.require_system_context else 'tvos-simulator',
                  os27SimulatorQualified=False, os27ContextQualified=False,
                  systemContextDelivery='unverified')
    device = fixture = coordinator = None
    root = out/'consumer'
    source = args.checkout.resolve()
    report['consumer'] = runpy.run_path(str(ROOT/'scripts/prepare-tvos-scene-consumer.py'))['prepare'](
        source, args.artifacts.resolve(), args.expected_manifest_sha256, root)
    watched = ['core/app/src/commonMain/kotlin/app/naviamp/app/NaviampProviderRandomAccessSource.kt',
        'core/domain/src/commonMain/kotlin/app/naviamp/domain/playback/CoreBassPlaybackEngine.kt',
        'core/testkit/src/commonMain/kotlin/app/naviamp/testkit/NaviampMobileMutualTlsContract.kt',
        'core/testkit/src/commonMain/kotlin/app/naviamp/testkit/NaviampAudioRecoveryContract.kt',
        'core/testkit/src/commonMain/kotlin/app/naviamp/testkit/NaviampRemoteTransportContract.kt',
        'core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCoreExternalPlaybackBridge.kt',
        'core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampExternalPlaybackSurface.kt',
        'platforms/apple-media/src/appleMain/kotlin/app/naviamp/apple/media/ApplePlaybackSurface.kt',
        'platforms/apple-media/src/appleMain/kotlin/app/naviamp/apple/media/AppleAudioSessionLifecyclePayloads.kt',
        'platforms/apple-media/src/appleMain/kotlin/app/naviamp/apple/media/AppleRemoteCommandPayloads.kt',
        'platforms/apple-media/src/nativeInterop/cinterop/include/NaviampAudioLifecycle.h',
        'platforms/apple-media/src/nativeInterop/cinterop/AudioLifecycle.def',
        'platforms/apple-media/build.gradle.kts',
        'platforms/apple-media/src/appleTest/kotlin/app/naviamp/apple/media/ApplePlaybackSurfaceTest.kt',
        'platforms/apple-media/src/appleTest/kotlin/app/naviamp/apple/media/AppleFixtureAudioSessionLifecyclePayloads.kt',
        'platforms/apple/src/appleMain/kotlin/app/naviamp/apple/playback/AppleBassPlayback.kt',
        'core/tls-testkit/src/jvmMain/kotlin/app/naviamp/testkit/NaviampMutualTlsFixture.kt',
        'core/tls-testkit/src/jvmMain/kotlin/app/naviamp/testkit/NaviampMobileMutualTlsFixture.kt',
        'platforms/tvos/src/tvosTransportTest/kotlin/app/naviamp/tvos/playback/TvosMutualTlsPlaybackIntegrationTest.kt',
        'platforms/tvos/src/tvosTransportTest/kotlin/app/naviamp/tvos/playback/TvosAudioRecoveryIntegrationTest.kt',
        'scripts/run-tvos-transport-tests.py', 'scripts/run-prebuilt-ios-native-tests.py',
        'scripts/tvos-audio-lifecycle-fixture.py', 'scripts/fixtures/tvos-audio-interrupter.m']
    report['sources'] = {p: hashlib.sha256((root/p).read_bytes()).hexdigest() for p in watched}
    env = os.environ.copy()
    env['GRADLE_OPTS'] = env.get('GRADLE_OPTS', '')+' -Dorg.gradle.daemon=false'

    def save():
        (out/'summary.json').write_text(json.dumps(report, indent=2)+'\n')

    def run(command, name, timeout):
        start = time.monotonic()
        try:
            with (out/(name+'.log')).open('x') as log:
                code = ownership['run_owned_command'](command, cwd=root, env=env, stdout=log, timeout=timeout)
            if code: raise RuntimeError(name+' exited '+str(code))
        except BaseException as error:
            report['failure'] = {'stage': name, 'type': type(error).__name__, 'message': str(error)}
            raise
        finally:
            report['timings'][name] = round(time.monotonic()-start, 3)
            save()
        print(name+' passed', flush=True)

    try:
        base = ['./gradlew', '--no-daemon', '--console=plain', '--max-workers=2',
            '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8', '-Pnaviamp.tvos.enabled=true',
            '-Pnaviamp.tvos.sceneHost=true', '-Pnaviamp.tvos.transportTests=true']
        run([*base, '--init-script', 'scripts/probe-compose-tvos-shared-ui.init.gradle', 'verifyFullSharedUiProbeVariants'], 'graph', 1200)
        (out/'consumer-variants.json').write_bytes((root/'build/full-ui-probe-variants.json').read_bytes())
        run([*base, ':core:testkit:prepareMobileMutualTlsFixture', ':platforms:tvos:linkDebugTestTvosSimulatorArm64',
             ':platforms:tvos:stageTvosSimulatorTestBassFrameworks', ':platforms:tvos:verifyTvosBassInventory',
             ':core:app:compileKotlinTvosArm64', ':core:testkit:compileKotlinTvosArm64'], 'build', args.build_timeout)
        binary = root/'platforms/tvos/build/bin/tvosSimulatorArm64/debugTest/test.kexe'
        frameworks = binary.parent/'Frameworks'
        for name, command in [('libraries', ['xcrun', 'otool', '-L', str(binary)]), ('symbols', ['xcrun', 'nm', '-u', str(binary)])]:
            text = subprocess.check_output(command, text=True, timeout=30)
            (out/(name+'.txt')).write_text(text)
            if 'bassmix' in text.lower() or '_BASS_Mixer_' in text:
                raise ValueError('Public tvOS transport test unexpectedly links BASSmix')
        report['mixerLinked'] = False
        lifecycle_fixture = runpy.run_path(str(root/'scripts/tvos-audio-lifecycle-fixture.py'))
        interrupter_app = lifecycle_fixture['build'](root, out) if args.require_system_context else None
        bootstrap = out/'fixture.bootstrap'
        with (out/'fixture.log').open('x') as log:
            fixture = subprocess.Popen([str(Path(os.environ['JAVA_HOME'])/'bin/java'),
                '@build/mobile-mtls-fixture.args', str(bootstrap)], cwd=root, stdout=log, stderr=subprocess.STDOUT)
        replay = runpy.run_path(str(root/'scripts/replay-ios-native-tls.py'))
        replay['wait_fixture_ready'](root, bootstrap, fixture, out)
        device = subprocess.check_output(['xcrun', 'simctl', 'create', 'Naviamp590Scene-transport-'+uuid.uuid4().hex[:8],
            'com.apple.CoreSimulator.SimDeviceType.Apple-TV-4K-3rd-generation-4K', args.runtime], text=True, timeout=30).strip()
        uuid.UUID(device)
        report['device'] = device
        (out/'ownership.json').write_text(json.dumps({'device': device, 'application': 'app.naviamp.tvos'})+'\n')
        run(['xcrun', 'simctl', 'boot', device], 'boot', 120)
        run(['xcrun', 'simctl', 'bootstatus', device, '-b'], 'boot-ready', 300)
        run([sys.executable, str(root/'scripts/mobile-mtls-fixture.py'), 'await-ready', str(bootstrap),
             '--readiness-report', str(out/'post-boot-readiness.json')], 'fixture-ready', 200)
        token = uuid.uuid4().hex
        if interrupter_app is not None:
            run(['xcrun', 'simctl', 'install', device, str(interrupter_app)], 'interrupter-install', 120)
            coordinator = lifecycle_fixture['Coordinator'](device, out, token)
            coordinator.start()
        native_env = {'SIMCTL_CHILD_NAVIAMP_MTLS_FIXTURE_BOOTSTRAP': bootstrap.read_text(),
            'SIMCTL_CHILD_NAVIAMP_OS27_REQUIRE_CONTEXT': '1' if args.require_system_context else '0',
            'SIMCTL_CHILD_NAVIAMP_OS27_INTERRUPTION_REQUEST': str(out/'system-interruption.request'),
            'SIMCTL_CHILD_NAVIAMP_OS27_INTERRUPTION_TOKEN': token,
            'DYLD_FRAMEWORK_PATH': str(frameworks), 'SIMCTL_CHILD_DYLD_FRAMEWORK_PATH': str(frameworks)}
        previous = {k: os.environ.get(k) for k in native_env}
        try:
            os.environ.update(native_env)
            native = runpy.run_path(str(root/'scripts/run-prebuilt-ios-native-tests.py'))
            required = required_native_cases()
            start = time.monotonic()
            native['run'](binary, device, out/'native-execution', timeout=360, required=required, tls_case=TLS_CASE, label='tvOS')
            report['timings']['native'] = round(time.monotonic()-start, 3)
            report['native'] = validate_native_evidence(out, require_system_context=args.require_system_context)
        finally:
            for key, value in previous.items():
                if value is None: os.environ.pop(key, None)
                else: os.environ[key] = value
        for path, identity in report['sources'].items():
            if hashlib.sha256((root/path).read_bytes()).hexdigest() != identity:
                raise ValueError('Source changed during native acceptance: '+path)
        report['passed'] = True
    finally:
        if coordinator is not None:
            try:
                report['systemInterruption'] = coordinator.close()
                if not report['systemInterruption']['passed']: report['passed'] = False
            except Exception as error:
                report['systemInterruptionError'] = str(error)
                report['passed'] = False
        if fixture is not None:
            if fixture.poll() is None and (out/'fixture.bootstrap').is_file():
                try: report['transport'] = fixture_state(root, out)
                except Exception as error:
                    report.setdefault('evidenceErrors', []).append(str(error))
                    report['passed'] = False
            try:
                runpy.run_path(str(root/'scripts/replay-ios-native-tls.py'))['stop_fixture'](fixture, out/'fixture.bootstrap', root)
            except Exception as error:
                report['fixtureStopError'] = str(error)
        report['cleanup'], report['cleanupErrors'] = ownership['cleanup_owned_scene'](device, fixture)
        if any(report['cleanup'].values()) or report.get('fixtureStopError'): report['passed'] = False
        save()
    if not report['passed']: raise RuntimeError('Transport test or owned resource cleanup failed')
    # Qualify only after source checks, fixture shutdown and simulator deletion succeed.
    report['os27SimulatorQualified'] = True
    report['os27ContextQualified'] = args.require_system_context
    if args.require_system_context: report['systemContextDelivery'] = 'verified'
    save()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--checkout', type=Path, default=ROOT)
    p.add_argument('--artifacts', type=Path, required=True)
    p.add_argument('--expected-manifest-sha256', required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--runtime', required=True)
    p.add_argument('--build-timeout', type=int, default=3600)
    p.add_argument('--require-system-context', action='store_true',
                   help='Diagnostic: additionally require genuine OS-created contexts; unsupported by the installed Simulator')
    args = p.parse_args()
    if sys.platform != 'darwin': p.error('The transport tests require macOS and Xcode')
    if not args.runtime.startswith('com.apple.CoreSimulator.SimRuntime.tvOS-'): p.error('A tvOS runtime is required')
    if int(args.runtime.split('tvOS-')[1].split('-')[0]) < 27: p.error('OS 27 lifecycle qualification requires a tvOS 27 or newer runtime')
    if not 60 <= args.build_timeout <= 7200: p.error('Build timeout must be 60–7200 seconds')
    with ownership['cancellation_signals'](), ownership['exclusive_build'](ROOT):
        execute(args)


if __name__ == '__main__':
    main()
