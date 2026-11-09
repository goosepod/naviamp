import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('ios_cache', ROOT / 'scripts/ios-ci-cache.py')
CACHE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CACHE)


class CacheCompatibilityTest(unittest.TestCase):
    def key(self, root=ROOT, architecture='arm64', xcode='Xcode 27.2\nBuild 27B52',
            simulator_sdk='24A1', device_sdk='24A1'):
        return CACHE.compatibility(root, architecture, xcode, simulator_sdk, device_sdk)[1]

    def test_each_native_abi_input_invalidates_the_entire_restore_prefix(self):
        baseline = self.key()
        for argument, value in [('architecture', 'x86_64'), ('xcode', 'Xcode 27.3\nBuild 27C1'),
                                ('simulator_sdk', '24B1'), ('device_sdk', '24B1')]:
            with self.subTest(argument=argument):
                self.assertNotEqual(baseline, self.key(**{argument: value}))

    def test_compiler_and_wrapper_changes_cannot_restore_previous_compiled_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'gradle/wrapper').mkdir(parents=True)
            versions = root / 'gradle/libs.versions.toml'
            wrapper = root / 'gradle/wrapper/gradle-wrapper.properties'
            versions.write_text('[versions]\nkotlin = "2.3.21"\n')
            wrapper.write_text('distributionUrl=gradle-9.3.zip\n')
            baseline = self.key(root)
            versions.write_text('[versions]\nkotlin = "2.4.0"\n')
            self.assertNotEqual(baseline, self.key(root))
            versions.write_text('[versions]\nkotlin = "2.3.21"\n')
            wrapper.write_text('distributionUrl=gradle-9.4.zip\n')
            self.assertNotEqual(baseline, self.key(root))

    def test_task_evidence_distinguishes_cache_hits_execution_and_skips(self):
        text = '> Task :compile FROM-CACHE\n> Task :test\n> Task :test\n> Task :empty NO-SOURCE\n> Task :same UP-TO-DATE\n'
        self.assertEqual({'FROM-CACHE': 1, 'EXECUTED': 1, 'NO-SOURCE': 1, 'UP-TO-DATE': 1},
                         CACHE.outcomes(text))


if __name__ == '__main__':
    unittest.main()
