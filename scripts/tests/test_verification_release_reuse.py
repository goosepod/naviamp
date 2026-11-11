"""Exercise the actual release lookup shell, including unavailable/corrupt proof fallback."""
from pathlib import Path
import os
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ReleaseReuseTest(unittest.TestCase):
    def lookup(self, mode):
        workflow = (ROOT / '.github/workflows/tag-release-builds.yml').read_text()
        step = workflow.split('      - name: Find successful verification for this source tree\n', 1)[1]
        shell = textwrap.dedent(step.split('        run: |\n', 1)[1].split('\n  verification:', 1)[0])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            (root / 'source.txt').write_text('release source\n')
            subprocess.run(['git', '-C', str(root), 'add', 'source.txt'], check=True)
            subprocess.run(['git', '-C', str(root), '-c', 'user.name=Release fixture',
                            '-c', 'user.email=release@example.invalid', 'commit', '-qm', 'fixture'], check=True)
            head = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            tree = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD^{tree}'], text=True).strip()
            binary = root / 'bin'
            binary.mkdir()
            mock = binary / 'gh'
            mock.write_text('''#!/usr/bin/env python3
import io, os, sys, zipfile
path = next(arg for arg in sys.argv if arg.startswith('repos/'))
mode = os.environ['PROOF_MODE']
head, tree = os.environ['PROOF_HEAD'], os.environ['PROOF_TREE']
if path.endswith('/pulls'):
    print(head)
elif path.endswith('/actions/artifacts'):
    print('123\\t456')
elif path.endswith('/actions/runs/456'):
    event = 'workflow_dispatch' if mode == 'wrong-event' else 'pull_request'
    print('Verify\\t' + event + '\\tsuccess\\t' + head)
elif path.endswith('/123/zip'):
    if mode == 'download-failure':
        print('simulated HTTP 502', file=sys.stderr)
        sys.exit(1)
    if mode == 'corrupt-archive':
        sys.stdout.buffer.write(b'incomplete archive')
        sys.exit(0)
    if mode == 'wrong-tree': tree = '0' * 40
    if mode == 'wrong-head': head = '0' * 40
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w') as archive:
        archive.writestr('evidence.txt', f'source_head_sha={head}\\ntested_tree_sha={tree}\\nworkflow_run_id=456\\n')
    sys.stdout.buffer.write(data.getvalue())
else:
    raise SystemExit('Unexpected API fixture request')
''')
            mock.chmod(0o755)
            output = root / 'outputs'
            env = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ['PATH'],
                       RUNNER_TEMP=str(root / 'runner'), GITHUB_OUTPUT=str(output),
                       GITHUB_REPOSITORY='goosepod/naviamp', PROOF_MODE=mode,
                       PROOF_HEAD=head, PROOF_TREE=tree)
            (root / 'runner').mkdir()
            result = subprocess.run(['bash', '-e', '-o', 'pipefail', '-c', shell], cwd=root,
                                    env=env, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            return output.read_text(), result.stdout

    def test_exact_tree_with_matching_successful_pr_evidence_can_be_reused(self):
        output, _ = self.lookup('valid')
        self.assertIn('reuse_verified=true\n', output)

    def test_unavailable_evidence_requires_fresh_verification(self):
        output, log = self.lookup('download-failure')
        self.assertIn('reuse_verified=false\n', output)
        self.assertIn('require fresh verification', log)

    def test_corrupt_archive_requires_fresh_verification(self):
        output, log = self.lookup('corrupt-archive')
        self.assertIn('reuse_verified=false\n', output)
        self.assertIn('require fresh verification', log)

    def test_wrong_head_tree_or_origin_never_reuses_proof(self):
        for mode in ('wrong-head', 'wrong-tree', 'wrong-event'):
            with self.subTest(mode=mode):
                output, _ = self.lookup(mode)
                self.assertIn('reuse_verified=false\n', output)
