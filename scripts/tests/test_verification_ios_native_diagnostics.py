"""Process snapshots must never target another checkout or an enclosing shell."""
from pathlib import Path
import runpy
import shlex
import sys
import tempfile
import unittest

HELPER = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'ios-native-test-diagnostics.py'))


class NativeDiagnosticOwnershipTest(unittest.TestCase):
    def test_fixture_pid_must_still_belong_to_this_bootstrap(self):
        with tempfile.TemporaryDirectory() as directory:
            bootstrap = Path(directory) / 'fixture-run' / 'fixture.bootstrap'
            valid = '/jdk/bin/java @build/mobile-mtls-fixture.args ' + shlex.quote(str(bootstrap.resolve()))
            self.assertTrue(HELPER['owns_fixture'](valid, bootstrap))
            self.assertFalse(HELPER['owns_fixture'](valid.replace('fixture-run', 'another-run'), bootstrap))
            self.assertFalse(HELPER['owns_fixture']('/bin/sh -c ' + valid, bootstrap))
            self.assertFalse(HELPER['owns_fixture'](valid.replace('mobile-mtls-fixture.args', 'other.args'), bootstrap))

    def test_selects_only_the_exact_native_executable(self):
        executable = '/work/naviamp/platforms/ios/test.kexe'
        table = '\n'.join([
            '11 ' + executable,
            '12 /other/naviamp/platforms/ios/test.kexe --test',
            '13 /usr/bin/simctl spawn owned-device ' + executable,
            '14 /bin/sh -c ' + executable,
            '15 ' + executable + '.old --test',
            '16 ' + executable + ' copy',
        ])
        self.assertEqual({11}, HELPER['matching_pids'](table, executable))

    def test_preserves_failing_test_exit_status(self):
        with tempfile.TemporaryDirectory() as directory:
            from unittest.mock import patch
            with patch.dict(HELPER['observe'].__globals__, {'processes': lambda _: set()}):
                result = HELPER['observe'](
                    [sys.executable, '-c', 'raise SystemExit(7)'],
                    str(Path(directory) / 'unrelated-test.kexe'), Path(directory) / 'diagnostics',
                )
        self.assertEqual(7, result)
