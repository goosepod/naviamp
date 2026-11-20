import hashlib
import json
from pathlib import Path
import runpy
import stat
import zipfile
import unittest
import warnings
import subprocess
import shutil
import test_verification_tvos_artifacts as fixtures
artifacts = fixtures.artifacts

api = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'tvos-compose-delivery.py'))


class ArtifactDeliveryTest(unittest.TestCase):
    def setUp(self):
        fixtures.ArtifactInventoryTest.setUp(self)
        self.manifest = self.root / 'manifest.json'
        self.manifest.write_text(json.dumps({'schemaVersion': 1, 'provenance': {'test': True},
                                            'files': artifacts.inventory(self.repositories)}))
        self.identity = artifacts.digest(self.manifest)
        self.bundle = self.root / 'bundle.zip'
        self.bundle_sha = api['pack'](self.repositories['ui'], self.repositories['resources'],
                                      self.manifest, self.identity, self.bundle)

    def install(self, bundle=None, identity=None):
        bundle = bundle or self.bundle
        return api['install'](bundle, artifacts.digest(bundle), identity or self.identity, self.root / 'installed')

    def test_delivery_is_deterministic_and_relocatable(self):
        other = self.root / 'second.zip'
        self.assertEqual(self.bundle_sha, api['pack'](self.repositories['ui'], self.repositories['resources'],
                                                     self.manifest, self.identity, other))
        installed = self.install()
        self.assertEqual(json.loads(self.manifest.read_text()), api['verify'](installed, self.identity))
        with self.assertRaises(ValueError): self.install()

    def test_bad_trust_identity_cannot_create_destination(self):
        with self.assertRaises(ValueError): self.install(identity='0' * 64)
        self.assertFalse((self.root / 'installed').exists())
        with self.assertRaises(ValueError):
            api['install'](self.bundle, '0'*64, self.identity, self.root/'installed')

    def test_tampering_is_rejected_even_with_a_new_bundle_checksum(self):
        with zipfile.ZipFile(self.bundle, 'a') as archive:
            archive.writestr('ui/extra.jar', b'extra')
        with self.assertRaises(ValueError): self.install()
        self.assertFalse((self.root/'installed').exists())

    def test_paths_duplicates_and_symlinks_are_rejected_atomically(self):
        for name, mode in [('../escape', stat.S_IFREG), ('ui/../escape', stat.S_IFREG),
                           ('ui\\escape', stat.S_IFREG), ('ui/link', stat.S_IFLNK), ('manifest.json', stat.S_IFREG)]:
            bad = self.root / 'bad.zip'
            with zipfile.ZipFile(self.bundle) as source, zipfile.ZipFile(bad, 'w') as target:
                for member in source.infolist(): target.writestr(member, source.read(member))
                item = zipfile.ZipInfo(name); item.external_attr = (mode | 0o644) << 16
                with warnings.catch_warnings():
                    warnings.simplefilter('ignore', UserWarning)
                    target.writestr(item, b'invalid')
            with self.assertRaises(ValueError): self.install(bad)
            self.assertFalse((self.root/'installed').exists())

    def test_consumer_verifies_artifacts_before_creating_checkout(self):
        prepare = runpy.run_path(str(Path(__file__).resolve().parents[1]/'prepare-tvos-scene-consumer.py'))['prepare']
        with self.assertRaises(ValueError):
            prepare(Path(__file__).resolve().parents[2], self.root, '0'*64, self.root/'consumer')
        self.assertFalse((self.root/'consumer').exists())

    def test_consumer_snapshot_preserves_local_edits_and_only_changes_settings(self):
        source = self.root/'source'
        source.mkdir()
        scripts = Path(__file__).resolve().parents[1]
        for name in ('tvos-compose-delivery.py', 'tvos-compose-artifacts.py'):
            target = source/'scripts'/name
            target.parent.mkdir(exist_ok=True)
            shutil.copyfile(scripts/name, target)
        template = source/'build-support/tvos-compose/consumer-settings.gradle.kts'
        template.parent.mkdir(parents=True)
        shutil.copyfile(scripts.parent/'build-support/tvos-compose/consumer-settings.gradle.kts', template)
        (source/'settings.gradle.kts').write_text('pluginManagement {}\nrootProject.name = "fixture"\n')
        (source/'source.kt').write_text('original')
        subprocess.run(['git', 'init', str(source)], check=True, capture_output=True)
        subprocess.run(['git', '-C', str(source), 'add', '.'], check=True, capture_output=True)
        subprocess.run(['git', '-C', str(source), '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.test',
                        '-c', 'commit.gpgsign=false', 'commit', '-m', 'fixture'], check=True, capture_output=True)
        (source/'source.kt').write_text('local edits')
        (source/'core').mkdir()
        (source/'core/new.kt').write_text('new common owner')
        (source/'.worktrees').mkdir()
        (source/'.worktrees/unrelated.kt').write_text('unrelated checkout')

        installed = self.install()
        prepare = runpy.run_path(str(scripts/'prepare-tvos-scene-consumer.py'))['prepare']
        destination = self.root/'consumer'
        report = prepare(source, installed, self.identity, destination)
        self.assertEqual('local edits', (destination/'source.kt').read_text())
        self.assertEqual('new common owner', (destination/'core/new.kt').read_text())
        self.assertFalse((destination/'.worktrees').exists())

        self.assertEqual(hashlib.sha256(b'local edits').hexdigest(), report['sourceFiles']['source.kt'])
        settings = (destination/'settings.gradle.kts').read_text()
        self.assertIn((installed/'ui').as_uri(), settings)
        self.assertNotIn('@@', settings)
        self.assertEqual(hashlib.sha256((destination/'settings.gradle.kts').read_bytes()).hexdigest(), report['settingsSha256'])
        self.assertIn('rootProject.name = "fixture"', settings)
        self.assertEqual('pluginManagement {}\nrootProject.name = "fixture"\n', (source/'settings.gradle.kts').read_text())

    def test_resume_inventory_is_explicit_and_does_not_weaken_complete_verification(self):
        expected = artifacts.expected_coordinates()
        retained = {c: owner for c,owner in expected.items() if owner == 'ui' or c[1] == 'compose-gradle-plugin'}
        for (group,module,version), owner in expected.items():
            if (group,module,version) not in retained:
                shutil.rmtree(self.repositories[owner].joinpath(*group.split('.'),module,version))
        self.assertEqual(98, len(artifacts.inventory(self.repositories, retained)))
        with self.assertRaisesRegex(ValueError, 'inventory mismatch'):
            artifacts.inventory(self.repositories)
