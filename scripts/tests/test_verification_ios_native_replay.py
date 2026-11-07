"""Cold replays must fail closed for missing, cached, incomplete or failed native results."""
from pathlib import Path
import json
import runpy
import tempfile
import unittest
from unittest.mock import patch

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
