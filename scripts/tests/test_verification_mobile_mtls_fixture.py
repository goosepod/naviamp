"""Runner endpoint selection must never reuse or stop a different fixture."""
import contextlib
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
