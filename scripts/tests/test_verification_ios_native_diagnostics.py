"""Process snapshots must never target another checkout or an enclosing shell."""
from pathlib import Path
from contextlib import contextmanager
import json
import runpy
import shlex
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

HELPER = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'ios-native-test-diagnostics.py'))


class NativeDiagnosticOwnershipTest(unittest.TestCase):
    def test_slow_process_inventory_is_bounded_and_does_not_abort_observation(self):
        with patch('subprocess.check_output', side_effect=subprocess.TimeoutExpired(['ps'], 5)) as inventory:
            self.assertEqual(set(), HELPER['processes']('/owned/test.kexe'))
        self.assertEqual(5, inventory.call_args.kwargs['timeout'])

    def test_manual_capture_retains_native_failure_even_if_observer_cleanup_fails(self):
        @contextmanager
        def observer(bootstrap, output):
            yield
            raise RuntimeError('observer cleanup failed')

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'diagnostics'
            arguments = ['diagnostics', '--executable', str(Path(directory) / 'test.kexe'),
                         '--fixture-bootstrap', str(Path(directory) / 'fixture.bootstrap'),
                         '--output', str(output), '--capture-loopback', '--', 'native-command']
            with patch.object(sys, 'argv', arguments), \
                    patch.dict(HELPER['main'].__globals__, observe=lambda *args: 7), \
                    patch('runpy.run_path', return_value={'packets': observer}):
                with self.assertRaisesRegex(RuntimeError, 'observer cleanup failed'):
                    HELPER['main']()
            self.assertEqual({'exitCode': 7}, json.loads((output / 'native-command-result.json').read_text()))

    def test_manual_capture_requires_owned_fixture(self):
        arguments = ['diagnostics', '--executable', 'test.kexe', '--output', 'unused',
                     '--capture-loopback', '--', 'native-command']
        with patch.object(sys, 'argv', arguments), self.assertRaises(SystemExit) as error:
            HELPER['main']()
        self.assertEqual(2, error.exception.code)

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
