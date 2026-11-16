import importlib.util
from pathlib import Path
import plistlib
import re
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

    def test_ios_uses_shared_translations_without_declaring_unused_bonjour_services(self):
        ios = self.app / 'ios'
        ios.mkdir()
        with (ios / 'Info.plist').open('wb') as handle:
            plistlib.dump({'CFBundleIdentifier': 'app.naviamp.ios'}, handle)
        self.assertEqual(4, module.configure(self.repo, ios, platform='ios'))
        module.configure(self.repo, ios, platform='ios', check=True)
        with (ios / 'Info.plist').open('rb') as handle:
            data = plistlib.load(handle)
        self.assertNotIn('NSBonjourServices', data)
        self.assertEqual('app.naviamp.ios', data['CFBundleIdentifier'])
        self.assertIn('Musique & appareils.', (ios / 'fr.lproj/InfoPlist.strings').read_text())

    def test_repository_ios_resources_and_signing_opt_in_are_consistent(self):
        repo = SCRIPT.parent.parent
        ios = repo / 'apps/ios/iosApp'
        module.configure(repo, ios, platform='ios', check=True)
        languages, _ = module.shared_metadata(repo)
        project = (repo / 'apps/ios/Naviamp.xcodeproj/project.pbxproj').read_text(encoding='utf-8')
        referenced = set(re.findall(r'path = "([^"/]+)\.lproj/InfoPlist\.strings"', project))
        self.assertEqual(set(languages), referenced)
        variant = re.search(r'([A-F0-9]+) /\* InfoPlist.strings \*/ = \{isa = PBXVariantGroup;', project).group(1)
        build = re.search(r'([A-F0-9]+) /\* InfoPlist.strings in Resources \*/ = \{isa = PBXBuildFile; fileRef = ' + variant, project).group(1)
        resource_phase = re.search(r'isa = PBXResourcesBuildPhase;[^\n]+', project).group(0)
        self.assertIn(build, resource_phase)
        self.assertEqual(2, project.count('NAVIAMP_UPNP_MULTICAST_AVAILABLE = NO;'))
        self.assertNotIn('CODE_SIGN_ENTITLEMENTS', project)
        with (ios / 'NaviampUpnp.entitlements').open('rb') as handle:
            self.assertEqual({'com.apple.developer.networking.multicast': True}, plistlib.load(handle))
        with (ios / 'Info.plist').open('rb') as handle:
            self.assertEqual('$(NAVIAMP_UPNP_MULTICAST_AVAILABLE)', plistlib.load(handle)['NaviampUpnpMulticastAvailable'])


if __name__ == '__main__':
    unittest.main()
