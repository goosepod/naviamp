import importlib.util
import json
from pathlib import Path
import plistlib
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location("tvos_feasibility", Path(__file__).parents[1] / "tvos-feasibility.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
RUNTIME_SPEC = importlib.util.spec_from_file_location("tvos_runtime_probe", Path(__file__).parents[1] / "probe-tvos-bass-runtime.py")
RUNTIME = importlib.util.module_from_spec(RUNTIME_SPEC)
RUNTIME_SPEC.loader.exec_module(RUNTIME)


class TvosFeasibilityTest(unittest.TestCase):
    def test_runtime_results_accept_c_bool_and_require_every_native_check(self):
        for success in (True, 1):
            result = dict.fromkeys(RUNTIME.CHECKS, success)
            self.assertTrue(RUNTIME.native_checks_pass(result))
            for key in RUNTIME.CHECKS:
                for failure in (False, 0, None, "1", "true"):
                    self.assertFalse(RUNTIME.native_checks_pass({**result, key: failure}))
                missing = result.copy()
                del missing[key]
                self.assertFalse(RUNTIME.native_checks_pass(missing))

    def archive(self, root, platform="tvos", simulator=True, architectures=None):
        path = root / "bass.zip"
        libraries = []
        with zipfile.ZipFile(path, "w") as z:
            for variant in (["device", "simulator"] if simulator else ["device"]):
                identifier = "tvos-arm64" + ("-simulator" if variant == "simulator" else "")
                item = dict(LibraryIdentifier=identifier, LibraryPath="bass.framework",
                            SupportedPlatform=platform, SupportedArchitectures=architectures or ["arm64"])
                if variant == "simulator":
                    item["SupportedPlatformVariant"] = variant
                libraries.append(item)
                z.writestr(f"bass.xcframework/{identifier}/bass.framework/bass", b"fixture, not a binary")
            z.writestr("bass.xcframework/Info.plist", plistlib.dumps({"AvailableLibraries": libraries}))
        return path

    def test_partial_inventory_never_claims_playback_or_abi_acceptance(self):
        with tempfile.TemporaryDirectory() as directory:
            result = MODULE.inspect_bass(self.archive(Path(directory)))
        self.assertIn("bassmix", result["missingRequiredComponents"])
        self.assertFalse(result["completePlaybackInventory"])
        self.assertFalse(result["machOAndLinkValidated"])
        self.assertFalse(result["playbackValidated"])

    def test_ios_arm64_is_rejected_even_with_tvos_directory_name(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "non-tvOS"):
                MODULE.inspect_bass(self.archive(Path(directory), platform="ios"))

    def test_device_only_archive_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "pair required"):
                MODULE.inspect_bass(self.archive(Path(directory), simulator=False))

    def test_intel_only_simulator_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "missing arm64"):
                MODULE.inspect_bass(self.archive(Path(directory), architectures=["x86_64"]))

    def test_manifest_mismatch_is_visible_and_substitutions_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = self.archive(root)
            manifest = root / "manifest.json"
            catalog = root / "catalog.toml"
            catalog.write_text('[versions]\ncompose = "1.11.0"\n')
            manifest.write_text(json.dumps(dict(schema=2, mappings={}, gradlePlugin="1.12.0")))
            self.assertFalse(MODULE.report(archive, manifest, catalog)["compose"]["sameComposeLine"])
            manifest.write_text(json.dumps(dict(schema=2, mappings={"1.11": "1.12"}, gradlePlugin="1.12.0")))
            with self.assertRaisesRegex(ValueError, "substitutions"):
                MODULE.report(archive, manifest, catalog)


if __name__ == "__main__":
    unittest.main()
