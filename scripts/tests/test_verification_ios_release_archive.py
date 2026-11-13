import importlib.util
import json
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

    def test_phase_profile_reaches_the_nested_compiler_without_changing_the_heap_or_optimization(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            env = ARCHIVE.prepare(ROOT, root / 'cache', root / 'report', profile_phases=True)
            props = (Path(env['GRADLE_USER_HOME']) / 'gradle.properties').read_text()
            self.assertIn('naviamp.ios.profilePhases=true\n', props)
            self.assertNotIn('binary.', props)
            self.assertNotIn('jvmargs', props)

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


if __name__ == '__main__':
    unittest.main()
