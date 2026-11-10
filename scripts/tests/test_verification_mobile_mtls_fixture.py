"""Runner endpoint selection must never reuse or stop a different fixture."""
import contextlib
import json
from types import SimpleNamespace
import io
from pathlib import Path
import runpy
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'mobile-mtls-fixture.py'
HELPER = runpy.run_path(str(SCRIPT))


class MobileTlsFixtureRunnerTest(unittest.TestCase):
    def run_helper(self, action, manifest, request):
        with tempfile.TemporaryDirectory() as directory:
            bootstrap = Path(directory) / 'fixture.bootstrap'
            bootstrap.write_text(manifest)
            output = io.StringIO()
            with patch.object(sys, 'argv', [str(SCRIPT), action, str(bootstrap)]), contextlib.redirect_stdout(output):
                # The loaded main retains its own module globals.
                with patch.dict(HELPER['main'].__globals__, {'request': request}):
                    HELPER['main']()
            return output.getvalue()

    manifest = 'controlPort=41001\nmutualPort=41002\nprivateCaPort=41003\nwrongHostPort=41004\nstopToken=run-a\n'

    def test_routing_uses_all_four_published_ports_without_network(self):
        def unexpected(*args):
            self.fail('Routing must not probe or reserve replacement ports')
        self.assertEqual('41001\n41002\n41003\n41004\n', self.run_helper('ports', self.manifest, unexpected))
        self.assertEqual('41001\n', self.run_helper('control-port', self.manifest, unexpected))

    def test_stop_requires_matching_bootstrap_and_run_token(self):
        calls = []
        def request(port, route):
            calls.append((port, route))
            return self.manifest if route == '/fixture' else 'stopping'
        self.run_helper('stop', self.manifest, request)
        self.assertEqual([(41001, '/fixture'), (41001, '/stop?token=run-a')], calls)

    def test_recycled_port_is_not_stopped(self):
        calls = []
        def request(port, route):
            calls.append(route)
            return self.manifest.replace('run-a', 'run-b')
        with self.assertRaisesRegex(ValueError, 'identity'):
            self.run_helper('stop', self.manifest, request)
        self.assertEqual(['/fixture'], calls)

    def test_invalid_or_overlapping_ports_fail_before_network(self):
        for port in ('0', '-1', '65536', '41001', 'bad'):
            with self.subTest(port=port), self.assertRaises(ValueError):
                self.run_helper('ready', self.manifest.replace('mutualPort=41002', 'mutualPort=' + port), lambda *_: self.fail('Invalid endpoints reached network'))


class PostBootFixtureReadinessTest(unittest.TestCase):
    def run_readiness(self, response, timeout=180):
        clock = SimpleNamespace(now=0.0)
        calls = []
        def request(port, route, timeout):
            calls.append((port, route, timeout))
            return response(clock, len(calls))
        with tempfile.TemporaryDirectory() as directory:
            bootstrap = Path(directory) / 'fixture.bootstrap'
            bootstrap.write_text(MobileTlsFixtureRunnerTest.manifest)
            report = Path(directory) / 'ready.json'
            with patch.dict(HELPER['await_ready'].__globals__, request=request), \
                    patch('time.monotonic', side_effect=lambda: clock.now), \
                    patch('time.sleep', side_effect=lambda seconds: setattr(clock, 'now', clock.now + seconds)):
                try:
                    HELPER['await_ready'](bootstrap, report, timeout)
                finally:
                    self.report = json.loads(report.read_text())
                    self.calls = calls

    def test_transient_host_transport_is_provisioned_without_replaying_native_tests(self):
        def response(clock, attempt):
            if attempt == 1:
                clock.now += 5
                raise TimeoutError('host response unavailable')
            return MobileTlsFixtureRunnerTest.manifest
        self.run_readiness(response)
        self.assertEqual('ready', self.report['status'])
        self.assertEqual(['transport unavailable', 'ready'], [x['outcome'] for x in self.report['attempts']])
        self.assertTrue(all(port == 41001 and route == '/fixture' for port, route, _ in self.calls))

    def test_setup_timeout_remains_bounded_and_preserves_failed_attempts(self):
        def response(clock, attempt):
            clock.now += 1
            raise TimeoutError('unavailable')
        with self.assertRaises(TimeoutError):
            self.run_readiness(response, timeout=1)
        self.assertEqual('failed', self.report['status'])
        self.assertEqual(1, self.report['seconds'])
        self.assertEqual(1, self.calls[0][2])

    def test_wrong_fixture_identity_fails_immediately_without_retry(self):
        with self.assertRaisesRegex(ValueError, 'identity'):
            self.run_readiness(lambda *_: MobileTlsFixtureRunnerTest.manifest.replace('run-a', 'another-run'))
        self.assertEqual(1, len(self.calls))
        self.assertEqual('wrong identity', self.report['attempts'][0]['outcome'])

    def test_old_readiness_evidence_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'ready.json'
            report.write_text('original evidence')
            with self.assertRaisesRegex(ValueError, 'overwrite'):
                HELPER['await_ready'](Path(directory) / 'missing-bootstrap', report)
            self.assertEqual('original evidence', report.read_text())
