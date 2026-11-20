import os
from pathlib import Path
import runpy
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

api = runpy.run_path(str(Path(__file__).resolve().parents[1]/'tvos-build-lock.py'))


@unittest.skipUnless(os.name == 'posix', 'Native build ownership uses POSIX process identities')
class NativeBuildOwnershipTest(unittest.TestCase):
    def test_sigterm_unwinds_command_cleanup_before_cli_exits(self):
        with tempfile.TemporaryDirectory() as temporary:
            child_file = Path(temporary)/'child'
            child = ('import os,time; from pathlib import Path; '
                     f'Path({str(child_file)!r}).write_text(str(os.getpid())); time.sleep(60)')
            controller = ('import os,runpy,subprocess,sys\n'
                          f'api=runpy.run_path({str(Path(__file__).resolve().parents[1]/"tvos-build-lock.py")!r})\n'
                          'with api["cancellation_signals"]():\n'
                          f' api["run_owned_command"]([sys.executable,"-c",{child!r}], '
                          f'cwd={temporary!r}, env=os.environ.copy(), stdout=subprocess.DEVNULL, timeout=30)\n')
            process = subprocess.Popen([sys.executable, '-c', controller])
            try:
                deadline = time.monotonic()+5
                while not child_file.exists() and time.monotonic()<deadline: time.sleep(.05)
                self.assertTrue(child_file.exists())
                process.terminate()
                self.assertEqual(process.wait(timeout=10), 128+signal.SIGTERM)
                state = subprocess.run(['ps', '-p', child_file.read_text(), '-o', 'stat='], capture_output=True, text=True)
                self.assertTrue(state.returncode != 0 or state.stdout.strip().startswith('Z'))
            finally:
                if process.poll() is None: process.kill()
                process.wait(timeout=5)

    def test_timeout_stops_an_owned_command_and_detached_child(self):
        with tempfile.TemporaryDirectory() as temporary:
            child_file = Path(temporary)/'child'
            code = ('import subprocess,sys,time; from pathlib import Path; '
                    'p=subprocess.Popen([sys.executable,"-c","import time; time.sleep(60)"], start_new_session=True); '
                    f'Path({str(child_file)!r}).write_text(str(p.pid)); time.sleep(60)')
            with self.assertRaises(subprocess.TimeoutExpired):
                api['run_owned_command']([sys.executable, '-c', code], cwd=temporary,
                                         env=os.environ.copy(), stdout=subprocess.DEVNULL, timeout=2)
            self.assertTrue(child_file.exists())
            state = subprocess.run(['ps', '-p', child_file.read_text(), '-o', 'stat='], capture_output=True, text=True)
            self.assertTrue(state.returncode != 0 or state.stdout.strip().startswith('Z'))

    def test_cleanup_stops_owned_children_and_preserves_an_unrelated_process(self):
        with tempfile.TemporaryDirectory() as temporary:
            child_file = Path(temporary)/'child'
            child = 'import time; time.sleep(60)'
            code = ('import subprocess,sys,time; from pathlib import Path; '
                    f'p=subprocess.Popen([sys.executable,"-c",{child!r}], start_new_session=True); '
                    f'Path({str(child_file)!r}).write_text(str(p.pid)); time.sleep(60)')
            parent = subprocess.Popen([sys.executable, '-c', code])
            unrelated = subprocess.Popen([sys.executable, '-c', child])
            try:
                deadline = time.monotonic()+5
                while not child_file.exists() and time.monotonic()<deadline: time.sleep(.05)
                self.assertTrue(child_file.exists())
                child_pid = int(child_file.read_text())
                api['terminate_tree']([parent.pid])
                parent.wait(timeout=5)
                state = subprocess.run(['ps','-p',str(child_pid),'-o','stat='], capture_output=True, text=True)
                self.assertTrue(state.returncode != 0 or state.stdout.strip().startswith('Z'))
                self.assertIsNone(unrelated.poll())
            finally:
                for process in (parent, unrelated):
                    if process.poll() is None: process.kill()
                    process.wait(timeout=5)


