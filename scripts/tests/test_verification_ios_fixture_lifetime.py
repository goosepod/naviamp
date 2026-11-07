"""Diagnostic failures must remain visible and capture must stay on owned loopback ports."""
from pathlib import Path
import json
import re
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

HELPER = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'diagnose-ios-fixture-lifetime.py'))


class FixtureLifetimeDiagnosticsTest(unittest.TestCase):
    def test_optional_snapshot_timeout_does_not_abort_native_experiment(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / 'snapshot.log'
            with patch.dict(HELPER['diagnostic_logged'].__globals__, run_logged=Mock(
                    side_effect=subprocess.TimeoutExpired(['ps'], 10))):
                self.assertIsNone(HELPER['diagnostic_logged'](['ps'], log, Path(directory), 10))
            self.assertIn('Snapshot unavailable', log.read_text())

    def test_jvm_memory_timeout_is_optional_too(self):
        def capture(command, log, root, timeout):
            if command[0] == 'ps':
                raise subprocess.TimeoutExpired(command, timeout)
            log.write_text('123 org.gradle.launcher.daemon.bootstrap.GradleDaemon\n'
                           if str(command[0]).endswith('jps') else 'snapshot\n')
            return 0

        with tempfile.TemporaryDirectory() as directory, \
                patch.dict(HELPER['host_snapshot'].__globals__, run_logged=capture), \
                patch.dict('os.environ', JAVA_HOME=directory):
            HELPER['host_snapshot'](Path(directory), Path(directory), 'before-native')
            self.assertIn('Snapshot unavailable',
                          (Path(directory) / 'before-native-jvm-memory.log').read_text())

    def test_packet_cleanup_signals_only_its_verified_isolated_group(self):
        process = Mock(pid=12345)
        process.poll.return_value = None
        ports = [41001, 41002, 41003, 41004]
        expression = HELPER['packet_filter'](ports)
        globals_ = HELPER['packets'].__wrapped__.__globals__
        with tempfile.TemporaryDirectory() as directory, \
                patch.dict(globals_, fixture_ports=lambda _: ports), \
                patch('subprocess.Popen', return_value=process) as popen, \
                patch('os.getpgid', return_value=12345, create=True), \
                patch('subprocess.run') as run:
            with HELPER['packets'](None, Path(directory) / 'packets.log'):
                pass
            self.assertTrue(popen.call_args.kwargs['start_new_session'])
            self.assertEqual(['sudo', '-n', '/bin/kill', '-INT', '--', '-12345'], run.call_args.args[0])

    def test_packet_cleanup_refuses_an_unowned_group(self):
        process = Mock(pid=12345)
        process.poll.return_value = None
        ports = [41001, 41002, 41003, 41004]
        expression = HELPER['packet_filter'](ports)
        with tempfile.TemporaryDirectory() as directory, \
                patch.dict(HELPER['packets'].__wrapped__.__globals__, fixture_ports=lambda _: ports), \
                patch('subprocess.Popen', return_value=process), \
                patch('os.getpgid', return_value=999, create=True), \
                patch('subprocess.run') as run:
            with self.assertRaisesRegex(RuntimeError, 'ownership changed'):
                with HELPER['packets'](None, Path(directory) / 'packets.log'):
                    pass
            run.assert_not_called()

    def report(self, text):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / 'TEST-native.xml').write_text(text)
            return HELPER['read_results'](path)

    case = ('<testcase classname="app.naviamp.ios.playback.IosMutualTlsPlaybackIntegrationTest" '
            'name="realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback">{}</testcase>')

    def test_failure_is_retained_instead_of_becoming_a_pass(self):
        cases = self.report('<testsuite tests="1">' + self.case.format(
            '<failure message="fixture bootstrap timeout"/>') + '</testsuite>')
        self.assertEqual('failure', cases[0]['outcome'])
        self.assertEqual('fixture bootstrap timeout', cases[0]['message'])

    def test_skipped_native_case_is_not_a_pass(self):
        cases = self.report('<testsuite tests="1">' + self.case.format('<skipped/>') + '</testsuite>')
        self.assertEqual('skipped', cases[0]['outcome'])

    def test_missing_duplicate_and_incomplete_results_are_rejected(self):
        for text in ('<testsuite tests="0"/>',
                     '<testsuite tests="2">' + self.case.format('') + '</testsuite>',
                     '<testsuite tests="2">' + self.case.format('') * 2 + '</testsuite>'):
            with self.subTest(text=text), self.assertRaises(RuntimeError):
                self.report(text)

    def test_complete_native_pass_is_distinguished(self):
        cases = self.report('<testsuite tests="1">' + self.case.format('') + '</testsuite>')
        self.assertEqual('passed', cases[0]['outcome'])

    def test_packet_filter_cannot_expand_beyond_four_owned_loopback_ports(self):
        expected = 'tcp and host 127.0.0.1 and (port 41001 or port 41002 or port 41003 or port 41004)'
        self.assertEqual(expected, HELPER['packet_filter']([41001, 41002, 41003, 41004]))
        for ports in ([0, 1, 2, 3], [1, 1, 2, 3], [1, 2, 3], [1, 2, 3, '4 or host example.test']):
            with self.subTest(ports=ports), self.assertRaises(ValueError):
                HELPER['packet_filter'](ports)

    def test_passing_control_cannot_erase_a_failed_or_skipped_condition(self):
        passed = {'exitCode': 0, 'cases': [{'outcome': 'passed'}]}
        self.assertEqual(0, HELPER['comparison_exit_code']([passed, passed]))
        for result in ({'exitCode': 1, 'cases': [{'outcome': 'passed'}]},
                       {'exitCode': 0, 'cases': [{'outcome': 'failure'}]},
                       {'exitCode': 0, 'cases': [{'outcome': 'skipped'}]},
                       {'exitCode': 0, 'cases': []}):
            with self.subTest(result=result):
                self.assertEqual(1, HELPER['comparison_exit_code']([result, passed]))
        self.assertEqual(1, HELPER['comparison_exit_code']([]))

    def test_manual_diagnosis_cannot_displace_standard_checks_or_publish_a_receipt(self):
        workflow = (Path(__file__).resolve().parents[2] / '.github/workflows/verify.yml').read_text()
        standard, diagnostic = workflow.split('  ios-fixture-lifetime-diagnostic:', 1)
        standard_jobs = standard.split('jobs:', 1)[1]
        self.assertNotRegex(standard_jobs, r'if:.*ios_fixture_(lifetime|packet)_diagnostic')
        for context in ('Shared and Android tests', 'Desktop native tests (macOS)',
                        'Desktop native tests (Windows)', 'Desktop native tests (Linux)', 'iOS simulator tests'):
            self.assertIn('name: ' + context, standard_jobs)
        self.assertIn('name: Android emulator native tests (API ${{ matrix.api-level }})', standard_jobs)
        api_levels = json.loads(re.search(r'api-level:\s*(\[[^\]]+\])', standard_jobs).group(1))
        self.assertTrue({30, 35}.issubset(api_levels))
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.ios_fixture_lifetime_diagnostic", diagnostic)
        self.assertNotIn('Write verification evidence', diagnostic)
        self.assertNotIn('verified-source-', diagnostic)

    def test_ordinary_ios_fixture_starts_after_compilation_before_simulator_boot(self):
        workflow = (Path(__file__).resolve().parents[2] / '.github/workflows/verify.yml').read_text()
        ios = re.split(r'\n  [a-z][a-z0-9-]*:', workflow.split('\n  ios:', 1)[1], maxsplit=1)[0]
        stages = [ios.index('name: ' + stage) for stage in (
            'Compile both targets and run common simulator tests',
            'Start disposable mobile mTLS fixture',
            'Boot a dedicated simulator for native TLS services',
            'Exercise native TLS and BASS on the booted simulator')]
        self.assertEqual(sorted(stages), stages)
        self.assertEqual(1, ios.count('run: bash scripts/start-mobile-mtls-fixture.sh'))
        self.assertNotIn('GRADLE_OPTS:', ios)
        self.assertIn('GRADLE_OPTS: -Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx4g', workflow)
        for evidence in ('fixture-startup.json', 'fixture-readiness.log', 'cleanup-complete', 'fixture.jfr'):
            self.assertIn('build/ios-native-replays/run-*/' + evidence, ios)
        self.assertIn('build/mobile-mtls-run.*/fixture.jfr', ios)
        self.assertIn('NAVIAMP_MTLS_FIXTURE_JFR:', ios)
        recorder = next(line for line in ios.splitlines() if 'NAVIAMP_MTLS_FIXTURE_JFR:' in line)
        self.assertIn("github.event_name == 'workflow_dispatch'", recorder)
        self.assertNotIn('verify:ios-native-cold', recorder)
