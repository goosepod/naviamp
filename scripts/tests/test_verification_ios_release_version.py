import importlib.util
from pathlib import Path
import tempfile
import unittest
import plistlib
import re

SCRIPT = Path(__file__).resolve().parents[1] / "validate-ios-version.py"
SPEC = importlib.util.spec_from_file_location("ios_version", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class IosReleaseVersionTest(unittest.TestCase):
    def test_app_local_defaults_declaration_is_an_actual_app_resource(self):
        root = SCRIPT.parents[1]
        manifest = root / "apps/ios/iosApp/PrivacyInfo.xcprivacy"
        data = plistlib.loads(manifest.read_bytes())
        self.assertEqual([{"NSPrivacyAccessedAPIType": "NSPrivacyAccessedAPICategoryUserDefaults",
                           "NSPrivacyAccessedAPITypeReasons": ["CA92.1"]},
                          {"NSPrivacyAccessedAPIType": "NSPrivacyAccessedAPICategoryFileTimestamp",
                           "NSPrivacyAccessedAPITypeReasons": ["C617.1"]}], data["NSPrivacyAccessedAPITypes"])
        project = (root / "apps/ios/Naviamp.xcodeproj/project.pbxproj").read_text()
        resource_id = re.search(r"(\w+) /\* PrivacyInfo.xcprivacy in Resources \*/ =", project).group(1)
        resources = re.search(r"A10000000000000000000032 /\* Resources \*/ = \{[^}]+", project).group(0)
        self.assertIn(resource_id, resources)

    def test_audited_sdk_imports_have_manifests_in_both_slices(self):
        root = SCRIPT.parents[1] / "platforms/ios/vendor/bass"
        expected = [
            {"NSPrivacyAccessedAPIType": "NSPrivacyAccessedAPICategoryFileTimestamp",
             "NSPrivacyAccessedAPITypeReasons": ["C617.1"]},
            {"NSPrivacyAccessedAPIType": "NSPrivacyAccessedAPICategorySystemBootTime",
             "NSPrivacyAccessedAPITypeReasons": ["35F9.1"]},
        ]
        for sdk in ("bass", "bassmidi"):
            manifests = list((root / f"{sdk}.xcframework").glob(f"*/{sdk}.framework/PrivacyInfo.xcprivacy"))
            self.assertEqual(2, len(manifests))
            for manifest in manifests:
                self.assertEqual(expected, plistlib.loads(manifest.read_bytes())["NSPrivacyAccessedAPITypes"])

    def test_real_project_matches_canonical_release(self):
        MODULE.validate(SCRIPT.parents[1])

    def test_both_configurations_and_missing_metadata_are_checked(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "VERSION").write_text("v2.10.0-beta.1+test\n")
            (root / "VERSION_CODE").write_text("60\n")
            project = root / "apps/ios/Naviamp.xcodeproj/project.pbxproj"
            project.parent.mkdir(parents=True)
            valid = "MARKETING_VERSION = 2.10.0; CURRENT_PROJECT_VERSION = 60;\n" * 2
            project.write_text(valid)
            MODULE.validate(root)
            for invalid in (valid.replace("2.10.0", "2.0.0", 1), valid.replace("60", "44", 1),
                            valid.splitlines()[0], ""):
                with self.subTest(metadata=invalid):
                    project.write_text(invalid)
                    with self.assertRaises(ValueError):
                        MODULE.validate(root)
