import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'classify-verification-scope.sh'


class VerificationScopeTest(unittest.TestCase):
    def test_pre_classifier_release_recovery_requires_full_verification(self):
        workflow = (SCRIPT.parents[1] / '.github/workflows/verify.yml').read_text()
        start = workflow.index('          if [ -f scripts/classify-verification-scope.sh ]; then')
        end = workflow.index('\n\n  shared-android:', start)
        command = '\n'.join(line[10:] for line in workflow[start:end].splitlines())
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'outputs'
            for event, source, allowed in [('workflow_call', 'v2.10.0', True),
                                           ('push', 'v2.10.0', True),
                                           ('pull_request', 'v2.10.0', False),
                                           ('workflow_call', '', True),
                                           ('pull_request', '', False)]:
                with self.subTest(event=event, source=source):
                    output.write_text('')
                    result = subprocess.run(['bash', '-c', command], cwd=directory,
                        text=True, capture_output=True, env={**os.environ,
                            'GITHUB_EVENT_NAME': event, 'SOURCE_REF': source,
                            'GITHUB_OUTPUT': str(output)})
                    self.assertEqual(allowed, result.returncode == 0, result.stderr)
                    self.assertEqual('run_full=true\nrun_translations=false\n' if allowed else '',
                        output.read_text())

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
            locale = Path(directory) / 'core/ui/src/commonMain/composeResources/values-de/strings.xml'
            locale.parent.mkdir(parents=True)
            locale.write_text('<resources/>')
            git('add', '.')
            git('commit', '-qm', 'baseline')
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
                    self.assertEqual(f'run_full={expected}\nrun_translations=false\n', result.stdout)
            locale_path = 'core/ui/src/commonMain/composeResources/values-de/strings.xml'
            for operation, full, translations in [
                ('modify', False, True), ('mixed', True, True),
                ('docs', False, True), ('add', True, False),
                ('delete', True, False), ('rename', True, False),
                ('source', True, False),
            ]:
                with self.subTest(operation=operation):
                    git('reset', '--hard', base)
                    git('clean', '-fdq')
                    locale = Path(directory) / locale_path
                    if operation in ('modify', 'mixed', 'docs'):
                        locale.write_text('<resources>changed</resources>')
                    if operation == 'mixed':
                        (Path(directory) / 'build.gradle.kts').write_text('changed')
                    if operation == 'docs':
                        (Path(directory) / 'README.md').write_text('changed')
                    if operation in ('add', 'source'):
                        target = locale.parent.parent / ('values-fr' if operation == 'add' else 'values') / 'strings.xml'
                        target.parent.mkdir(parents=True)
                        target.write_text('<resources/>')
                    if operation == 'delete':
                        locale.unlink()
                    if operation == 'rename':
                        locale.rename(locale.with_name('renamed.xml'))
                    git('add', '-A')
                    git('commit', '-qm', operation)
                    result = classify(git('rev-parse', 'HEAD'))
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertEqual(f'run_full={str(full).lower()}\nrun_translations={str(translations).lower()}\n', result.stdout)
            # Main can advance with product changes after a translation branch forks.
            # Only the PR's own changes should determine its verification scope.
            git('reset', '--hard', base)
            git('clean', '-fdq')
            (Path(directory) / locale_path).write_text('<resources>translation</resources>')
            git('add', '.')
            git('commit', '-qm', 'translation')
            translation_head = git('rev-parse', 'HEAD')
            git('reset', '--hard', base)
            (Path(directory) / 'product.kt').write_text('product change on main')
            git('add', '.')
            git('commit', '-qm', 'main advanced')
            old_base = base
            base = git('rev-parse', 'HEAD')
            result = classify(translation_head)
            self.assertEqual('run_full=false\nrun_translations=true\n', result.stdout)
            base = old_base
            result = classify('missing-commit')
            self.assertNotEqual(0, result.returncode)
            self.assertEqual('', result.stdout)
            self.assertEqual('run_full=true\nrun_translations=false\n', classify('missing-commit', 'workflow_call').stdout)


if __name__ == '__main__':
    unittest.main()
