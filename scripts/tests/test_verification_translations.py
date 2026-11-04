import importlib.util
import re
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('translations', ROOT / 'scripts/validate-translations.py')
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class TranslationValidationTest(unittest.TestCase):
    def validate(self, translated, source='<string name="hello">Hello %1$s</string>'):
        with tempfile.TemporaryDirectory() as directory:
            source_path = Path(directory) / 'source.xml'
            translation_path = Path(directory) / 'translation.xml'
            source_path.write_text(f'<resources>{source}</resources>')
            translation_path.write_text(f'<resources>{translated}</resources>')
            validator.validate(source_path, translation_path)

    def test_partial_catalog_and_reordered_arguments(self):
        self.validate('')
        self.validate('<string name="hello">%2$d %1$s</string>',
                      '<string name="hello">%1$s %2$d</string>')
        self.validate('<string name="hello">%1$d%</string>',
                      '<string name="hello">%1$d%</string>')
        self.validate('<string name="hello">100%%</string>',
                      '<string name="hello">100%%</string>')

    def test_invalid_catalogs_fail(self):
        cases = [
            '<string name="hello">Missing argument</string>',
            '<string name="hello">Wrong %1$d</string>',
            '<string name="hello">%s</string>',
            '<string name="hello">%1$s %1$s</string>',
            '<string name="unknown">%1$s</string>',
            '<string name="hello">%1$s</string><string name="hello">%1$s</string>',
            '<string>%1$s</string>',
            '<plurals name="hello"><item quantity="other">%1$s</item></plurals>',
            '<string name="hello"><b>%1$s</b></string>',
            '<string name="hello" unexpected="true">%1$s</string>',
            '<string name="hello">broken',
        ]
        for case in cases:
            with self.subTest(case=case), self.assertRaises((ValueError, ET.ParseError)):
                self.validate(case)

    def test_plural_categories_can_vary_but_arguments_must_match(self):
        source = '<plurals name="n"><item quantity="one">%1$d item</item><item quantity="other">%1$d items</item></plurals>'
        self.validate('<plurals name="n"><item quantity="few">%1$d</item><item quantity="other">%1$d</item></plurals>', source)
        for items in [
            '<item quantity="one">%1$d</item>',
            '<item quantity="other">%1$d</item><item quantity="other">%1$d</item>',
            '<item quantity="invalid">%1$d</item><item quantity="other">%1$d</item>',
            '<item quantity="other">no argument</item>',
        ]:
            with self.subTest(items=items), self.assertRaises(ValueError):
                self.validate(f'<plurals name="n">{items}</plurals>', source)

    def test_arrays_preserve_length_and_arguments(self):
        source = '<string-array name="a"><item>%1$s</item><item>B</item></string-array>'
        self.validate(source, source)
        with self.assertRaises(ValueError):
            self.validate('<string-array name="a"><item>%1$s</item></string-array>', source)

    def test_entity_declarations_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'strings.xml'
            path.write_text('<!DOCTYPE resources [<!ENTITY x "bad">]><resources/>')
            with self.assertRaises(ValueError):
                validator.catalog(path)

    def test_required_checks_and_release_evidence_remain_gated(self):
        workflow = (ROOT / '.github/workflows/verify.yml').read_text()
        self.assertNotIn('paths-ignore:', workflow)
        self.assertIn("github.event_name == 'pull_request' &&", workflow)
        evidence = workflow.split('  evidence:', 1)[1]
        self.assertIn("needs.changes.outputs.run_full == 'true'", evidence)
        for name in ['Shared and Android tests', 'Android emulator native tests (API ${{ matrix.api-level }})',
                     'Desktop native tests (macOS)', 'Desktop native tests (Windows)',
                     'Desktop native tests (Linux)', 'iOS simulator tests']:
            self.assertIn('name: ' + name, workflow)
        shared = workflow.split('  shared-android:', 1)[1].split('  desktop-macos:', 1)[0]
        self.assertIn('if: always()', shared)
        self.assertIn("needs.changes.result != 'success'", shared)
        self.assertIn('python3 scripts/validate-translations.py --base', shared)
        android = workflow.split('  android-device:', 1)[1].split('  desktop-windows:', 1)[0]
        self.assertIn('if: always()', android)
        self.assertIn("needs.changes.result != 'success'", android)
        self.assertIn('api-level: [30, 35]', android)
        for job in ['desktop-macos', 'desktop-windows', 'desktop-linux', 'ios']:
            block = re.split(r'\n  [a-z][a-z-]*:', workflow.split(f'  {job}:', 1)[1])[0]
            self.assertIn("if: needs.changes.outputs.run_full == 'true'", block)


if __name__ == '__main__':
    unittest.main()
