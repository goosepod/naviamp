import importlib.util
from pathlib import Path
import tempfile
import time
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "collect-android-device-evidence.py"
spec = importlib.util.spec_from_file_location("android_evidence", SCRIPT)
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


class AndroidEvidenceTest(unittest.TestCase):
    def setUp(self):
        scratch = SCRIPT.parents[1] / "build"
        scratch.mkdir(exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix="android-evidence-", dir=scratch)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.started = time.time() - 1
        self.app = self.write("apps/android", '<testcase classname="native.Boundary" name="passes"/><testcase name="optional"><skipped/></testcase>')
        self.ui = self.write("core/ui", f'<testcase classname="{evidence.REQUIRED_CLASS}" name="{evidence.REQUIRED_METHOD}"/>')

    def write(self, module, cases):
        path = self.root / module / "build/outputs/androidTest-results/connected/TEST-device.xml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("<testsuite>" + cases + "</testsuite>")
        return path

    def test_fresh_results_have_short_names_hashes_and_keep_optional_skips_visible(self):
        report = evidence.collect(self.root, self.root / "evidence", self.started)
        self.assertTrue(report["passed"], report)
        self.assertEqual(1, report["modules"]["apps/android"]["skips"])
        self.assertEqual({"junit/app-000.xml", "junit/ui-000.xml"},
                         {Path(item["saved"]).as_posix() for item in report["files"]})
        self.assertTrue(all(len(item["sha256"]) == 64 for item in report["files"]))

    def test_missing_runtime_case_fails_but_retains_available_evidence(self):
        self.write("core/ui", '<testcase classname="other.Class" name="passes"/>')
        report = evidence.collect(self.root, self.root / "evidence", self.started)
        self.assertFalse(report["passed"])
        self.assertTrue((self.root / "evidence/junit/ui-000.xml").exists())
        self.assertTrue((self.root / "evidence/summary.json").exists())

    def test_skipped_or_failed_required_case_cannot_pass(self):
        for tag in ("skipped", "failure", "error"):
            with self.subTest(tag=tag):
                self.write("core/ui", f'<testcase classname="{evidence.REQUIRED_CLASS}" name="{evidence.REQUIRED_METHOD}"><{tag}/></testcase>')
                self.assertFalse(evidence.collect(self.root, self.root / tag, self.started)["passed"])

    def test_missing_app_and_stale_xml_are_rejected(self):
        self.app.unlink()
        report = evidence.collect(self.root, self.root / "missing", time.time() + 30)
        self.assertFalse(report["passed"])
        self.assertTrue(any("Missing" in error for error in report["errors"]))
        self.assertTrue(any("Stale" in error for error in report["errors"]))

    def test_existing_destination_cannot_mask_an_earlier_run(self):
        output = self.root / "evidence"
        output.mkdir()
        with self.assertRaises(FileExistsError):
            evidence.collect(self.root, output, self.started)

    def test_invalid_start_timestamp_cannot_bypass_freshness(self):
        for value in (float("nan"), float("inf"), 0, -1):
            with self.assertRaises(ValueError):
                evidence.collect(self.root, self.root / "invalid", value)


if __name__ == "__main__":
    unittest.main()
