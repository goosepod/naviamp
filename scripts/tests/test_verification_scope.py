import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'classify-verification-scope.sh'


class VerificationScopeTest(unittest.TestCase):
    def test_workflow_steps_do_not_duplicate_conditions(self):
        workflow = SCRIPT.parents[1] / '.github/workflows/verify.yml'
        conditions = 0
        for line in workflow.read_text().splitlines():
            if line.startswith('      - '):
                conditions = 0
            if line.startswith('        if:'):
                conditions += 1
                self.assertLessEqual(conditions, 1, line)

    def test_classifies_real_history_and_rejects_missing_history(self):
        with tempfile.TemporaryDirectory() as directory:
            def git(*args):
                return subprocess.check_output(['git', *args], cwd=directory, text=True).strip()
            git('init', '-q')
            git('config', 'user.email', 'fixture@example.test')
            git('config', 'user.name', 'Fixture')
            git('commit', '--allow-empty', '-qm', 'baseline')
            base = git('rev-parse', 'HEAD')
            def classify(head, event='pull_request'):
                return subprocess.run(['bash', str(SCRIPT)], cwd=directory, text=True,
                    capture_output=True, env={**os.environ, 'GITHUB_EVENT_NAME': event,
                                             'BASE_SHA': base, 'HEAD_SHA': head})
            for filename, expected in [('README.md', 'false'), ('docs/guide.md', 'false'),
                                       ('readme-assets/image.png', 'false'), ('core/source.kt', 'true'),
                                       ('.github/workflows/verify.yml', 'true'),
                                       ('README.md\nsource.kt', 'true')]:
                with self.subTest(filename=filename):
                    git('reset', '--hard', base)
                    git('clean', '-fdq')
                    path = Path(directory) / filename
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text('fixture\n')
                    git('add', '.')
                    git('commit', '-qm', 'change')
                    result = classify(git('rev-parse', 'HEAD'))
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertEqual(f'run_full={expected}\n', result.stdout)
            result = classify('missing-commit')
            self.assertNotEqual(0, result.returncode)
            self.assertEqual('', result.stdout)
            self.assertEqual('run_full=true\n', classify('missing-commit', 'workflow_call').stdout)


if __name__ == '__main__':
    unittest.main()
