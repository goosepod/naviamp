"""Exercise provenance failures independently of Windows SDK package validation."""

import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import zipfile


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('windows_store_package', ROOT / 'scripts/windows-store-package.py')
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)


def pe():
    data = bytearray(70)
    data[:2] = b'MZ'
    struct.pack_into('<I', data, 60, 64)
    data[64:68] = b'PE\0\0'
    struct.pack_into('<H', data, 68, 0x8664)
    return bytes(data)


class StorePackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'release.zip'
        self.payload = {name: (b'java-options=-Xmx512m\n' if name.endswith('.cfg') else pe())
                        for name in tool.REQUIRED_PAYLOAD}
        self.payload['Naviamp/app/example.jar'] = b'unchanged released jar'
        self.write_source()
        self.config = tool.release() | {'sha256': tool.file_digest(self.source)}
        self.release_patch = patch.object(tool, 'release', return_value=self.config)
        self.release_patch.start()
        self.addCleanup(self.release_patch.stop)

    def write_source(self, extras=None):
        with zipfile.ZipFile(self.source, 'w') as archive:
            for name, data in (self.payload | (extras or {})).items():
                archive.writestr(name, data)

    def create_package(self, changes=None, extras=None):
        layout = self.root / 'layout'
        tool.prepare(self.source, layout)
        contents = {p.relative_to(layout).as_posix(): p.read_bytes() for p in layout.rglob('*') if p.is_file()}
        contents |= {'AppxBlockMap.xml': b'<BlockMap/>', '[Content_Types].xml': b'<Types/>'}
        contents |= changes or {}
        contents |= extras or {}
        package = self.root / 'app.msix'
        with zipfile.ZipFile(package, 'w') as archive:
            for name, data in contents.items():
                archive.writestr(name, data)
        return package

    def test_published_identity_and_icon_dimensions(self):
        config = json.loads((tool.METADATA / 'release.json').read_text())
        self.assertEqual(config['tag'], 'v2.9.0')
        self.assertEqual(config['msix_version'], '2.9.57.0')
        tool.check_manifest((tool.METADATA / 'AppxManifest.xml').read_bytes(), config)
        for name, size in [('StoreLogo.png', 50), ('Square44x44Logo.png', 44), ('Square150x150Logo.png', 150)]:
            data = (tool.METADATA / 'Assets' / name).read_bytes()
            self.assertEqual(data[:8], b'\x89PNG\r\n\x1a\n')
            self.assertEqual(struct.unpack('>II', data[16:24]), (size, size))

    def test_all_released_files_preserved_and_pending_checks_explicit(self):
        package = self.create_package()
        evidence = self.root / 'provenance.json'
        tool.verify(self.source, package, evidence)
        report = json.loads(evidence.read_text())
        self.assertEqual(report['payload_file_count'], len(self.payload))
        self.assertEqual(report['interactive_windows_acceptance'], 'pending')
        self.assertEqual(report['store_certification'], 'pending')

    def test_rejects_substituted_release_before_extraction(self):
        self.source.write_bytes(b'different release')
        with self.assertRaisesRegex(ValueError, 'SHA-256'):
            tool.prepare(self.source, self.root / 'layout')
        self.assertFalse((self.root / 'layout').exists())

    def test_rejects_changed_application_payload(self):
        package = self.create_package(changes={'Naviamp/app/example.jar': b'unreleased change'})
        with self.assertRaisesRegex(ValueError, 'application file changed'):
            tool.verify(self.source, package, self.root / 'evidence.json')

    def test_rejects_extra_unreleased_payload(self):
        package = self.create_package(extras={'Naviamp/app/new.jar': b'new'})
        with self.assertRaisesRegex(ValueError, 'Unexpected or missing'):
            tool.verify(self.source, package, self.root / 'evidence.json')

    def test_rejects_wrong_store_publisher(self):
        manifest = (tool.METADATA / 'AppxManifest.xml').read_bytes().replace(b'CN=71F0', b'CN=0000')
        with self.assertRaisesRegex(ValueError, 'identity'):
            tool.check_manifest(manifest, self.config)

    def test_rejects_traversal_and_windows_case_collisions(self):
        for name in ['Naviamp/../../outside', '/Naviamp/absolute', 'Naviamp/app/file:stream',
                     'Naviamp\\escape', 'Naviamp/app/file.', 'Naviamp/APP/example.jar']:
            with self.subTest(name=name):
                self.write_source({name: b'unexpected'})
                # ZipInfo normalizes Windows separators when creating an archive
                # on Windows. Put the unsafe name into both raw ZIP headers so
                # this exercises the reader on every build host.
                if name == 'Naviamp\\escape':
                    self.source.write_bytes(self.source.read_bytes().replace(b'Naviamp/escape', b'Naviamp\\escape'))
                with zipfile.ZipFile(self.source) as archive, self.assertRaises(ValueError):
                    tool.checked_entries(archive)

    def test_rejects_wrong_architecture(self):
        data = bytearray(pe())
        struct.pack_into('<H', data, 68, 0x14c)
        self.write_source({'Naviamp/Naviamp.exe': bytes(data)})
        with zipfile.ZipFile(self.source) as archive, self.assertRaisesRegex(ValueError, 'x64 PE'):
            tool.checked_entries(archive)


if __name__ == '__main__':
    unittest.main()
