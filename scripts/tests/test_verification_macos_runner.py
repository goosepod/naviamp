"""Routing must preserve hosted defaults and reject ambiguous/injected local selectors."""
from pathlib import Path
import runpy
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SELECT = runpy.run_path(str(ROOT / 'scripts/select-verification-macos-runner.py'))['select_runner']
LABEL = 'naviamp-local-12345678123412341234123456789abc'


class MacosRunnerRoutingTest(unittest.TestCase):
    def test_only_manual_dispatch_can_select_local_execution(self):
        for event in ('pull_request', 'push', 'workflow_call', ''):
            self.assertEqual('macos-latest', SELECT(event, LABEL))
        self.assertEqual('macos-latest', SELECT('workflow_dispatch', ''))
        self.assertEqual(['self-hosted', 'macOS', 'ARM64', LABEL], SELECT('workflow_dispatch', LABEL))

    def test_invalid_or_injected_labels_fail_closed(self):
        for label in ('macos-latest', 'self-hosted', 'naviamp-local-1', LABEL + '\n',
                      LABEL + '","other', LABEL.upper(), ' ' + LABEL):
            with self.subTest(label=label), self.assertRaises(ValueError):
                SELECT('workflow_dispatch', label)

    def test_both_macos_jobs_use_the_validated_classification_output(self):
        workflow = (ROOT / '.github/workflows/verify.yml').read_text()
        self.assertEqual(2, workflow.count('runs-on: ${{ fromJSON(needs.changes.outputs.macos_runner) }}'))
        self.assertIn('python3 scripts/select-verification-macos-runner.py >> "$GITHUB_OUTPUT"', workflow)
        self.assertIn('macos_runner: ${{ steps.classify.outputs.macos_runner }}', workflow)
        self.assertNotIn('inputs.source_ref || github.ref', workflow)
        self.assertIn('inputs.source_ref || github.sha', workflow)
        self.assertIn("github.event_name == 'workflow_dispatch'", workflow)
        self.assertIn('SOURCE_HEAD_SHA: ${{ github.event.pull_request.head.sha || github.sha }}', workflow)
        # Keep the full native contract, production app and Keychain steps in the real Actions job.
        for command in (':platforms:ios:iosSimulatorArm64Test', 'xcodebuild build -quiet',
                        '-scheme NaviampTests', 'name: Record verified source tree'):
            self.assertIn(command, workflow)

    def test_older_release_recovery_without_helper_stays_hosted_and_manual_fails_closed(self):
        workflow = (ROOT / '.github/workflows/verify.yml').read_text()
        start = workflow.index('          if [ -f scripts/select-verification-macos-runner.py ]; then')
        end = workflow.index('          scripts/classify-verification-scope.sh', start)
        block = '\n'.join(line[10:] for line in workflow[start:end].splitlines())
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'output'
            for event in ('workflow_call', 'workflow_dispatch'):
                output.write_text('')
                result = subprocess.run(['bash', '-e', '-c', block], cwd=directory, capture_output=True,
                    env={**os.environ, 'GITHUB_EVENT_NAME': event, 'MACOS_RUNNER_LABEL': '', 'GITHUB_OUTPUT': str(output)})
                self.assertEqual(event == 'workflow_dispatch', result.returncode != 0)
                self.assertEqual('macos_runner="macos-latest"\n' if event == 'workflow_call' else '', output.read_text())
