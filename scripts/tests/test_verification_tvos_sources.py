import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "prepare-tvos-compose-sources.py"
spec = importlib.util.spec_from_file_location("tvos_sources", SCRIPT)
sources = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sources)


class SourceReplayTest(unittest.TestCase):
    def setUp(self):
        scratch = SCRIPT.parents[1] / "build"
        scratch.mkdir(exist_ok=True)
        temporary = tempfile.TemporaryDirectory(prefix="tvos-sources-", dir=scratch)
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def test_actual_reviewed_bundle_has_complete_source_inventories(self):
        bundle = SCRIPT.parents[1] / "build-support/tvos-compose/sources.zip"
        recipe, files = sources.load_bundle(bundle)
        self.assertEqual(696, len(json.loads(files["ui-files.json"])))
        self.assertEqual(18, len(json.loads(files["resources-files.json"])))
        self.assertEqual(16, len(sources.read_zip(files["settings-plugin.zip"])))
        self.assertTrue(all(entry["replayVerified"] for entry in recipe["producers"].values()))

    def test_tampered_bundle_is_rejected_before_unpacking_or_network(self):
        bundle = self.root / "wrong.zip"
        bundle.write_bytes(b"not the reviewed source")
        with self.assertRaisesRegex(ValueError, "reviewed identity"):
            sources.prepare(bundle, self.root / "destination", {})
        self.assertFalse((self.root / "destination").exists())

    def archive(self, name, data=b"x", mode=stat.S_IFREG | 0o644):
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as archive:
            entry = zipfile.ZipInfo(name)
            entry.external_attr = mode << 16
            archive.writestr(entry, data)
        data = output.getvalue()
        # ZipInfo normalizes backslashes on Windows; exercise the actual wire spelling.
        if "\\" in name:
            data = data.replace(name.replace("\\", "/").encode(), name.encode())
        return data

    def test_archive_rejects_escape_git_paths_symlinks_and_size_excess(self):
        for name in ("../escape", "/absolute", "C:/escape", "back\\slash", ".git/config", "a//b", "a/./b"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                sources.read_zip(self.archive(name))
        with self.assertRaises(ValueError):
            sources.read_zip(self.archive("link", b"target", stat.S_IFLNK | 0o777))
        with self.assertRaises(ValueError):
            sources.read_zip(self.archive("big", b"1234"), maximum=3)

    def test_duplicate_archive_entries_are_rejected(self):
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as archive:
            archive.writestr("same", b"first")
            with self.assertWarns(UserWarning):
                archive.writestr("same", b"second")
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            sources.read_zip(output.getvalue())

    def fixture(self):
        source = self.root / "original"
        source.mkdir()
        sources.git(source, "init")
        sources.git(source, "config", "user.name", "Source replay test")
        sources.git(source, "config", "user.email", "fixture@example.invalid")
        for name in ("deleted.kt", "renamed.kt"):
            (source / name).write_bytes(b"original\n")
        sources.git(source, "add", ".")
        sources.git(source, "commit", "-m", "Fixture base")
        base = sources.git(source, "rev-parse", "HEAD").decode().strip()
        (source / "deleted.kt").unlink()
        (source / "renamed.kt").rename(source / "moved.kt")
        (source / "new.kt").write_bytes(b'val embedded = """ literal spaces  \n"""\n')
        sources.git(source, "add", "-A")
        delta = sources.git(source, "diff", "--cached", base, "--binary", "--full-index", "--no-renames")
        tree = sources.git(source, "write-tree").decode().strip()
        manifest = {"deleted.kt": None, "renamed.kt": None,
                    **{name: hashlib.sha256((source / name).read_bytes()).hexdigest()
                       for name in ("new.kt", "moved.kt")}}
        return source, base, delta, tree, manifest

    def test_real_git_replay_preserves_whitespace_additions_moves_and_deletions(self):
        original, base, delta, tree, manifest = self.fixture()
        with patch.dict(os.environ, {"GIT_CONFIG_COUNT": "1", "GIT_CONFIG_KEY_0": "apply.whitespace",
                                     "GIT_CONFIG_VALUE_0": "fix"}):
            result = sources.replay("unused", self.root / "replay", base, delta, manifest, tree, original)
        self.assertEqual(4, result["paths"])
        self.assertTrue(result["referenceObjectsShared"])
        self.assertEqual((original / "new.kt").read_bytes(), (self.root / "replay/new.kt").read_bytes())
        self.assertEqual(base, sources.git(original, "rev-parse", "HEAD").decode().strip())

    def test_wrong_tree_or_file_identity_cannot_pass(self):
        original, base, delta, tree, manifest = self.fixture()
        with self.assertRaisesRegex(ValueError, "source tree"):
            sources.replay("unused", self.root / "wrong-tree", base, delta, manifest, "0" * 40, original)
        manifest["new.kt"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "Source bytes"):
            sources.replay("unused", self.root / "wrong-bytes", base, delta, manifest, tree, original)

    def test_existing_destination_is_never_overwritten(self):
        destination = self.root / "existing"
        destination.mkdir()
        sentinel = destination / "keep"
        sentinel.write_text("owned by another run")
        with self.assertRaises(ValueError):
            sources.replay("unused", destination, "base", b"", {}, "tree")
        self.assertEqual("owned by another run", sentinel.read_text())


if __name__ == "__main__":
    unittest.main()
