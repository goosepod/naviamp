import base64
import tempfile
import unittest
from pathlib import Path

from prepare import APP_ID
from verify_public import verify_descriptors


class DescriptorTests(unittest.TestCase):
    def test_installer_cannot_substitute_remote_key_or_application(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = {"url": "https://example.org/naviamp/"}
            key = b"accepted public key"
            encoded = base64.b64encode(key).decode()
            reference = f"[Flatpak Ref]\nName={APP_ID}\nBranch=stable\nUrl={config['url']}repo/\nGPGKey={encoded}\n"
            repository = f"[Flatpak Repo]\nUrl={config['url']}repo/\nGPGKey={encoded}\n"
            (root / "naviamp.flatpakrepo").write_text(repository)
            path = root / "Naviamp.flatpakref"
            path.write_text(reference)
            verify_descriptors(root, config, key)
            for replacement in (
                reference.replace("example.org", "attacker.example"),
                reference.replace(encoded, base64.b64encode(b"other key").decode()),
                reference.replace(APP_ID, "org.example.Other"),
                reference.replace("Branch=stable", "Branch=testing"),
            ):
                with self.subTest(replacement=replacement):
                    path.write_text(replacement)
                    with self.assertRaises(ValueError):
                        verify_descriptors(root, config, key)


if __name__ == "__main__":
    unittest.main()
