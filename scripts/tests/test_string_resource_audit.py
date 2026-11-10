import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location(
    'string_audit', Path(__file__).resolve().parents[1] / 'audit-string-resources.py')
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


class StringResourceAuditTest(unittest.TestCase):
    def test_inventory_includes_strings_plurals_and_arrays(self):
        self.assertEqual({'title', 'count', 'choices'}, AUDIT.resource_names('''
            <resources><string name="title">Title</string>
            <plurals name="count"><item quantity="other">Items</item></plurals>
            <string-array name="choices"><item>Choice</item></string-array>
            <color name="background">#000000</color></resources>'''))

    def test_indirect_native_and_conditional_consumers_are_retained(self):
        keys = {'first_label', 'second_label', 'native_permission', 'alias_target', 'dead_label'}
        found = AUDIT.references(keys, [
            ('ui.kt', 'if (enabled) Res.string.first_label else Res.string.second_label'),
            ('package.py', "RESOURCE_KEY = 'native_permission'"),
            ('strings.xml', '@string/alias_target'),
        ])
        self.assertEqual(['ui.kt'], found['first_label'])
        self.assertEqual(['ui.kt'], found['second_label'])
        self.assertEqual(['package.py'], found['native_permission'])
        self.assertEqual(['strings.xml'], found['alias_target'])
        self.assertEqual([], found['dead_label'])

    def test_android_manifest_is_not_a_catalog(self):
        self.assertEqual(set(), AUDIT.resource_names('<manifest package="app.naviamp"/>'))
