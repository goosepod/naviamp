import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

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
    def toolchain_pair(self):
        baseline, candidate = valid(100), valid(30)
        for result, source, version, millis in ((baseline, 'baseline', '2.3.21', 80_000),
                                                 (candidate, 'candidate', '2.4.21', 20_000)):
            result['workload'].update(source=source, coldCompile=True, profilePhases=False, compilerBuildReports=True)
            result['workload']['compatibility']['kotlin'] = version
            result['releaseLinks'] = [dict(outcome='EXECUTED', durationMillis=millis)]
            result['tasks'] = [dict(task=':apps:ios:compileKotlinIosArm64', outcome='EXECUTED')]
        return baseline, candidate

    def toolchain_sources(self):
        return ['gradle/libs.versions.toml', 'kotlin = "2.3.21"', 'kotlin = "2.4.21"',
                'kotlin.native.binary.gc=pmcs', 'classpath("com.android.tools:r8:9.1.57")']

    def test_toolchain_comparison_isolates_compiler_with_gc_and_optimizer_held_constant(self):
        with patch.object(ARCHIVE.CACHE, 'command', side_effect=self.toolchain_sources()):
            result = ARCHIVE.compare_toolchain(ROOT, *self.toolchain_pair())
        self.assertEqual(75, result['linkImprovementPercent'])
        self.assertEqual('pmcs', result['configuredNativeGc'])
        self.assertEqual('9.1.57', result['configuredR8'])
        self.assertFalse(result['hostedImprovementEstablished'])

    def test_toolchain_comparison_rejects_changed_runtime_policy_or_additional_source_changes(self):
        for index, replacement in [(0, 'core/ui/Changed.kt'), (2, 'kotlin = "2.4.21"\ncompose = "other"'),
                                   (3, ''), (3, 'kotlin.native.binary.gc=cms'),
                                   (4, 'classpath("com.android.tools:r8:8.13.19")')]:
            sources = self.toolchain_sources(); sources[index] = replacement
            with self.subTest(index=index, value=replacement), patch.object(ARCHIVE.CACHE, 'command', side_effect=sources), self.assertRaises(ValueError):
                ARCHIVE.compare_toolchain(ROOT, *self.toolchain_pair())

    def test_toolchain_comparison_rejects_cached_compilation_and_mismatched_measurement_flags(self):
        for mutation in ('cache', 'uptodate', 'profile', 'heap', 'sdk'):
            baseline, candidate = self.toolchain_pair()
            if mutation == 'cache': candidate['tasks'][0]['outcome'] = 'FROM-CACHE'
            if mutation == 'uptodate': candidate['tasks'][0]['outcome'] = 'UP-TO-DATE'
            if mutation == 'profile': candidate['workload']['profilePhases'] = True
            if mutation == 'heap': candidate['workload']['archiveHeapGiB'] = 8
            if mutation == 'sdk': candidate['workload']['compatibility']['sdk'] = 'other'
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                ARCHIVE.compare_toolchain(ROOT, baseline, candidate)

    def cold_pair(self):
        baseline, candidate = valid(100), valid(30)
        for result, source, millis in ((baseline, 'baseline', 80_000), (candidate, 'candidate', 20_000)):
            result['workload'].update(source=source, coldCompile=True, profilePhases=True)
            result['releaseLinks'] = [dict(outcome='EXECUTED', durationMillis=millis)]
            result['tasks'] = [dict(outcome='EXECUTED')]
        return baseline, candidate

    def test_cold_comparison_accepts_only_the_reviewed_storage_export_difference(self):
        baseline, candidate = self.cold_pair()
        original = 'plugins {}\n            export(project(":core:storage"))\n            api(project(":core:storage"))\n'
        narrowed = 'plugins {}\n            implementation(project(":core:storage"))\n'
        with patch.object(ARCHIVE.CACHE, 'command', side_effect=['apps/ios/build.gradle.kts', original, narrowed]):
            result = ARCHIVE.compare_cold(ROOT, baseline, candidate)
        self.assertEqual(75, result['linkImprovementPercent'])
        self.assertEqual(70, result['archiveImprovementPercent'])
        self.assertFalse(result['hostedImprovementEstablished'])
        for responses in (['core/ui/Changed.kt'], ['apps/ios/build.gradle.kts', original, narrowed + 'other change']):
            with patch.object(ARCHIVE.CACHE, 'command', side_effect=responses), self.assertRaises(ValueError):
                ARCHIVE.compare_cold(ROOT, baseline, candidate)

    def test_cold_comparison_rejects_cached_or_changed_compiler_workloads(self):
        for mutation in ('link', 'task', 'heap', 'cold'):
            baseline, candidate = self.cold_pair()
            if mutation == 'link': candidate['releaseLinks'][0]['outcome'] = 'UP-TO-DATE'
            if mutation == 'task': candidate['tasks'][0]['outcome'] = 'FROM-CACHE'
            if mutation == 'heap': candidate['workload']['archiveHeapGiB'] = 8
            if mutation == 'cold': candidate['workload']['coldCompile'] = False
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                ARCHIVE.compare_cold(ROOT, baseline, candidate)

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
            self.assertEqual('konan.data.dir=' + str(root / 'cache/konan') + '\norg.gradle.caching=true\n' +
                             'kotlin.build.report.output=file\n' +
                             'kotlin.build.report.file.output_dir=' + str(root / 'report/kotlin-build') + '\n' +
                             'kotlin.build.report.include_compiler_arguments=false\n',
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

    def test_cold_compilation_never_restores_task_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            env = ARCHIVE.prepare(ROOT, root / 'cache', root / 'report', cold_compile=True)
            props = (Path(env['GRADLE_USER_HOME']) / 'gradle.properties').read_text()
            self.assertIn('org.gradle.caching=false\n', props)
            self.assertNotIn('org.gradle.caching=true', props)

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
