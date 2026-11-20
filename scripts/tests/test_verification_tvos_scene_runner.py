"""Exercise failed scene evidence and teardown without allocating native resources."""
import contextlib
import io
import json
import os
from pathlib import Path
import runpy
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
api = runpy.run_path(str(ROOT/'scripts/run-tvos-scene-tests.py'))


@unittest.skipUnless(os.name == 'posix', 'Checkout fixture uses POSIX directory links')
class FailedSceneEvidenceTest(unittest.TestCase):
    def failure_case(self, collection_fails=False):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)/'checkout'
            root.mkdir()
            for name in ('apps', 'core', 'platforms', 'scripts'):
                (root/name).symlink_to(ROOT/name, target_is_directory=True)
            (root/'build').mkdir()
            (root/'build/full-ui-probe-variants.json').write_text('{}')
            out = Path(temporary)/'evidence'
            device = '11111111-2222-3333-4444-555555555555'
            args = SimpleNamespace(checkout=root, output=out, artifacts=None,
                expected_manifest_sha256='a'*64, build_timeout=60, runtime='unused',
                ui_repository=root/'ui', resources_repository=root/'resources', artifact_manifest=root/'manifest')
            fixture = mock.Mock()
            fixture.stdout.readline.return_value = '{"port":18790}\n'
            cleanup = mock.Mock(return_value=({'fixture': 0, 'shutdown': 0, 'delete': 0}, []))

            def command(command, **kwargs):
                if command[:2] == ['xcodebuild', 'test-without-building']:
                    (out/'scene.xcresult').mkdir()
                    return 65
                if command[:4] == ['xcrun', 'xcresulttool', 'export', 'attachments']:
                    (out/'scene-attachments').mkdir()
                return 0

            def output(command, **kwargs):
                if command[:3] == ['xcrun', 'simctl', 'create']:
                    return device+'\n'
                if collection_fails:
                    raise subprocess.CalledProcessError(1, command)
                return json.dumps({'totalTestCount': 1, 'failedTests': 1})

            with mock.patch.dict(api['ownership'], run_owned_command=command, cleanup_owned_scene=cleanup), \
                    mock.patch('subprocess.Popen', return_value=fixture), \
                    mock.patch('subprocess.check_output', side_effect=output), \
                    mock.patch('subprocess.run', return_value=subprocess.CompletedProcess([], 0)), \
                    mock.patch('selectors.DefaultSelector') as selector, contextlib.redirect_stdout(io.StringIO()):
                selector.return_value.__enter__.return_value.select.return_value = [True]
                with self.assertRaisesRegex(RuntimeError, 'scene failed'):
                    api['run_scene'](args)
            cleanup.assert_called_once_with(device, fixture)
            report = json.loads((out/'summary.json').read_text())
            self.assertFalse(report['passed'])
            self.assertEqual(report['failure'], {'stage': 'scene', 'reason': 'exit', 'returncode': 65})
            self.assertEqual(report['cleanup']['delete'], 0)
            if collection_fails:
                self.assertEqual(len(report['evidenceErrors']), 1)
            else:
                self.assertEqual(report['tests']['scene']['failedTests'], 1)
                self.assertTrue((out/'scene-attachments').is_dir())

    def test_failed_case_retains_results_and_attachments_then_cleans_resources(self):
        self.failure_case()

    def test_collection_error_preserves_the_original_failure_and_still_cleans_resources(self):
        self.failure_case(collection_fails=True)
