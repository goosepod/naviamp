import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("prepare", Path(__file__).with_name("prepare.py"))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class ReleasePackagingTest(unittest.TestCase):
    def setUp(self):
        self.release = {"tag_name": "v2.10.0", "draft": False, "prerelease": False,
                        "published_at": "2026-11-03T12:00:00Z", "assets": [{
                            "name": "Naviamp-v2.10.0-linux-x64-release.zip",
                            "browser_download_url": "https://github.com/goosepod/naviamp/releases/download/v2.10.0/Naviamp-v2.10.0-linux-x64-release.zip",
                            "digest": "sha256:" + "a" * 64}]}

    def test_rejects_unpublished_and_unpinned_inputs(self):
        for key, value in (("draft", True), ("prerelease", True), ("tag_name", "v2.10.0/../main")):
            release = copy.deepcopy(self.release)
            release[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                prepare.release_source(release)
        for key, value in (("digest", ""), ("browser_download_url", "https://example.com/application.zip")):
            release = copy.deepcopy(self.release)
            release["assets"][0][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                prepare.release_source(release)

    def test_refuses_ambiguous_release_assets(self):
        self.release["assets"] *= 2
        with self.assertRaises(ValueError):
            prepare.release_source(self.release)

    def test_package_is_pinned_and_preserves_narrow_persistent_permissions(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            prepare.prepare(self.release, output)
            manifest = json.loads((output / f"{prepare.APP_ID}.json").read_text())
            self.assertEqual("a" * 64, manifest["modules"][-1]["sources"][0]["sha256"])
            self.assertIn("--persist=.local/share/naviamp", manifest["finish-args"])
            self.assertIn("--talk-name=org.freedesktop.secrets", manifest["finish-args"])
            self.assertFalse(any(p in manifest["finish-args"] for p in
                                 ("--filesystem=home", "--filesystem=host", "--socket=session-bus", "--device=all")))
            self.assertEqual("2.10.0", json.loads((output / "source.json").read_text())["application_version"])


if __name__ == "__main__":
    unittest.main()
