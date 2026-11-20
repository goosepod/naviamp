"""Transport acceptance cannot be satisfied by old/skipped cases or missing recovery phases."""
import json
from pathlib import Path
import runpy
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
runner = runpy.run_path(str(ROOT/'scripts/run-tvos-transport-tests.py'))
native = runpy.run_path(str(ROOT/'scripts/run-prebuilt-ios-native-tests.py'))


class TvosTransportEvidenceTest(unittest.TestCase):
    def test_simulator_acceptance_cannot_claim_genuine_context_qualification(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary); (out/'native-execution').mkdir()
            (out/'native-execution/execution.json').write_text(json.dumps(dict(status='passed', tests=11, failures=0, errors=0, skipped=0)))
            phases = ['mTLS transport recovery passed: '+s for s in runner['RECOVERIES']]
            phases += ['audio recovery passed: '+s for s in runner['AUDIO_RECOVERIES'] - {'system-generated-lifecycle'}]
            phases += ['remote transport passed: '+name for name in sorted(runner['REMOTE_RECOVERIES'])]
            phases += ['remote command native callbacks verified']
            phases += ['OS27 notification availability and observer disposal verified']
            log = out/'native-execution/native.log'
            log.write_text('\n'.join(phases))
            self.assertEqual(runner['validate_native_evidence'](out)['status'], 'passed')
            with self.assertRaises(ValueError): runner['validate_native_evidence'](out, True)
            for missing in range(len(phases)):
                log.write_text('\n'.join(phase for index, phase in enumerate(phases) if index != missing))
                with self.subTest(missing=phases[missing]), self.assertRaises(ValueError):
                    runner['validate_native_evidence'](out)

    def test_system_audio_fixture_rejects_another_runs_request_before_launching_any_app(self):
        fixture = runpy.run_path(str(ROOT/'scripts/tvos-audio-lifecycle-fixture.py'))
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary)
            coordinator = fixture['Coordinator']('00000000-0000-0000-0000-000000000001', out, 'owned-token')
            coordinator.request.write_text('another-run')
            with mock.patch('subprocess.Popen') as launch:
                coordinator.start(); coordinator.thread.join(timeout=2)
                result = coordinator.close()
                launch.assert_not_called()
            self.assertFalse(result['passed'])
            self.assertIn('Unowned system-interruption request', result['errors'])

    def test_closing_a_fixture_before_request_cannot_leave_an_app_launch_pending(self):
        fixture = runpy.run_path(str(ROOT/'scripts/tvos-audio-lifecycle-fixture.py'))
        with tempfile.TemporaryDirectory() as temporary, mock.patch('subprocess.Popen') as launch:
            coordinator = fixture['Coordinator']('00000000-0000-0000-0000-000000000001', Path(temporary), 'owned')
            coordinator.start(); result = coordinator.close()
            launch.assert_not_called()
            self.assertFalse(result['passed'])
            self.assertFalse(coordinator.thread.is_alive())

    def test_non_tls_execution_requires_explicit_provenance_without_weakening_tls_defaults(self):
        expected = {('AudioAdapterTest', 'case')}
        capture = """audio lifecycle native adapter executed
##teamcity[testSuiteStarted name='AudioAdapterTest']
##teamcity[testStarted name='case']
##teamcity[testFinished name='case' duration='1']
##teamcity[testSuiteFinished name='AudioAdapterTest']
"""
        self.assertEqual(native['inventory']('AudioAdapterTest.\n  case\n', None), expected)
        self.assertEqual(native['results'](capture, expected, 'audio lifecycle native adapter executed'), {('AudioAdapterTest', 'case'): 0.001})
        with self.assertRaises(RuntimeError): native['inventory']('AudioAdapterTest.\n  case\n')
        with self.assertRaises(RuntimeError): native['results'](capture, expected)

    def test_counter_capture_requires_the_owned_fixture_and_rejects_unexpected_fields(self):
        state = dict(bytes='128', peers='CN=Naviamp Test Client', ranges='bytes=0-127', privatePeers='',
                     wrongHostRequests='0', unavailable='2', truncated='2', wrongRanges='2')
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary)
            protocol = {'read_manifest': mock.Mock(return_value=('owned-bootstrap', {}, [12345])),
                        'request': mock.Mock(side_effect=['owned-bootstrap', '\n'.join(k+'='+v for k,v in state.items())])}
            with mock.patch('runpy.run_path', return_value=protocol):
                self.assertEqual(runner['fixture_state'](ROOT, out), state)
            (out/'transport-state.json').unlink()
            protocol['request'].side_effect = ['another-fixture']
            with mock.patch('runpy.run_path', return_value=protocol), self.assertRaisesRegex(ValueError, 'identity changed'):
                runner['fixture_state'](ROOT, out)
            protocol['request'].side_effect = ['owned-bootstrap', 'password=must-not-be-recorded']
            with mock.patch('runpy.run_path', return_value=protocol), self.assertRaisesRegex(ValueError, 'Unexpected'):
                runner['fixture_state'](ROOT, out)
            self.assertFalse((out/'transport-state.json').exists())

    def test_tvos_inventory_requires_its_own_native_tls_case_and_preserves_ios_default(self):
        owner, case = runner['TLS_CASE']
        listing = f'{owner}.\n  {case}\n'
        self.assertEqual(native['inventory'](listing, (owner, case)), {(owner, case)})
        with self.assertRaises(RuntimeError): native['inventory'](listing)
        with self.assertRaises(RuntimeError): native['inventory']('other.Suite.\n  other\n', (owner, case))

    def test_every_fault_must_recover_in_a_successful_fresh_native_execution(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            execution = root/'native-execution'
            execution.mkdir()
            report = dict(status='passed', tests=11, failures=0, errors=0, skipped=0)
            path = execution/'execution.json'
            path.write_text(json.dumps(report))
            log = execution/'native.log'
            phases = ['mTLS transport recovery passed: '+name for name in sorted(runner['RECOVERIES'])]
            phases += ['audio recovery passed: '+name for name in sorted(runner['AUDIO_RECOVERIES'])]
            phases += ['remote transport passed: '+name for name in sorted(runner['REMOTE_RECOVERIES'])]
            phases += ['remote command native callbacks verified']
            phases += ['OS27 notification availability and observer disposal verified', 'OS27 typed deactivation received: System', 'OS27 typed resumption received: Resume',
                       'OS27 typed application deactivation verified']
            log.write_text('\n'.join(phases))
            self.assertEqual(runner['validate_native_evidence'](root, True), report)
            for field, value in [('status', 'failed'), ('tests', 8), ('failures', 1), ('errors', 1), ('skipped', 1)]:
                path.write_text(json.dumps(dict(report, **{field: value})))
                with self.subTest(field=field), self.assertRaises(ValueError): runner['validate_native_evidence'](root, True)
            path.write_text(json.dumps(report))
            for missing in range(len(phases)):
                log.write_text('\n'.join(phase for index, phase in enumerate(phases) if index != missing))
                with self.subTest(missing=missing), self.assertRaises(ValueError): runner['validate_native_evidence'](root, True)

    def test_required_inventory_contains_real_audio_recovery_and_native_notification_translation(self):
        cases = runner['required_native_cases']()
        self.assertEqual(len(cases), 11)
        self.assertIn(runner['AUDIO_CASE'], cases)
        self.assertIn(runner['REMOTE_CASE'], cases)
        self.assertIn(runner['TLS_CASE'], cases)
        self.assertTrue(runner['APPLE_SURFACE_CASES'] <= cases)
