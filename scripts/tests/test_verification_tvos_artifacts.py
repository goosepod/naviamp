import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "tvos-compose-artifacts.py"
spec = importlib.util.spec_from_file_location("tvos_artifacts", SCRIPT)
artifacts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(artifacts)


class ArtifactInventoryTest(unittest.TestCase):
    def setUp(self):
        # Windows sandbox TEMP has a long prefix; use an owned short path for Maven coordinates.
        scratch = SCRIPT.parents[1] / "build"
        scratch.mkdir(exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix="tvos-artifacts-", dir=scratch)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repositories = {name: self.root / name for name in ("ui", "resources")}
        for (group, module, version), owner in artifacts.expected_coordinates().items():
            directory = self.repositories[owner].joinpath(*group.split("."), module, version)
            directory.mkdir(parents=True)
            umbrella = artifacts.re.sub(r"-(tvosarm64|tvossimulatorarm64)$", "", module, flags=artifacts.re.I)
            target = next((target for suffix, target in artifacts.TARGETS.items() if module.lower().endswith("-" + suffix)), None)
            data = directory / "fixture.klib"
            data.write_bytes(b"test artifact bytes")
            metadata = {"component": {"group": group, "module": umbrella, "version": version},
                        "variants": [{"attributes": {"org.jetbrains.kotlin.native.target": target},
                                      "files": [{"url": data.name, "size": data.stat().st_size,
                                                 "sha256": artifacts.digest(data)}]}]}
            (directory / f"{module}-{version}.module").write_text(json.dumps(metadata))
        self.leaf = next(self.repositories["ui"].rglob("ui-tvosarm64-1.12.2.module"))

    def rewrite(self, change):
        metadata = json.loads(self.leaf.read_text())
        change(metadata)
        self.leaf.write_text(json.dumps(metadata))

    def test_exact_reviewed_inventory_is_stable_and_hashes_actual_bytes(self):
        first = artifacts.inventory(self.repositories)
        self.assertEqual(first, artifacts.inventory(self.repositories))
        self.assertEqual(104, len(first))
        self.assertEqual(52, len([p for p in first if p.endswith(".module")]))

    def test_missing_publication_fails(self):
        self.leaf.unlink()
        with self.assertRaisesRegex(ValueError, "inventory mismatch"):
            artifacts.inventory(self.repositories)

    def test_original_platform_publication_cannot_enter_private_bundle(self):
        path = self.repositories["ui"] / "org/jetbrains/compose/ui/ui-iosarm64/1.12.2/invalid.klib"
        path.parent.mkdir(parents=True)
        path.write_bytes(b"not reviewed")
        with self.assertRaisesRegex(ValueError, "Unreviewed publication"):
            artifacts.inventory(self.repositories)

    def test_wrong_target_leaf_fails(self):
        self.rewrite(lambda m: m["variants"][0]["attributes"].update({"org.jetbrains.kotlin.native.target": "ios_arm64"}))
        with self.assertRaisesRegex(ValueError, "wrong-target"):
            artifacts.inventory(self.repositories)

    def test_metadata_cannot_reference_external_file(self):
        self.rewrite(lambda m: m["variants"][0]["files"][0].update({"url": "../outside.klib"}))
        with self.assertRaisesRegex(ValueError, "Non-local artifact"):
            artifacts.inventory(self.repositories)

    def test_changed_artifact_is_rejected_against_gradle_metadata(self):
        (self.leaf.parent / "fixture.klib").write_bytes(b"changed bytes")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            artifacts.inventory(self.repositories)

    def test_manifest_requires_separate_identity_and_detects_extra_file(self):
        manifest = self.root / "manifest.json"
        provenance = self.root / "provenance.json"
        provenance.write_text('{"fixture": "test-only"}')
        common = [sys.executable, str(SCRIPT), "--ui-repository", str(self.repositories["ui"]),
                  "--resources-repository", str(self.repositories["resources"]), "--manifest", str(manifest)]
        subprocess.run(common + ["seal", "--provenance", str(provenance)], check=True, capture_output=True)
        identity = artifacts.digest(manifest)
        subprocess.run(common + ["verify", "--expected-sha256", identity], check=True, capture_output=True)
        self.assertNotEqual(0, subprocess.run(common + ["verify"], capture_output=True).returncode)
        self.assertNotEqual(0, subprocess.run(common + ["verify", "--expected-sha256", "0" * 64], capture_output=True).returncode)
        (self.leaf.parent / "extra.jar").write_bytes(b"extra")
        result = subprocess.run(common + ["verify", "--expected-sha256", identity], capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("inventory differ", result.stderr)


if __name__ == "__main__":
    unittest.main()
