import copy
import json
import os
from pathlib import Path
import runpy
import shutil
import shlex
import sys
import tempfile
import stat
import subprocess
import unittest
from unittest import mock
import zipfile

import test_verification_tvos_delivery as fixtures

ROOT = Path(__file__).resolve().parents[2]
api = runpy.run_path(str(ROOT/'scripts/tvos-hosted-compose.py'))
ci = runpy.run_path(str(ROOT/'scripts/tvos-hosted-ci.py'))


class HostedArtifactTrustTest(unittest.TestCase):
    def setUp(self):
        fixtures.ArtifactDeliveryTest.setUp(self)
        self.archive = self.root/'github.zip'
        with zipfile.ZipFile(self.archive, 'w') as zipped:
            zipped.write(self.bundle, 'compose.zip')
        self.pin = dict(schemaVersion=1, status='approved', repository='goosepod/naviamp', repositoryId=91,
            workflowPath=api['WORKFLOW'], producerCommit='a'*40, runId=12, runAttempt=1, artifactId=34,
            artifactName='tvos-compose-candidate-12-1', artifactSha256=api['sha'](self.archive),
            bundleSha256=self.bundle_sha, manifestSha256=self.identity, sourceBundleSha256=api['SOURCE_SHA'])
        repository = dict(id=91, full_name='goosepod/naviamp')
        self.run = dict(id=12, run_attempt=1, event='workflow_dispatch', path=api['WORKFLOW'], head_branch='main',
            head_sha='a'*40, status='completed', conclusion='success', repository=repository, head_repository=repository)
        self.artifact = dict(id=34, name=self.pin['artifactName'], expired=False,
            digest='sha256:'+self.pin['artifactSha256'], size_in_bytes=self.archive.stat().st_size,
            workflow_run=dict(id=12, head_sha='a'*40, head_branch='main', repository_id=91, head_repository_id=91))
        self.network = mock.Mock(side_effect=lambda endpoint: copy.deepcopy(self.run if '/runs/' in endpoint else self.artifact))
        self.download = mock.Mock(side_effect=lambda endpoint, target: shutil.copyfile(self.archive, target))
        self.attest = mock.Mock()

    def fetch(self, suffix=''):
        return api['fetch'](self.pin, self.root/('installed'+suffix), self.root/('evidence'+suffix),
                            api=self.network, download=self.download, attest=self.attest)

    def test_valid_pinned_chain_authenticates_before_installing_complete_inventory(self):
        def attest(bundle, pin, evidence):
            self.assertFalse((self.root/'installed').exists())
            self.assertEqual(api['sha'](bundle), pin['bundleSha256'])
        self.attest.side_effect = attest
        self.assertTrue(self.fetch()['passed'])
        self.attest.assert_called_once()
        self.assertEqual(fixtures.api['verify'](self.root/'installed', self.identity)['files'],
                         json.loads(self.manifest.read_text())['files'])
        self.assertEqual(self.network.call_args_list, [mock.call('repos/goosepod/naviamp/actions/runs/12'),
            mock.call('repos/goosepod/naviamp/actions/artifacts/34')])
        self.assertFalse(list((self.root/'evidence').glob('.hosted-compose-*')))

    def test_unapproved_pin_fails_before_network_and_never_promotes_itself(self):
        pending = json.loads((ROOT/'build-support/tvos-compose/trusted-artifact.json').read_text())
        for index, pin in enumerate([pending, dict(self.pin, status='candidate')]):
            self.pin = pin
            with self.assertRaisesRegex(ValueError, 'No approved'):
                self.fetch(str(index))
            self.assertFalse((self.root/('installed'+str(index))).exists())
        self.network.assert_not_called()
        self.download.assert_not_called()
        self.attest.assert_not_called()

    def test_wrong_run_or_artifact_is_rejected_before_downloading(self):
        changes = [('run', 'head_branch', 'feature'), ('run', 'head_sha', 'b'*40),
            ('run', 'event', 'pull_request'), ('run', 'path', '.github/workflows/other.yml'),
            ('run', 'status', 'in_progress'), ('run', 'conclusion', 'failure'), ('run', 'run_attempt', 2),
            ('run', 'repository', dict(id=999, full_name='fork/naviamp')),
            ('run', 'head_repository', dict(id=999, full_name='fork/naviamp')),
            ('artifact', 'id', 999), ('artifact', 'name', 'latest'), ('artifact', 'expired', True),
            ('artifact', 'digest', 'sha256:'+'0'*64), ('artifact', 'size_in_bytes', api['MAX_DOWNLOAD']+1),
            ('artifact', 'workflow_run', dict(id=999, head_sha='a'*40, head_branch='main', repository_id=91, head_repository_id=91))]
        original_run, original_artifact = copy.deepcopy(self.run), copy.deepcopy(self.artifact)
        for index, (owner, key, value) in enumerate(changes):
            with self.subTest(owner=owner, key=key):
                self.run, self.artifact = copy.deepcopy(original_run), copy.deepcopy(original_artifact)
                getattr(self, owner)[key] = value
                with self.assertRaises(ValueError): self.fetch(str(index))
                self.assertFalse((self.root/('installed'+str(index))).exists())
        self.download.assert_not_called()
        self.attest.assert_not_called()

    def test_archive_or_bundle_digest_mismatch_never_reaches_attestation(self):
        self.pin['artifactSha256'] = '0'*64
        self.artifact['digest'] = 'sha256:'+'0'*64
        with self.assertRaisesRegex(ValueError, 'archive digest'): self.fetch('archive')
        self.pin['artifactSha256'] = api['sha'](self.archive)
        self.artifact['digest'] = 'sha256:'+self.pin['artifactSha256']
        self.pin['bundleSha256'] = '0'*64
        with self.assertRaisesRegex(ValueError, 'bundle differs'): self.fetch('bundle')
        self.attest.assert_not_called()

    def test_size_mismatch_and_unsafe_outer_members_fail_without_installation(self):
        self.artifact['size_in_bytes'] -= 1
        with self.assertRaisesRegex(ValueError, 'archive size differs'): self.fetch('size')
        for index, member in enumerate(('../compose.zip', 'nested/compose.zip', 'compose.zip')):
            with zipfile.ZipFile(self.archive, 'w') as zipped:
                info = zipfile.ZipInfo(member)
                info.external_attr = (stat.S_IFLNK | 0o777) << 16 if index == 2 else (stat.S_IFREG | 0o644) << 16
                zipped.writestr(info, self.bundle.read_bytes())
            self.pin['artifactSha256'] = api['sha'](self.archive)
            self.artifact.update(digest='sha256:'+self.pin['artifactSha256'], size_in_bytes=self.archive.stat().st_size)
            with self.assertRaises(ValueError): self.fetch(str(index))
        self.attest.assert_not_called()

    def test_attestation_rejection_preserves_failure_and_leaves_no_repository(self):
        self.attest.side_effect = subprocess.CalledProcessError(1, ['gh', 'attestation', 'verify'])
        with self.assertRaises(subprocess.CalledProcessError): self.fetch()
        self.assertFalse((self.root/'installed').exists())
        self.assertFalse(json.loads((self.root/'evidence/summary.json').read_text())['passed'])

    def test_attestation_cannot_replace_the_independent_manifest_pin(self):
        self.pin['manifestSha256'] = '0'*64
        with self.assertRaisesRegex(ValueError, 'Manifest differs'): self.fetch()
        self.attest.assert_called_once()
        self.assertFalse((self.root/'installed').exists())

    def test_attestation_command_pins_subject_source_workflow_and_hosted_issuer(self):
        command = api['attestation_command'](self.bundle, self.pin)
        self.assertIn('--deny-self-hosted-runners', command)
        for flag, value in [('--repo', 'goosepod/naviamp'), ('--source-ref', 'refs/heads/main'),
            ('--source-digest', 'a'*40), ('--signer-digest', 'a'*40),
            ('--signer-workflow', 'goosepod/naviamp/'+api['WORKFLOW']),
            ('--cert-oidc-issuer', 'https://token.actions.githubusercontent.com')]:
            self.assertEqual(command[command.index(flag)+1], value)

    def test_github_host_is_fixed_even_with_a_different_local_cli_default(self):
        with mock.patch.dict(os.environ, GH_HOST='untrusted.example'), \
                mock.patch('subprocess.check_output', return_value='{}') as command:
            api['gh_json']('repos/goosepod/naviamp/actions/runs/12')
        self.assertEqual(command.call_args.kwargs['env']['GH_HOST'], 'github.com')
        self.assertEqual(command.call_args.args[0][:4], ['gh', 'api', '--hostname', 'github.com'])

    def test_promotion_output_stays_unapproved_and_validates_immutable_identifiers(self):
        env = dict(GITHUB_SHA='a'*40, GITHUB_REPOSITORY_ID='91', GITHUB_RUN_ID='12', GITHUB_RUN_ATTEMPT='1',
                   COMPOSE_ARTIFACT_ID='34', COMPOSE_ARTIFACT_SHA256=self.pin['artifactSha256'],
                   COMPOSE_BUNDLE_SHA256=self.bundle_sha, COMPOSE_MANIFEST_SHA256=self.identity)
        with mock.patch.dict(os.environ, env):
            pin = api['promotion_from_environment']()
        self.assertEqual(pin, dict(self.pin, status='candidate'))
        with self.assertRaisesRegex(ValueError, 'No approved'): api['validate_pin'](pin)
        for key, value in [('schemaVersion', True), ('artifactId', True), ('producerCommit', 'main'), ('artifactName', 'latest'),
                           ('sourceBundleSha256', '0'*64), ('unexpected', 'value')]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                api['validate_pin'](dict(self.pin, **{key: value}))

    def test_stage_revalidates_the_sealed_inventory_and_records_candidate_identity(self):
        source = self.root/'producer'
        source.mkdir()
        shutil.copytree(self.repositories['ui'], source/'ui-repository')
        shutil.copytree(self.repositories['resources'], source/'resources-repository')
        evidence = source/'producer-evidence'
        evidence.mkdir()
        shutil.copyfile(self.manifest, evidence/'manifest.json')
        summary = dict(passed=True, identity={'bundleSha256': api['SOURCE_SHA']}, manifestSha256=self.identity)
        (evidence/'summary.json').write_text(json.dumps(summary))
        staged = api['stage'](source, self.root/'staged')
        self.assertEqual(staged['bundleSha256'], self.bundle_sha)
        self.assertEqual(staged['manifestSha256'], self.identity)
        summary['passed'] = False
        (evidence/'summary.json').write_text(json.dumps(summary))
        with self.assertRaises(ValueError): api['stage'](source, self.root/'incomplete')
        self.assertFalse((self.root/'incomplete').exists())