class SceneCleanupTest(unittest.TestCase):
    @unittest.skipUnless(os.name == 'posix', 'POSIX CLI signals')
    def test_signal_guard_restores_the_callers_handler(self):
        previous = signal.getsignal(signal.SIGTERM)
        with api['cancellation_signals']():
            self.assertIsNot(signal.getsignal(signal.SIGTERM), previous)
        self.assertIs(signal.getsignal(signal.SIGTERM), previous)

    def test_keyboard_interrupt_cleans_both_command_tree_and_consumer(self):
        command = api['run_owned_command']
        process = mock.Mock(pid=123)
        process.wait.side_effect = [KeyboardInterrupt(), None]
        with mock.patch('subprocess.Popen', return_value=process), mock.patch.dict(command.__globals__,
                terminate_tree=mock.Mock(), terminate_consumer_gradle=mock.Mock()):
            with self.assertRaises(KeyboardInterrupt):
                command(['xcodebuild'], cwd=Path('/owned'), env={}, stdout=subprocess.DEVNULL, timeout=10)
            command.__globals__['terminate_tree'].assert_called_once_with([123])
            command.__globals__['terminate_consumer_gradle'].assert_called_once_with(Path('/owned'))

    def test_fixture_timeout_escalates_and_still_deletes_simulator(self):
        fixture = mock.Mock()
        fixture.wait.side_effect = [subprocess.TimeoutExpired('fixture', 10), None]
        with mock.patch('subprocess.check_output', return_value='{"devices":{"tv":[{"udid":"owned","state":"Booted"}]}}'), \
                mock.patch('subprocess.run', return_value=subprocess.CompletedProcess([], 0)) as run:
            result, errors = api['cleanup_owned_scene']('owned', fixture)
        fixture.kill.assert_called_once()
        self.assertEqual(result, dict(fixture=0, shutdown=0, delete=0))
        self.assertFalse(errors)
        self.assertEqual([c.args[0] for c in run.call_args_list], [
            ['xcrun', 'simctl', 'shutdown', 'owned'], ['xcrun', 'simctl', 'delete', 'owned']])

    def test_fixture_failure_and_inspection_failure_do_not_skip_simulator_cleanup(self):
        fixture = mock.Mock()
        fixture.terminate.side_effect = OSError('fixture unavailable')
        with mock.patch('subprocess.check_output', side_effect=OSError('inspection unavailable')), \
                mock.patch('subprocess.run', return_value=subprocess.CompletedProcess([], 0)) as run:
            result, errors = api['cleanup_owned_scene']('owned', fixture)
        self.assertEqual(result, dict(fixture=1, shutdown=0, delete=0))
        self.assertEqual(len(errors), 2)
        self.assertEqual([c.args[0][-2:] for c in run.call_args_list], [['shutdown', 'owned'], ['delete', 'owned']])

    def test_shutdown_timeout_still_attempts_delete_and_records_failure(self):
        with mock.patch('subprocess.check_output', side_effect=OSError('inspection unavailable')), \
                mock.patch('subprocess.run', side_effect=[subprocess.TimeoutExpired('shutdown', 30),
                           subprocess.CompletedProcess([], 0)]) as run:
            result, errors = api['cleanup_owned_scene']('owned', None)
        self.assertEqual(result, dict(shutdown=1, delete=0))
        self.assertEqual(run.call_args_list[-1].args[0], ['xcrun', 'simctl', 'delete', 'owned'])
        self.assertEqual(len(errors), 2)

    def test_already_deleted_device_does_not_touch_other_simulators(self):
        with mock.patch('subprocess.check_output', return_value='{"devices":{"tv":[{"udid":"unrelated","state":"Booted"}]}}'), \
                mock.patch('subprocess.run') as run:
            result, errors = api['cleanup_owned_scene']('owned', None)
        self.assertEqual(result, dict(shutdown=0, delete=0))
        self.assertFalse(errors)
        run.assert_not_called()
