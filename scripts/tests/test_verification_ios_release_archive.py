import importlib.util
import json
import tarfile
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('release_archive', ROOT / 'scripts/ios-release-archive.py')
ARCHIVE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ARCHIVE)


def valid(seconds=100):
    return dict(validated=True, exitCode=0, totalSeconds=seconds,
                workload=dict(source='same-commit', dirty=False, compatibility={'sdk': 'same'},
                              configuration='Release', architecture='arm64', archiveHeapGiB=6),
                releaseLinks=[dict(outcome='FROM-CACHE')])


class ReleaseArchiveEvidenceTest(unittest.TestCase):
    def test_comparison_requires_same_source_toolchain_and_optimized_workload(self):
        before = valid()
        for key, value in [('source', 'different'), ('compatibility', {'sdk': 'different'}),
                           ('configuration', 'Debug'), ('architecture', 'x86_64'), ('archiveHeapGiB', 2)]:
            after = valid(20)
            after['workload'][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                ARCHIVE.compare(before, after)

    def test_failed_dirty_or_unvalidated_commands_are_not_performance_results(self):
        for change in ('validated', 'exitCode', 'dirty'):
            after = valid()
            if change == 'dirty':
                after['workload']['dirty'] = True
            else:
                after[change] = False if change == 'validated' else 1
            with self.subTest(change=change), self.assertRaises(ValueError):
                ARCHIVE.compare(valid(), after)

    def test_archive_comparison_does_not_claim_net_hosted_improvement(self):
        result = ARCHIVE.compare(valid(100), valid(20))
        self.assertEqual(80, result['archiveImprovementPercent'])
        self.assertEqual(['FROM-CACHE'], result['warmReleaseLinkOutcomes'])
        self.assertFalse(result['hostedNetImprovementEstablished'])

    def test_phase_clocks_report_incomplete_work_and_reject_invalid_sequences(self):
        phases, pending = ARCHIVE.phase_durations([
            dict(phase='archive', state='start', monotonicNs=100),
            dict(phase='archive', state='end', monotonicNs=1_000_000_100),
            dict(phase='validation', state='start', monotonicNs=1_000_000_200)])
        self.assertEqual({'archive': 1.0}, phases)
        self.assertEqual(['validation'], pending)
        for events in ([dict(phase='archive', state='end', monotonicNs=100)],
                       [dict(phase='archive', state='start', monotonicNs=200),
                        dict(phase='archive', state='end', monotonicNs=100)]):
            with self.assertRaises(ValueError):
                ARCHIVE.phase_durations(events)

    def test_isolated_homes_carry_the_profile_and_live_test_policy_into_nested_gradle(self):
        with tempfile.TemporaryDirectory(prefix='archive measurement ') as directory:
            root = Path(directory).resolve()
            env = ARCHIVE.prepare(ROOT, root / 'cache', root / 'report')
            home = Path(env['GRADLE_USER_HOME'])
            self.assertEqual('konan.data.dir=' + str(root / 'cache/konan') + '\norg.gradle.caching=true\n',
                             (home / 'gradle.properties').read_text())
            self.assertEqual((ROOT / 'scripts/ios-ci-cache.init.gradle.kts').read_bytes(),
                             (home / 'init.d/ios-ci-cache.init.gradle.kts').read_bytes())
            self.assertTrue((home / 'init.d/ios-release-profile.init.gradle.kts').is_file())
            self.assertEqual(str(root / 'report'), env['NAVIAMP_IOS_ARCHIVE_REPORT_DIR'])

    def test_measurements_cannot_write_into_regular_or_overlapping_cache_homes(self):
        with tempfile.TemporaryDirectory() as directory:
            for cache, report in [(Path.home() / '.gradle', Path(directory) / 'report'),
                                  (Path(directory) / 'cache', Path(directory) / 'cache/report'),
                                  (Path(directory) / 'report/cache', Path(directory) / 'report')]:
                with self.subTest(cache=cache), self.assertRaises(ValueError):
                    ARCHIVE.prepare(ROOT, cache, report)

    def test_success_without_link_timing_or_completed_validation_is_not_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory)
            self.assertFalse(ARCHIVE.summarize(report, valid()['workload'], 10, 0)['validated'])
            for phase in ('archive', 'validation', 'packaging'):
                ARCHIVE.event(report, phase, 'start')
                ARCHIVE.event(report, phase, 'end')
            tasks = report / 'tasks'
            tasks.mkdir()
            path = tasks / 'gradle-test.json'
            path.write_text(json.dumps([dict(task=':apps:ios:linkReleaseFrameworkIosArm64', outcome='FAILED')]))
            self.assertFalse(ARCHIVE.summarize(report, valid()['workload'], 10, 0)['validated'])
            path.write_text(json.dumps([dict(task=':apps:ios:linkReleaseFrameworkIosArm64', outcome='FROM-CACHE')]))
            self.assertTrue(ARCHIVE.summarize(report, valid()['workload'], 10, 0)['validated'])
            self.assertFalse(ARCHIVE.summarize(report, valid()['workload'], 10, 1)['validated'])