class HostedSceneBoundaryTest(unittest.TestCase):
    def test_runtime_selects_only_available_tv_runtime_and_records_actual_choice(self):
        rows = [dict(identifier='com.apple.CoreSimulator.SimRuntime.tvOS-'+version.replace('.', '-'),
                     version=version, isAvailable=available) for version, available in [('18.5', True), ('27.2', True), ('28.0', False)]]
        rows.append(dict(identifier='com.apple.CoreSimulator.SimRuntime.iOS-30-0', version='30.0', isAvailable=True))
        self.assertEqual(ci['select_runtime'](rows)['version'], '27.2')
        with self.assertRaises(ValueError): ci['select_runtime'](rows[2:])

    def test_ci_cleanup_refuses_an_unrelated_device_and_preserves_error_evidence(self):
        import tempfile
        device = '11111111-2222-3333-4444-555555555555'
        with tempfile.TemporaryDirectory() as temporary:
            scene = Path(temporary)
            (scene/'ownership.json').write_text(json.dumps(dict(device=device, application='app.naviamp.tvos')))
            devices = json.dumps({'devices': {'tv': [dict(udid=device, name='User Apple TV', state='Booted')]}})
            cleanup = mock.Mock()
            with mock.patch('subprocess.check_output', return_value=devices), \
                    mock.patch.dict(ci['ownership'], cleanup_owned_scene=cleanup):
                with self.assertRaisesRegex(ValueError, 'unrelated simulator'): ci['cleanup'](scene)
            cleanup.assert_not_called()
            self.assertFalse(json.loads((scene/'ci-cleanup.json').read_text())['passed'])

    def test_ci_cleanup_handles_a_job_that_never_allocated_a_device(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temporary, mock.patch('subprocess.check_output') as effect:
            self.assertTrue(ci['cleanup'](Path(temporary)/'scene')['passed'])
            effect.assert_not_called()


@unittest.skipUnless(shutil.which('ruby'), 'Workflow structure needs the local Ruby YAML parser; also run actionlint')
class HostedWorkflowTrustTest(unittest.TestCase):
    def workflow(self, name):
        data = json.loads(subprocess.check_output(['ruby', '-ryaml', '-rjson', '-e',
            'puts JSON.generate(YAML.load_file(ARGV.fetch(0)))', str(ROOT/'.github/workflows'/name)], text=True))
        # Ruby's YAML 1.1 parser treats the unquoted Actions `on` key as true.
        data['on'] = data.get('on', data.get('true'))
        return data

    def test_writer_is_main_dispatch_only_and_attestation_privileges_are_separate(self):
        workflow = self.workflow('tvos-compose-producer.yml')
        self.assertEqual(set(workflow['on']), {'workflow_dispatch'})
        self.assertEqual(workflow['permissions'], {'contents': 'read'})
        build, attest = workflow['jobs']['build'], workflow['jobs']['attest']
        for job in (build, attest):
            self.assertIn("github.ref == 'refs/heads/main'", job['if'])
            self.assertIn("github.repository == 'goosepod/naviamp'", job['if'])
        self.assertEqual(attest['needs'], 'build')
        self.assertEqual(attest['permissions']['id-token'], 'write')
        self.assertEqual(attest['permissions']['attestations'], 'write')
        self.assertNotIn('permissions', build)
        steps = build['steps']
        scene = next(i for i, s in enumerate(steps) if 'scripts/run-tvos-scene-tests.py' in s.get('run', ''))
        transport = next(i for i, s in enumerate(steps) if 'scripts/run-tvos-transport-tests.py' in s.get('run', ''))
        upload = next(i for i, s in enumerate(steps) if s.get('id') == 'upload')
        self.assertLess(scene, transport)
        self.assertLess(transport, upload)
        self.assertNotIn('if', steps[transport])
        self.assertNotIn('--require-system-context', steps[transport]['run'])
        self.assertIn('os27SimulatorQualified', steps[transport]['run'])
        cleanup = next(s for s in steps if 'cleanup --scene build/tvos-transport' in s.get('run', ''))
        self.assertEqual(cleanup['if'], 'always()')
        self.assertEqual(steps[upload]['with']['path'], 'build/tvos-delivery/compose.zip')
        self.assertIs(steps[upload]['with']['overwrite'], False)
        self.assertIs(steps[upload]['with']['archive'], True)

    def test_consumer_uses_base_policy_with_read_only_permissions_and_step_scoped_token(self):
        workflow = self.workflow('tvos-scene.yml')
        pin = json.loads((ROOT / 'build-support/tvos-compose/trusted-artifact.json').read_text())
        expected = {'workflow_dispatch'} if pin['status'].startswith('pending') else {'pull_request', 'workflow_dispatch'}
        self.assertEqual(set(workflow['on']), expected)
        self.assertEqual(workflow['permissions'], {'contents': 'read', 'actions': 'read', 'attestations': 'read'})
        job = workflow['jobs']['scene']
        self.assertNotIn('GH_TOKEN', job['env'])
        policy = next(s for s in job['steps'] if s.get('with', {}).get('path') == 'policy')
        self.assertIn('github.event.pull_request.base.sha', policy['with']['ref'])
        self.assertIn('refs/heads/main', policy['with']['ref'])
        token_steps = [s for s in job['steps'] if 'GH_TOKEN' in s.get('env', {})]
        self.assertEqual(len(token_steps), 1)
        self.assertIn('python3 policy/scripts/tvos-hosted-compose.py fetch', token_steps[0]['run'])
        self.assertIn('--pin policy/build-support/tvos-compose/trusted-artifact.json', token_steps[0]['run'])
        cleanup = next(s for s in job['steps'] if 'policy/scripts/tvos-hosted-ci.py cleanup' in s.get('run', ''))
        self.assertEqual(cleanup['if'], 'always()')
        transport = next(s for s in job['steps'] if 'source/scripts/run-tvos-transport-tests.py' in s.get('run', ''))
        self.assertIn('--checkout source', transport['run'])
        self.assertNotIn('--require-system-context', transport['run'])
        self.assertIn('os27SimulatorQualified', transport['run'])
        self.assertIn('policy/build-support/tvos-compose/trusted-artifact.json', transport['run'])
        transport_cleanup = next(s for s in job['steps'] if 'cleanup --scene build/tvos-transport' in s.get('run', ''))
        self.assertEqual(transport_cleanup['if'], 'always()')
        evidence = next(s['with']['path'] for s in job['steps'] if s.get('uses', '').startswith('actions/upload-artifact@'))
        self.assertIn('!build/tvos-transport/fixture.bootstrap', evidence)
        self.assertIn('!build/tvos-transport/consumer/**', evidence)

    def test_workflow_receipts_accept_only_successful_simulator_qualification(self):
        for name, job in [('tvos-compose-producer.yml', 'build'), ('tvos-scene.yml', 'scene')]:
            steps = self.workflow(name)['jobs'][job]['steps']
            transport = next(s for s in steps if 'scripts/run-tvos-transport-tests.py' in s.get('run', ''))
            command = shlex.split(transport['run'].splitlines()[-1])
            self.assertEqual(command[:2], ['python3', '-c'])
            accepted = dict(passed=True, scope='tvos-simulator', os27SimulatorQualified=True,
                            os27ContextQualified=False, systemContextDelivery='unverified')
            with tempfile.TemporaryDirectory() as temporary:
                receipt = Path(temporary)/'build/tvos-transport/summary.json'
                receipt.parent.mkdir(parents=True)
                for delta, allowed in [({}, True), ({'passed': False}, False),
                                       ({'scope': 'components'}, False),
                                       ({'scope': 'os27-system-context-probe'}, False),
                                       ({'os27SimulatorQualified': False}, False)]:
                    receipt.write_text(json.dumps(dict(accepted, **delta)))
                    result = subprocess.run([sys.executable, *command[1:]], cwd=temporary,
                                            capture_output=True, text=True, timeout=10)
                    with self.subTest(workflow=name, delta=delta):
                        self.assertEqual(result.returncode == 0, allowed, result.stderr)

    def test_every_action_is_commit_pinned_and_no_job_restores_or_writes_shared_caches(self):
        for name in ('tvos-compose-producer.yml', 'tvos-scene.yml'):
            workflow = self.workflow(name)
            for job in workflow['jobs'].values():
                for step in job['steps']:
                    if 'uses' not in step: continue
                    self.assertRegex(step['uses'], r'^[\w-]+/[\w-]+@[0-9a-f]{40}$')
                    self.assertNotIn('actions/cache@', step['uses'])
                    self.assertNotIn('gradle/actions/setup-gradle@', step['uses'])
                    if step['uses'].startswith('actions/checkout@'):
                        self.assertIs(step['with']['persist-credentials'], False)
