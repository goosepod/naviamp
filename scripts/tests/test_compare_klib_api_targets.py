import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location("klib_comparison", Path(__file__).parents[1] / "compare-klib-api-targets.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class KlibApiComparisonTest(unittest.TestCase):
    def test_target_alias_and_scope_do_not_leak_to_siblings(self):
        dump = """// Klib ABI Dump
// Targets: [iosArm64, tvosArm64]
// Alias: ios => [iosArm64]
// Library unique name: <fixture>
// Targets: [ios]
final class Example { // Example
    final val value // Example.value
        final fun <get-value>(): kotlin/Int // Example.value.get
}
final class Shared // Shared
"""
        result = MODULE.declarations_by_target(dump)
        self.assertEqual(4, len(result['iosArm64']))
        self.assertEqual({'final class Shared // Shared'}, result['tvosArm64'])

    def test_member_target_is_restricted_without_restricting_next_member(self):
        dump = """// Klib ABI Dump
// Targets: [iosArm64, tvosArm64]
// Library unique name: <fixture>
final class Shared { // Shared
    // Targets: [iosArm64]
    final fun phone() // Shared.phone
    final fun all() // Shared.all
}
"""
        result = MODULE.declarations_by_target(dump)
        self.assertEqual(3, len(result['iosArm64']))
        self.assertEqual(2, len(result['tvosArm64']))

    def test_renamed_targets_retain_added_and_removed_signatures(self):
        result = MODULE.compare({'old': {'unchanged', 'removed'}},
                                {'new': {'unchanged', 'added'}}, [('old', 'new')])
        self.assertEqual(['removed'], result['old->new']['removed'])
        self.assertEqual(['added'], result['old->new']['added'])
        with self.assertRaisesRegex(ValueError, 'Missing requested target'):
            MODULE.compare({'old': {'a'}}, {'new': {'a'}}, [('old', 'absent')])

    def test_missing_or_empty_dump_is_an_error(self):
        for text in ('', '// Targets: [iosArm64]\n// Library unique name: <empty>'):
            with self.assertRaises(ValueError):
                MODULE.declarations_by_target(text)

    def test_unknown_target_annotation_cannot_silently_drop_declarations(self):
        with self.assertRaisesRegex(ValueError, 'Unknown declaration targets'):
            MODULE.declarations_by_target('''// Targets: [iosArm64]
// Library unique name: <fixture>
// Targets: [typo]
final class Hidden // Hidden
''')


if __name__ == '__main__':
    unittest.main()
