import importlib.util
from pathlib import Path
import unittest


SPEC = importlib.util.spec_from_file_location("tvos_deployment", Path(__file__).parents[1] / "audit-tvos-native-deployment.py")
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)
RUNTIME_SPEC = importlib.util.spec_from_file_location("tvos_renderer_runtime", Path(__file__).parents[1] / "probe-tvos-renderer-runtime.py")
RUNTIME = importlib.util.module_from_spec(RUNTIME_SPEC)
RUNTIME_SPEC.loader.exec_module(RUNTIME)


class TvosDeploymentTest(unittest.TestCase):
    def test_runtime_selection_normalizes_optional_patch_versions(self):
        self.assertEqual(RUNTIME.numeric_version("18.5"), RUNTIME.numeric_version("18.5.0"))
        self.assertLess(RUNTIME.numeric_version("18.5.0"), RUNTIME.numeric_version("26.5"))
        with self.assertRaises(ValueError):
            RUNTIME.numeric_version("18.5; unsafe")

    def fixture(self, platform="8", minimum="27.2"):
        return f"""Archive : /tmp/helper.a
/tmp/helper.a(first.o):
Load command 0
      cmd LC_BUILD_VERSION
  cmdsize 24
 platform {platform}
    minos {minimum}
      sdk 27.2
   ntools 0
"""

    def test_default_sdk_floor_is_rejected_by_the_requested_probe_floor(self):
        entries = AUDIT.objects(self.fixture())
        self.assertEqual("minimum exceeds 17.0", AUDIT.violations(entries, 8, "17.0")[0]["reason"])

    def test_every_archive_member_is_checked(self):
        raw = self.fixture(minimum="17.0") + self.fixture().replace("first.o", "second.o")
        entries = AUDIT.objects(raw)
        self.assertEqual(2, len(entries))
        rejected = AUDIT.violations(entries, 8, "17.0")
        self.assertEqual(1, len(rejected))
        self.assertIn("second.o", rejected[0]["object"])

    def test_ios_and_device_objects_do_not_count_as_tvos_simulator_inputs(self):
        for platform in ("2", "3", "7"):
            self.assertTrue(AUDIT.violations(AUDIT.objects(self.fixture(platform, "17.0")), 8, "17.0"))

    def test_missing_or_conflicting_metadata_never_passes(self):
        self.assertTrue(AUDIT.violations(AUDIT.objects("/tmp/helper.a(empty.o):\nLoad command 0\n"), 8, "17.0"))
        with self.assertRaises(ValueError):
            AUDIT.objects("no object inventory")
        raw = self.fixture() + self.fixture().split("Load command 0", 1)[1]
        with self.assertRaisesRegex(ValueError, "Conflicting"):
            AUDIT.objects(raw)

    def test_legacy_tvos_metadata_and_patch_versions_are_explicit(self):
        raw = "/tmp/helper.a(old.o):\n cmd LC_VERSION_MIN_TVOS\n cmdsize 16\n version 9.0\n sdk 14.3\n"
        self.assertFalse(AUDIT.violations(AUDIT.objects(raw), 3, "17.0"))
        self.assertFalse(AUDIT.violations(AUDIT.objects(self.fixture(minimum="17.0.0")), 8, "17.0"))
        with self.assertRaises(ValueError):
            AUDIT.version("17.0; echo unsafe")


if __name__ == "__main__":
    unittest.main()
