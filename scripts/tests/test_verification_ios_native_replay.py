"""Cold replays must fail closed for missing, cached, incomplete or failed native results."""
from pathlib import Path
import json
import runpy
import tempfile
import unittest
from unittest.mock import Mock, patch

HELPER = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'replay-ios-native-tls.py'))
OWNER = 'app.naviamp.ios.playback.IosMutualTlsPlaybackIntegrationTest'
NAME = 'realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback[iosSimulatorArm64]'


class NativeReplayResultsTest(unittest.TestCase):
    def results(self, content):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            if content is not None:
                (root / 'TEST-native.xml').write_text(content)
            return HELPER['read_results'](root)

    def suite(self, inner='', counters='tests="1" failures="0" errors="0" skipped="0"'):
        return f'<testsuite name="{OWNER}" {counters}><testcase classname="{OWNER}" name="{NAME}">{inner}</testcase></testsuite>'

    def test_requires_the_actual_tls_case(self):
        self.assertEqual(frozenset({(OWNER, NAME)}), self.results(self.suite()))
        for content in (None, '<testsuite tests="0"/>', self.suite().replace(NAME, 'unrelatedTest')):
            with self.subTest(content=content), self.assertRaises(RuntimeError):
                self.results(content)

    def test_failure_or_skip_cannot_be_reported_as_acceptance(self):
        for tag in ('failure', 'error', 'skipped'):
            with self.subTest(tag=tag), self.assertRaises(RuntimeError):
                self.results(self.suite(f'<{tag}/>'))
        with self.assertRaises(RuntimeError):
            self.results(self.suite(counters='tests="1" failures="1"'))

    def test_truncated_or_duplicate_reports_fail(self):
        with self.assertRaises(RuntimeError):
            self.results(self.suite(counters='tests="2"'))
        duplicate = self.suite().replace('</testsuite>', f'<testcase classname="{OWNER}" name="{NAME}"/></testsuite>').replace('tests="1"', 'tests="2"')
        with self.assertRaises(RuntimeError):
            self.results(duplicate)


class NativeReplayCleanupTest(unittest.TestCase):
    device_id = '12345678-1234-1234-1234-123456789abc'
    name = 'Naviamp TLS cold replay 1 owned-marker'

    def test_failed_boot_cleans_its_prepared_fixture_and_owned_device(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            results = root / 'platforms/ios/build/test-results/iosSimulatorArm64Test'
            results.mkdir(parents=True)
            (results / 'TEST-native.xml').write_text(
                f'<testsuite tests="1"><testcase classname="{OWNER}" name="{NAME}"/></testsuite>')
            output = root / 'build/replays'
            output.mkdir(parents=True)
            cleanup = Mock()
            stop = Mock()
            fixture = Mock(pid=12345)
            def simctl(*arguments):
                if arguments[0] == 'list':
                    return json.dumps({'devices': {'runtime': [{'name': 'iPhone test', 'udid': self.device_id}]}})
                if arguments[0] == 'clone':
                    return self.device_id
                raise RuntimeError('owned boot failed')
            with patch.dict(HELPER['replay'].__globals__, simctl=simctl, remove_owned_device=cleanup,
                            stop_fixture=stop, wait_fixture_ready=Mock()), \
                    patch.dict('os.environ', JAVA_HOME=directory), \
                    patch('subprocess.check_output', return_value='test-commit'), \
                    patch('subprocess.run'), patch('subprocess.Popen', return_value=fixture) as launch:
                with self.assertRaisesRegex(RuntimeError, 'owned boot failed'):
                    HELPER['replay'](root, output, 1)
                launch.assert_called_once()
                cleanup.assert_called_once()
                stop.assert_called_once_with(fixture, output / 'run-1/fixture.bootstrap', root)
            failure = json.loads((output / 'run-1/summary.json').read_text())
            self.assertEqual('simulator boot', failure['stage'])

    def test_never_removes_a_device_whose_name_changed(self):
        with tempfile.TemporaryDirectory() as directory:
            record = Path(directory) / 'device.json'
            record.write_text(json.dumps({'id': self.device_id, 'name': self.name}))
            devices = json.dumps({'devices': {'runtime': [{'udid': self.device_id, 'name': 'My iPhone'}]}})
            with patch.dict(HELPER['remove_owned_device'].__globals__, {'simctl': lambda *args: devices}), patch('subprocess.run') as run:
                with self.assertRaises(RuntimeError):
                    HELPER['remove_owned_device'](record)
                run.assert_not_called()

    def test_recovers_only_its_unique_clone_after_a_missing_rpc_result(self):
        with tempfile.TemporaryDirectory() as directory:
            record = Path(directory) / 'device.json'
            record.write_text(json.dumps({'id': None, 'name': self.name}))
            calls = []
            def simctl(*args):
                calls.append(args)
                return json.dumps({'devices': {'runtime': [{'udid': self.device_id, 'name': self.name}]}}) if args[0] == 'list' else ''
            with patch.dict(HELPER['remove_owned_device'].__globals__, {'simctl': simctl}), patch('subprocess.run') as run:
                HELPER['remove_owned_device'](record)
                self.assertEqual(('delete', self.device_id), calls[-1])
                self.assertEqual(['xcrun', 'simctl', 'shutdown', self.device_id], run.call_args.args[0])


class NativeReplayFixtureStartupTest(unittest.TestCase):
    def run_startup(self, appears_after, exit_code=None, timeout=None):
        from types import SimpleNamespace
        clock = SimpleNamespace(now=0.0)
        clock.monotonic = lambda: clock.now
        clock.sleep = lambda seconds: setattr(clock, 'now', clock.now + seconds)
        bootstrap = Mock()
        bootstrap.exists.side_effect = lambda: clock.now >= appears_after
        fixture = Mock()
        fixture.poll.return_value = exit_code
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            error = None
            with patch.dict(HELPER['wait_fixture_ready'].__globals__, time=clock), \
                    patch('subprocess.run', return_value=SimpleNamespace(returncode=0)) as probe:
                try:
                    options = {} if timeout is None else {'timeout': timeout}
                    HELPER['wait_fixture_ready'](output, bootstrap, fixture, output, **options)
                except RuntimeError as failure:
                    error = str(failure)
            return error, json.loads((output / 'fixture-startup.json').read_text()), probe.call_count

    def test_slow_certificate_generation_has_a_separate_provisioning_budget(self):
        error, report, probes = self.run_startup(95)
        self.assertIsNone(error)
        self.assertEqual(180, report['budgetSeconds'])
        self.assertEqual(95, report['bootstrapSeconds'])
        self.assertEqual('ready', report['status'])
        self.assertEqual(1, probes)

    def test_missing_bootstrap_still_fails_at_its_bounded_setup_deadline(self):
        error, report, probes = self.run_startup(95, timeout=1)
        self.assertEqual('Owned fixture did not become ready', error)
        self.assertEqual('failed', report['status'])
        self.assertIsNone(report['bootstrapSeconds'])
        self.assertEqual(1, report['seconds'])
        self.assertEqual(0, probes)

    def test_dead_fixture_fails_without_retrying_a_native_suite(self):
        error, report, probes = self.run_startup(0, exit_code=1)
        self.assertEqual('Owned fixture exited during startup', error)
        self.assertEqual('failed', report['status'])
        self.assertEqual(0, probes)