class ReleaseCacheTransferTest(unittest.TestCase):
    def stores(self, root):
        for name in ARCHIVE.STORES:
            (root / name).mkdir(parents=True)
        (root / 'gradle/caches/module.jar').write_bytes(b'cache')
        compiler = root / 'konan/compiler'
        compiler.write_text('executable fixture')
        compiler.chmod(0o755)
        keys = root / 'gradle/caches/keyrings'
        keys.mkdir()
        (keys / 'credential').write_text('excluded fixture')
        (root / 'gradle/gradle.properties').write_text('must not transfer')

    def test_transfers_only_compiler_stores_and_preserves_executable_bits(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, target, bundle = root / 'source', root / 'target', root / 'cache.tar.gz'
            self.stores(source)
            ARCHIVE.pack_cache(source, bundle, valid()['workload'])
            result = ARCHIVE.restore_cache(target, bundle, valid()['workload'])
            self.assertEqual(b'cache', (target / 'gradle/caches/module.jar').read_bytes())
            self.assertTrue((target / 'konan/compiler').stat().st_mode & 0o100)
            self.assertFalse((target / 'gradle/caches/keyrings').exists())
            self.assertFalse((target / 'gradle/gradle.properties').exists())
            self.assertGreater(result['bytes'], 0)
            with self.assertRaises(ValueError):
                ARCHIVE.restore_cache(target, bundle, valid()['workload'])

    def test_mismatched_workload_and_corrupted_bundle_do_not_write_cache_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, target, bundle = root / 'source', root / 'target', root / 'cache.tar.gz'
            self.stores(source)
            ARCHIVE.pack_cache(source, bundle, valid()['workload'])
            changed = valid()['workload']; changed['source'] = 'other'
            with self.assertRaises(ValueError):
                ARCHIVE.restore_cache(target, bundle, changed)
            self.assertFalse(target.exists())
            with bundle.open('ab') as output:
                output.write(b'corrupt')
            with self.assertRaises(ValueError):
                ARCHIVE.restore_cache(target, bundle, valid()['workload'])
            self.assertFalse(target.exists())

    def test_forbids_paths_links_and_keyring_or_init_script_injection(self):
        with tempfile.TemporaryDirectory() as directory:
            for name, link in [('../escape', None), ('/escape', None),
                               ('gradle/init.d/injected.gradle', None),
                               ('gradle/caches/keyrings/secret', None),
                               ('konan/link', '/escape'), ('konan/link', '../../escape')]:
                member = tarfile.TarInfo(name)
                if link is not None:
                    member.type = tarfile.SYMTYPE; member.linkname = link
                with self.subTest(name=name, link=link), self.assertRaises((ValueError, tarfile.FilterError)):
                    ARCHIVE.allowed_member(member, directory)


if __name__ == '__main__':
    unittest.main()
