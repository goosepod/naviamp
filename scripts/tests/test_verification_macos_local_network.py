import importlib.util
from pathlib import Path
import plistlib
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'configure-macos-local-network.py'
spec = importlib.util.spec_from_file_location('mac_network', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class MacLocalNetworkPackagingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name) / 'repo'
        self.app = Path(self.temp.name) / 'Naviamp.app'
        self.plist = self.app / 'Contents/Info.plist'
        self.plist.parent.mkdir(parents=True)
        with self.plist.open('wb') as handle:
            plistlib.dump({'CFBundleIdentifier': 'app.naviamp.test', 'CFBundleVersion': '60'}, handle)
        for folder, text in [('values', 'Connect to music servers.'), ('values-fr', 'Musique &amp; appareils.'), ('values-zh-rCN', '本地音乐。'), ('values-in', 'Musik lokal.')]:
            path = self.catalog(folder)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('<resources><string name="permission_local_network_usage">' + text + '</string></resources>')
        for relative, content in [
            ('core/domain/src/commonMain/kotlin/app/naviamp/domain/connect/NaviampConnectProtocol.kt',
             'const val NaviampConnectServiceType: String = "_naviamp-connect._tcp"'),
            ('core/app/src/commonMain/kotlin/app/naviamp/app/NaviampCastDiscoveryController.kt',
             'const val ServiceType = "_googlecast._tcp.local."'),
        ]:
            path = self.repo / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)

    def catalog(self, folder):
        return self.repo / 'core/ui/src/commonMain/composeResources' / folder / 'strings.xml'

    def test_generates_shared_services_and_native_language_names_without_changing_identity(self):
        self.assertEqual(4, module.configure(self.repo, self.app))
        module.configure(self.repo, self.app, check=True)
        with self.plist.open('rb') as handle:
            data = plistlib.load(handle)
        self.assertEqual('app.naviamp.test', data['CFBundleIdentifier'])
        self.assertEqual('60', data['CFBundleVersion'])
        self.assertEqual(['_googlecast._tcp', '_naviamp-connect._tcp'], data['NSBonjourServices'])
        self.assertEqual(['en', 'fr', 'id', 'zh-Hans'], data['CFBundleLocalizations'])
        self.assertIn('Musique & appareils.', (self.app / 'Contents/Resources/fr.lproj/InfoPlist.strings').read_text())

    def test_idempotence_preserves_other_localized_keys_and_handles_quotes(self):
        path = self.app / 'Contents/Resources/en.lproj/InfoPlist.strings'
        path.parent.mkdir(parents=True)
        path.write_text('"CFBundleDisplayName" = "Keep this name";\n')
        self.catalog('values').write_text('<resources><string name="permission_local_network_usage">Connect to &quot;music&quot; &amp; devices.</string></resources>')
        module.configure(self.repo, self.app)
        before = path.read_text()
        module.configure(self.repo, self.app)
        self.assertEqual(before, path.read_text())
        self.assertIn('"CFBundleDisplayName" = "Keep this name";', before)
        self.assertEqual(1, before.count('NSLocalNetworkUsageDescription'))
        module.configure(self.repo, self.app, check=True)

    def test_validation_rejects_missing_usage_or_locale_or_wrong_service(self):
        with self.assertRaises(ValueError):
            module.configure(self.repo, self.app, check=True)
        module.configure(self.repo, self.app)
        path = self.app / 'Contents/Resources/fr.lproj/InfoPlist.strings'
        path.unlink()
        with self.assertRaisesRegex(ValueError, 'translation: fr'):
            module.configure(self.repo, self.app, check=True)
        module.configure(self.repo, self.app)
        with self.plist.open('rb') as handle:
            data = plistlib.load(handle)
        data['NSBonjourServices'] = ['_wrong._tcp']
        with self.plist.open('wb') as handle:
            plistlib.dump(data, handle)
        with self.assertRaisesRegex(ValueError, 'Bonjour'):
            module.configure(self.repo, self.app, check=True)

    def test_missing_shared_translation_fails_before_modifying_the_bundle(self):
        before = self.plist.read_bytes()
        self.catalog('values-fr').write_text('<resources/>')
        with self.assertRaisesRegex(ValueError, 'missing permission_local_network_usage'):
            module.configure(self.repo, self.app)
        self.assertEqual(before, self.plist.read_bytes())


if __name__ == '__main__':
    unittest.main()
